package com.fox.music.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.delay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequestReturnCacheDataElseLoad
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.stat as posixStat

/**
 * iOS 侧图片加载：Ktor(Darwin) 下载 → Skia 解码 → Compose ImageBitmap。
 *
 * 没有用第三方 KMP 图片库，是为了避免再引入一条依赖链；
 * 当前只需要「按 URL 拉网络图」这一条路径。
 *
 * 三级缓存（对齐 Android 端 Coil 语义）：
 *   1. 内存 Map（进程内，48 条有界 FIFO）—— 最快；
 *   2. 确定性磁盘缓存（Caches/fox_image_cache，按 URL 哈希存原始字节）—— 跨重启持久，
 *      这是冷启动不重下的真正保障；
 *   3. 网络下载，成功后回填磁盘 + 内存。
 *
 * 为什么不用 NSURLCache 当磁盘缓存：它在 Ktor(Darwin) 流式响应 + 服务端旧响应头下
 * 基本不落盘，实测就是「加载过一次的图冷启动仍重下」。故改为自己按文件管理，绕过
 * 整套 HTTP 缓存语义。NSURLCache 仍保留为会话级兜底，不影响主路径。
 */
private val imageHttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            // 共享 URL 缓存：64MB 内存 + 256MB 磁盘（覆盖 iOS 默认 4MB 磁盘缓存
            // 远不够装封面图的短板），弱网下二次启动的封面直接走本地。
            NSURLCache.setSharedURLCache(
                NSURLCache(
                    memoryCapacity = (64L * 1024 * 1024).toULong(),
                    diskCapacity = (256L * 1024 * 1024).toULong(),
                    diskPath = null,
                )
            )
            URLCache = NSURLCache.sharedURLCache
            requestCachePolicy = NSURLRequestReturnCacheDataElseLoad
            timeoutIntervalForRequest = 30.0
        }
    }
}

/**
 * 解码后**最长边**上限（px）。
 *
 * 为什么必须有：Android 端 Coil 会按目标 View 尺寸降采样（`size(Size.ORIGINAL)` 之外的
 * 默认行为），iOS 侧原来是 `makeFromEncoded` 整图解码。实测聊天里一张后端原图
 * 3072×4096，ARGB_8888 下就是 **~50MB** 位图；而它在气泡/列表里只显示一两百 dp。
 * 同一屏出现几张就足以触发内存告警甚至被 jetsam 杀掉。
 *
 * 缩到 720 长边后约 1.2MB（≈1/40），视觉上与缩略图展示无差别。
 * 720 是「三倍屏下 240dp 展示尺寸」的上界，覆盖气泡图、帖子图、歌单封面三类场景。
 */
private const val MAX_DECODE_EDGE = 720

/** 内存缓存条目上限。原来是无上限 `HashMap` —— 长列表滑到底会让几十张整图常驻。 */
private const val MAX_CACHE_ENTRIES = 48

/**
 * 有界 FIFO 内存缓存（进程内有效，与 Android 端 Coil 的内存缓存语义相当）。
 *
 * 不能用 `java.util.LinkedHashMap(accessOrder = true)` 那套写法：那个构造器与
 * `removeEldestEntry` 是 JVM 专有 API，Kotlin/Native 上没有。
 */
private val imageCache = HashMap<String, ImageBitmap>()
private val imageCacheOrder = ArrayDeque<String>()

private fun cacheGet(url: String): ImageBitmap? = imageCache[url]

private fun cachePut(url: String, bitmap: ImageBitmap) {
    if (imageCache.put(url, bitmap) == null) imageCacheOrder.addLast(url)
    while (imageCacheOrder.size > MAX_CACHE_ENTRIES) {
        imageCache.remove(imageCacheOrder.removeFirst())
    }
}

/**
 * 确定性磁盘缓存：图片**原始字节**按 URL 哈希落 Caches 目录。
 *
 * 为什么自己管、不用 NSURLCache：
 * NSURLCache 的落盘依赖服务端响应头 + 它对 Ktor(Darwin) 流式响应的缓存判定，
 * 二者任一不满足就根本不写盘 —— 实测就是这个组合导致「加载过一次的图冷启动仍重下」。
 * 这里绕开整套 HTTP 缓存语义，自己按 URL 存文件，**跨进程重启必命中**，与 Android 端
 * Coil 的 diskCache 语义对齐。
 *
 * 存原始字节而非解码后的位图：读回时再按 [MAX_DECODE_EDGE] 降采样，既省磁盘又保证清晰。
 */
private const val MAX_DISK_FILES = 1000

@OptIn(ExperimentalForeignApi::class)
private fun imageCacheDir(): String? {
    val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val base = (dirs.firstOrNull() as? String) ?: return null
    val dir = "$base/fox_image_cache"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

private fun diskPathFor(url: String): String? {
    val dir = imageCacheDir() ?: return null
    return "$dir/${urlHash(url)}.img"
}

@OptIn(ExperimentalForeignApi::class)
private fun diskCacheGet(url: String): ByteArray? {
    val path = diskPathFor(url) ?: return null
    val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
    val ptr = data.bytes ?: return null
    return ptr.reinterpret<ByteVar>().readBytes(data.length.toInt())
}

@OptIn(ExperimentalForeignApi::class)
private fun diskCachePut(url: String, bytes: ByteArray) {
    val path = diskPathFor(url) ?: return
    runCatching {
        // 沿用项目约定（FoxLocalChatStore 等）的 POSIX 写文件方式，避免 NSData 构造器
        // 在本 Kotlin/Native 版本下的 cinterop 差异。pin 仅需撑过 fwrite 调用。
        val f = fopen(path, "wb") ?: return@runCatching
        try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
            }
        } finally {
            fclose(f)
        }
    }
    pruneDiskCacheIfNeeded()
}

