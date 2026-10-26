package com.fox.music.core.common.cache

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 主 Tab 页的「接口快照」缓存（cache-first，内存 + 磁盘双层）。
 *
 * 背景：底部五个 Tab 互相切换时，目的地会被出栈、ViewModel 随之重建 → init 重新打接口 →
 * 页面先空一下再填内容（音流更糟：会换一批从头起播）；应用进程重启（崩溃恢复、用户手动杀）
 * 后内存缓存也会全部丢失。这里在内存之外再落一份盘，页面重建 / 冷启动时**先把旧数据铺上去
 * 并带 loading**，等新数据回来再整体替换：
 *
 * - 命中快照：进入即有内容，不闪空屏；无网络时也能看到上一次的数据；
 * - 未命中：走原来的首次加载路径（loading / 空态 / 错误态）。
 *
 * 落盘是纯文本信封：首行过期时间戳（epoch ms），之后是 payload JSON（紧凑格式无换行，
 * 即使含换行也用首个换行分隔，不影响解析）。core:common 不依赖 kotlinx.serialization，
 * 各 feature 用 [register] 注入自己快照的编解码 codec。
 *
 * 安全边界：个人数据快照自带 ownerUserId，由调用方校验后再铺 UI；磁盘文件在应用私有
 * cacheDir 下，随系统缓存清理一并消失，不承担长期持久化职责。
 */
object TabPageCache {

    /** 默认有效期：超过后视为过期直接丢弃（正常每次进页面都会重写，TTL 只是兜底） */
    const val DEFAULT_TTL_MS = 30 * 60 * 1000L

    private const val DISK_DIR = "tab_snapshots"

    private class Entry(val value: Any, val expiresAtMs: Long)

    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    /** 每个 key 的编解码器：由各 feature 在 ViewModel init 时注册（见 [register]）。 */
    private class DiskCodec(val encode: (Any) -> String, val decode: (String) -> Any?)
    private val codecs = ConcurrentHashMap<String, DiskCodec>()

    @Volatile
    private var appContext: Context? = null

    private val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 绑定 Application Context 以启用磁盘层；未初始化时退化为纯内存缓存（不崩、不写盘）。
     * 由 Application.onCreate 调用。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 注册某个 key 的磁盘编解码器（幂等，重复注册以最后一次为准）。
     * 必须在读/写该 key 之前注册——冷启动时没有 codec 就无法解析磁盘上的旧快照。
     */
    fun register(key: String, encode: (Any) -> String, decode: (String) -> Any?) {
        codecs[key] = DiskCodec(encode, decode)
    }

    fun put(key: String, value: Any, ttlMs: Long = DEFAULT_TTL_MS, persistToDisk: Boolean = true) {
        val expiresAtMs = System.currentTimeMillis() + ttlMs
        synchronized(lock) {
            entries[key] = Entry(value, expiresAtMs)
        }
        if (persistToDisk) {
            writeDisk(key, value, expiresAtMs)
        }
    }

    /**
     * 读取快照：先内存，未命中再落盘（解析成功会回填内存）。类型不匹配或已过期返回 null。
     * 调用方自行校验快照归属（[ownerUserId] 等）后再铺到 UI 上。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> read(key: String): T? {
        synchronized(lock) {
            val entry = entries[key]
            if (entry != null) {
                if (entry.expiresAtMs < System.currentTimeMillis()) {
                    entries.remove(key)
                } else {
                    return entry.value as? T
                }
            }
        }
        return readDisk(key) as? T
    }

    /**
     * [read] 的挂起版本：磁盘解析放到 IO 线程（音流快照整批卡片带歌词，JSON 可能上 MB，
     * 主线程同步解析会掉帧）。协程内优先用这个。
     */
    suspend fun <T : Any> readAsync(key: String): T? =
        kotlinx.coroutines.withContext(Dispatchers.IO) { read(key) }

    /** 读取后按 [transform] 就地更新（如只回填分页首批），不存在则写入 [create] 的结果。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> update(
        key: String,
        create: () -> T,
        transform: (T) -> T,
    ) {
        synchronized(lock) {
            val current = entries[key]
                ?.takeIf { it.expiresAtMs >= System.currentTimeMillis() }
                ?.value as? T
            val next = if (current == null) create() else transform(current)
            val expiresAtMs = System.currentTimeMillis() + DEFAULT_TTL_MS
            entries[key] = Entry(next, expiresAtMs)
            writeDisk(key, next, expiresAtMs)
        }
    }

    fun invalidate(key: String) {
        synchronized(lock) { entries.remove(key) }
        diskScope.launch { runCatching { diskFile(key)?.delete() } }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        diskScope.launch {
            runCatching { appContext?.let { File(it.cacheDir, DISK_DIR).deleteRecursively() } }
        }
    }

    // ---- 磁盘层 ----

    private fun diskFile(key: String): File? {
        val ctx = appContext ?: return null
        return File(File(ctx.cacheDir, DISK_DIR).apply { mkdirs() }, "$key.json")
    }

    private fun writeDisk(key: String, value: Any, expiresAtMs: Long) {
        val codec = codecs[key] ?: return
        val file = diskFile(key) ?: return
        val payload = runCatching { codec.encode(value) }.getOrNull() ?: return
        diskScope.launch {
            runCatching {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText("$expiresAtMs\n$payload")
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun readDisk(key: String): Any? {
        val codec = codecs[key] ?: return null
        val file = diskFile(key) ?: return null
        return runCatching {
            if (!file.exists()) return null
            val raw = file.readText()
            val nl = raw.indexOf('\n')
            if (nl <= 0) return null
            val expiresAtMs = raw.substring(0, nl).trim().toLongOrNull() ?: return null
            if (expiresAtMs < System.currentTimeMillis()) {
                file.delete()
                return null
            }
            val payload = raw.substring(nl + 1)
            val value = codec.decode(payload) ?: return null
            // 回填内存，后续同进程内读取不再碰盘
            synchronized(lock) { entries[key] = Entry(value, expiresAtMs) }
            value
        }.getOrNull()
    }
}

/** 各 Tab 页快照的 key：集中定义，避免各处硬编码字符串撞车。 */
object TabPageKeys {
    const val HOME = "tab_home"
    const val DISCOVER = "tab_discover"
    const val COMMUNITY = "tab_community"
    const val PROFILE = "tab_profile"
    const val STREAM = "tab_stream"
}
