# FoxMusic

FoxMusic 是一款基于 Jetpack Compose 的 Android 在线音乐流媒体应用，支持音乐播放、歌单管理、社交互动，并提供独特的桌面歌词悬浮窗功能。

## 功能特性

- **在线播放** — 基于 Media3 / ExoPlayer，支持后台播放、系统媒体控制、2GB LRU 音频缓存
- **桌面歌词** — 应用退至后台时，通过悬浮窗显示同步歌词，支持拖拽与多种样式预设
- **发现与搜索** — 热门推荐、艺人详情、专辑浏览、关键词搜索
- **歌单管理** — 创建/编辑歌单、收藏、本地与在线歌单
- **社交与聊天** — 用户互动、即时消息
- **个人中心** — 账号设置、播放偏好、应用更新检测

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.3 |
| UI | Jetpack Compose (Material 3) |
| 架构 | Clean Architecture + MVI |
| 依赖注入 | Hilt |
| 网络 | Retrofit 3 + OkHttp 5 + Kotlinx Serialization |
| 本地存储 | Room、DataStore |
| 播放器 | Media3 / ExoPlayer |
| 分页 | Paging 3 |
| 图片加载 | Coil、Glide |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 36 |

## 环境要求

- Android Studio Ladybug 或更高版本
- JDK 17
- Android SDK 36

## 快速开始

### 克隆项目

```bash
git clone <repository-url>
cd FoxMusic
```

### 构建 Debug 包

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

### 安装到设备

```bash
gradlew.bat installDebug
```

### 构建 Release 包

Release 构建需要签名配置。将 `keystore.properties.example` 复制为 `keystore.properties` 并填入签名信息：

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=foxmusic
storeFile=keystore/foxmusic-release.jks
```

然后执行：

```bash
gradlew.bat assembleRelease
```

## 常用命令

```bash
# 清理构建
gradlew.bat clean

# 运行单元测试
gradlew.bat test

# 运行指定模块测试
gradlew.bat :core:player:test

# 运行仪器化测试（需连接设备或模拟器）
gradlew.bat connectedAndroidTest

# Lint 检查
gradlew.bat lint
```

## 项目结构

```
FoxMusic/
├── app/                    # 应用入口、导航、主 Activity
├── core/                   # 核心基础设施模块
│   ├── common/             # 工具类、MVI 基类、扩展
│   ├── model/              # 领域数据模型
│   ├── domain/             # Repository 接口、Use Case
│   ├── data/               # Repository 实现、数据映射
│   ├── network/            # Retrofit 服务、拦截器
│   ├── database/           # Room 数据库
│   ├── datastore/          # 偏好设置存储
│   ├── ui/                 # 共享 Compose 组件
│   └── player/             # 播放器服务与控制器
└── feature/                # 功能模块（仅依赖 core）
    ├── home/               # 首页
    ├── auth/               # 登录注册
    ├── player/             # 播放器 UI、桌面歌词
    ├── playlist/           # 歌单
    ├── search/             # 搜索
    ├── discover/           # 发现
    ├── social/             # 社交
    ├── chat/               # 聊天
    └── profile/            # 个人中心
```

**模块依赖规则**：`feature` 模块之间不可互相依赖，共享代码放在 `core` 模块中；`core` 模块之间除 `common` 外尽量避免交叉依赖。

## 架构概览

### MVI 模式

所有 ViewModel 继承 `MviViewModel<UiState, UiIntent, UiEffect>`：

- **UiState** — 不可变 UI 状态
- **UiIntent** — 用户/系统操作
- **UiEffect** — 一次性副作用（Toast、导航等）

### 播放器

```
MusicPlaybackService (MediaSessionService)
        ↓
MusicController (单例，暴露 playerState: StateFlow)
        ↓
UI / 桌面歌词 (LyricSyncManager 监听播放状态)
```

- `updatePlaylist()` — 无缝切换播放列表，保持当前歌曲
- `setPlaylist()` — 完整替换列表，从头开始播放

### 桌面歌词

悬浮窗通过 `WindowManager` + `TYPE_APPLICATION_OVERLAY` 实现，需要 `SYSTEM_ALERT_WINDOW` 权限。样式由 `LyricStyleManager` 管理，支持 CLASSIC、NEON、WARM、COOL、DARK 等预设。

## 配置说明

### API 地址

后端 Base URL 定义在 `core/network/build.gradle.kts`：

```kotlin
buildConfigField("String", "BASE_URL", "\"http://your-api-host:port/\"")
```

媒体资源地址在 `core/common/.../AppConstants.kt` 的 `MEDIA_BASE_URL` 中配置。

### Debug 与 Release

- Debug 包名后缀：`.debug`（`com.fox.music.debug`）
- Release 启用代码混淆与资源压缩

## 开发说明

更详细的架构约定、文件路径索引和近期功能记录见 [CLAUDE.md](.claude/CLAUDE.md)。

## 许可证

本项目暂未声明开源许可证。如需二次分发或商用，请先与项目维护者确认。