/** 超过上限时删最旧文件，避免无限膨胀（任何失败都静默跳过，绝不影响图片加载）。 */
@OptIn(ExperimentalForeignApi::class)
private fun pruneDiskCacheIfNeeded() {
    val dir = imageCacheDir() ?: return
    val fm = NSFileManager.defaultManager
    val files = (fm.contentsOfDirectoryAtPath(dir, null) as? List<*>) ?: return
    if (files.size <= MAX_DISK_FILES) return
    // 用 POSIX stat 取 st_mtime（纯数值，可直接比较），绕开 NSDate 在本 interop 下
    // 未暴露日期方法的限制。st_mtime 越小 = 越旧。
    val withMtime = files.mapNotNull { name ->
        val p = "$dir/${name as? String ?: return@mapNotNull null}"
        val mtime = memScoped {
            val st = alloc<platform.posix.stat>()
            // Apple 的 struct stat 用 st_mtimespec.tv_sec（st_mtime 是 C 宏，interop 不含）
            if (posixStat(p, st.ptr) == 0) st.st_mtimespec.tv_sec else null
        } ?: return@mapNotNull null
        p to mtime
    }.sortedBy { it.second }
    val removeCount = withMtime.size - MAX_DISK_FILES
    withMtime.take(removeCount).forEach { (p, _) -> fm.removeItemAtPath(p, null) }
}

/** FNV-1a 64 位哈希 → 16 位十六进制，作缓存文件名，碰撞概率可忽略。 */
private fun urlHash(url: String): String {
    var h: ULong = 1469598103934665603uL
    for (c in url) {
        h = h xor c.code.toULong()
        h = h * 1099511628211uL
    }
    return h.toString(16).padStart(16, '0')
}

@Composable
actual fun PlatformAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
    loadingContent: @Composable BoxScope.() -> Unit,
    errorContent: @Composable BoxScope.() -> Unit,
) {
    // null = 加载中；ImageBitmap = 成功；Throwable = 失败
    var state by remember(url) { mutableStateOf<Any?>(cacheGet(url)) }

    LaunchedEffect(url) {
        if (state is ImageBitmap) return@LaunchedEffect
        // 1) 内存命中（进程内，最快）
        cacheGet(url)?.let { state = it; return@LaunchedEffect }
        // 2) 磁盘命中（跨重启持久；修复冷启动满屏占位、等十几秒重下的问题）
        diskCacheGet(url)?.let { diskBytes ->
            runCatching { decodeDownsampled(diskBytes) }.getOrNull()?.let { bmp ->
                cachePut(url, bmp)
                state = bmp
                return@LaunchedEffect
            }
        }
        // 3) 网络下载：成功写磁盘 + 写内存，下次重启直接命中
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                val bytes = imageHttpClient.get(url).readBytes()
                diskCachePut(url, bytes)
                val bitmap = decodeDownsampled(bytes)
                cachePut(url, bitmap)
                state = bitmap
                return@LaunchedEffect
            } catch (e: Throwable) {
                lastError = e
                if (attempt == 0) delay(400)
            }
        }
        state = lastError
        onError()
    }

    Box(modifier = modifier) {
        when (val current = state) {
            null -> loadingContent()
            is Throwable -> errorContent()
            is ImageBitmap -> Image(
                bitmap = current,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

/**
 * 本地文件图片：NSFileManager 读字节 → 复用 [decodeDownsampled] 降采样解码。
 *
 * 聊天背景是设置页从相册复制进 Application Support 的 JPEG，没有网络请求，
 * 因此也不走 Ktor / NSURLCache。文件不存在或解码失败时静默留白（背景是纯装饰层，
 * 不该把整个聊天页打成错误态）。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformFileImage(
    filePath: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    var state by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(filePath) {
        val bytes = runCatching {
            val data = NSFileManager.defaultManager.contentsAtPath(filePath) ?: return@runCatching null
            val ptr = data.bytes ?: return@runCatching null
            ptr.reinterpret<ByteVar>().readBytes(data.length.toInt())
        }.getOrNull()
        state = bytes?.let { decodeDownsampled(it) }
    }

    Box(modifier = modifier) {
        state?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

/**
 * 按 [MAX_DECODE_EDGE] 等比降采样解码。
 *
 * 做法：先让 Skia 解出原图（`makeFromEncoded` 是惰性 SkImage），再把它画进一张
 * 目标尺寸的 raster surface，最后取 snapshot —— 于是**常驻**的位图只有缩略图那么大。
 * 任何一步失败都退回原来的整图解码，保证不因优化把图弄丢。
 */
private fun decodeDownsampled(bytes: ByteArray): ImageBitmap {
    return try {
        val src = SkiaImage.makeFromEncoded(bytes)
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src.toComposeImageBitmap()

        val scale = MAX_DECODE_EDGE.toFloat() / maxOf(w, h).toFloat()
        if (scale >= 1f) return src.toComposeImageBitmap()

        val targetW = maxOf(1, (w * scale).toInt())
        val targetH = maxOf(1, (h * scale).toInt())
        val surface = Surface.makeRasterN32Premul(targetW, targetH)
        val canvas = surface.canvas
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawImage(src, 0f, 0f)
        canvas.restore()
        // 不 close surface：snapshot 由 SkImage 自己持有像素，提前释放有 use-after-free 风险，
        // 而 Surface 本身是 Managed（有 finalizer），交给 GC 即可。
        surface.makeImageSnapshot().toComposeImageBitmap()
    } catch (e: Throwable) {
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }
}
