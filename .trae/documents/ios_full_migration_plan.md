# iOS 剩余功能全量迁移实施计划

> 范围：2026-09-28 盘点出的 7 个未迁移项 + 4 个半成品，共 11 项。
> 原则：数据/状态在 Kotlin 侧（iosShared），SwiftUI 负责导航与系统能力；commonMain 改动以「新增带默认值回调」方式保持 Android 零行为变更；每个批次可独立编译、独立验证。

## 调研结论（现状要点）

- 分享接口后端已就绪：`POST api/messages/share`，请求体 `ShareMessageRequest(receiverId, shareType, shareId, content)`（[ChatApiService.kt:11](file:///Users/luofulin/AndroidStudioProjects/FoxMusic/core/network-android/src/main/kotlin/com/fox/music/core/network/api/ChatApiService.kt#L11)）；iOS `FoxMusicApi` 尚无此方法。
- SelectFriendScreen 在 Android 是 android-only 页面（commonMain 无 Content），iOS 需 SwiftUI 原生新建；数据源可复用 `FoxMusicApi.fetchFriends()` / `searchUsers()`。
- 聊天页三个死回调在 [FoxChatDetailScreen.kt:427,455,456](file:///Users/luofulin/AndroidStudioProjects/FoxMusic/iosShared/src/iosMain/kotlin/com/fox/music/iosshared/ui/FoxChatDetailScreen.kt#L427)：onForward（转发）、onShareClick（卡片跳转）、onPlayVoice（语音播放）。语音字段 `voiceUrl/voiceDuration` DTO 已具备。
- 帖子：`PUT api/posts/{id}`（updatePost）后端就绪，iOS 缺方法；iOS 发帖 `FoxCreatePostScreen` 无 postId 编辑态；共享层 `PostDetailContent`/`CommunityPlazaContent` 已有 `onEditPost(Long)` 回调但 iOS 未接。
- 评论举报：iOS 在 presenter 内以固定 "other" 直接提交（[FoxCommunityScreen.kt:318](file:///Users/luofulin/AndroidStudioProjects/FoxMusic/iosShared/src/iosMain/kotlin/com/fox/music/iosshared/ui/FoxCommunityScreen.kt#L318)），未走统一举报弹窗。
- 歌曲评论：Android 面板支持展开回复（`GET api/music-comments/{id}/replies`）与回复（`POST` 带 parentId，iOS `postMusicComment` 已支持 parentId）；通知跳转用 `ResolveMusicCommentNavigationUseCase`（经 replies 接口拿 musicId）。iOS 面板 parentId 恒 nil。
- 发现页：共享层 `HotArtistListContent`/`ArtistListContent` 已跨端；数据 `GET api/artists?sort=hot`（iOS `fetchArtists` 需加 sort 参数）；iOS 发现页工厂未传 `onHotArtistMore`。
- 曲库管理（Android `ManageScreen` + `ManageSearchScreen`）：`GET api/admin/music`（分页）、`DELETE api/admin/music/{id}`、`POST api/admin/music`（添加单曲）、`POST /api/import/netease/charts?platform=mobile`、`POST /api/import/hot-playlists?platform=mobile`。iOS 完全缺失。
- 下载：引擎具备断点续传/持久化/`localPath(for:)`，但 `localPath(for:)` 全工程零调用；下载页无分组/存储概览/批量操作/Wi-Fi 开关 UI；`wifiOnly` 判断代码自承未做真实网络类型区分。
- 聊天记录搜索：iOS 已有全局 `ChatSearchView`+`FoxChatSearch`（本地聚合），缺「会话内搜索」维度（Android UserChatSearchScreen，ChatSettings 带入 userId）。

## 实施批次（按价值/依赖排序）

### 批次 A：聊天能力闭环（缺口 1/2/3/11）

**A1. 分享接口与选人页**
- `iosShared/.../FoxMusicApi.kt`：新增 `suspend fun sendShareMessage(receiverId, shareType, shareId, content)` → POST `api/messages/share`（请求体字段对齐 `ShareMessageRequest`）。
- 新建 `iosApp/iosApp/SelectFriendView.swift`：SwiftUI 选人页（好友列表 + 关键词搜索、单选；标题随场景变「分享给好友/转发给」），选中后回调 `(userId, nickname)`；样式沿用现有导航页与底部弹窗规范。
- 接线（保留系统分享作为二级入口）：
  - 用户资料页分享（[UserHomeView.swift](file:///Users/luofulin/AndroidStudioProjects/FoxMusic/iosApp/iosApp/UserHomeView.swift) 与 MessagesTabView 的 UserProfileView shareItem）→ 选人 → `sendShareMessage(shareType="user", shareId=userId)`，成功 Toast。
  - 歌曲菜单（[SongMoreMenu.swift:434](file:///Users/luofulin/AndroidStudioProjects/FoxMusic/iosApp/iosApp/SongMoreMenu.swift#L434)）：菜单改为「分享给好友」（FoxMusic 内 share music）+「系统分享」两项。
  - 帖子详情/歌单/专辑详情的分享入口（凡 Swift 侧已有 share 出口处）→ 选人发对应 shareType（post/playlist/album）。
  - 聊天消息长按「转发」：`makeChatDetailViewController` 增加 `onForwardMessage(localId/messageId)` 回调 → Swift 弹 SelectFriendView → 按 Android 转发语义发送（实施时对齐 Android 转发是 share 还是重发文本/媒体，优先复用 `messages/share`；不支持 share 的消息类型走原内容重发）。
- 会话内收到名片/卡片消息的气泡渲染已在 commonMain，无需新增 UI。

**A2. 卡片消息点击跳转**
- `FoxChatDetailScreen`：`onShareClick` 由无参 Toast 改为 `onOpenShare(shareType: String, shareId: Long)`（工厂方法新参数，Kotlin 侧从 Message.shareData 取 type/id，逻辑参照 Android ChatNavGraph 223-266 行）。
- Swift `ChatDetailView` 接回调，按类型导航（MessagesRoute 已覆盖 playlistDetail/albumDetail/artist→artistDetail/userHome/postDetail）；`music` 类型直接用卡片数据调 `PlayerModel.shared.play(song)` 播全曲（无歌曲详情页概念，与 Android music 分支对齐）。

**A3. 语音消息播放**
- 新建 `iosApp/iosApp/VoiceMessagePlayer.swift`：AVAudioPlayer 单例，`play(url, messageId)` / `stop()`，发布 `@Published playingMessageId`、时长/进度；处理与主播放器 AVAudioSession 的类别切换（.playback，播放完恢复）；语音文件走临时缓存。
- `ChatThreadContent` 的 onPlayVoice 回调如需携带 url/id（实施时核对 commonMain 签名，以新增默认回调/参数方式处理，Android 不受影响）；`FoxChatDetailScreen` 透传 `onPlayVoice(url, messageId)`；Swift `ChatDetailView` 接 VoiceMessagePlayer；气泡播放态由 commonMain 既有状态或回调刷新（若 commonMain 无播放态字段，播放中态先在 iOS 侧用最小手段呈现，不改共享层数据模型）。

**A4. 会话内查找聊天记录**
- `FoxChatSearch.kt` 增加可选 `peerUserId` 过滤（本地 JSON 已按会话存储，过滤成本低）。
- `ChatSearchView` 增加 `peerUserId/nickname` 参数；`ChatSettingsView`「查找聊天记录」入口按所在会话传入 userId（对齐 Android ChatSettingsScreen.onNavigateToChatSearch）；消息 Tab 根入口仍为全局搜索。

### 批次 B：社区——编辑帖子 + 评论举报（缺口 6/10）

**B1. 编辑帖子**
- `FoxMusicApi`：新增 `updatePost(postId, title, content, mediaUrls, musicId, tagIds)` → PUT `api/posts/{id}`（字段对齐 Android SocialApiService.updatePost）。
- `FoxCreatePostScreen.kt`：
  - `makeViewController(postId: Long? = -1, ...)`；postId>0 时进入编辑态：启动调 `fetchPostDetail` 回填标题/正文/关联歌曲/话题；既有媒体（图片/视频 URL）以「只读回显+可删除」形态进入选择列表，与新选媒体混排，发布时旧媒体保留 URL、新媒体先上传。
  - 发布按钮：编辑态走 `updatePost`，新建态仍走 `FoxPostPublishQueue`（编辑同步提交即可，对齐 Android 行为）。
- Swift：`CreatePostView` 透传 postId；`CommunityRoute` 增加 `editPost(id)`；广场/帖子详情 `onEditPost` → 接该路由；`PostDetailView` 编辑保存成功后刷新。
- 核对共享层 `CreatePostContent`/Store 是否支持既有媒体回显；若不支持，仅在 iosMain 侧做包装层（预览列表前置既有项），不改 androidMain。

**B2. 评论举报走统一弹窗**
- `FoxCommunityScreen` presenter：`PostDetailIntent.ReportComment` 不再直接 submit，改为新增 `onReportComment(commentId)` 出口回调（工厂方法带默认空实现，Android 侧无影响）。
- Swift PostDetailView（CommunityTabView、MessagesTabView、ContentView、ProfileTabView 四处宿主）接回调 → `ReportSheet(targetType:"comment", targetId:)`。

### 批次 C：歌曲评论楼中楼 + 通知跳转（缺口 7）

- `FoxMusicApi`：新增 `fetchMusicCommentReplies(commentId, page, limit)` → GET `api/music-comments/{id}/replies`；新增 `resolveMusicComment(commentId)`（经 replies 接口返回拿 musicId，逻辑对齐 `ResolveMusicCommentNavigationUseCase`）。
- `PlayerSheets.swift` CommentSheet 升级：
  - 评论行点击 → 就地展开回复分页（加载更多）+ 输入栏切换为「回复 @xxx」，发送带 parentId（`postMusicComment(musicId, content, parentId)` 已就绪）；可取消回复态。
  - 评论详情以 sheet 内二级页承载（不做全局路由）。
- `FoxNotificationCategoryScreen.openTarget`：音乐评论分支由 Toast 改为 `onOpenMusicComment(musicId)` 回调；Swift 侧播放该歌曲并打开 CommentSheet（经 PlayerUICoordinator 全屏播放器，落地后自动弹评论面板——用一个一次性触发标志实现）。

### 批次 D：发现页歌手列表（缺口 4）

- `FoxMusicApi.fetchArtists` 增加 `sort: String? = null` 查询参数。
- 新建 iosShared 包装（放 `FoxHomeDiscoverScreens.kt` 或新增 `FoxArtistListScreens.kt`）：`makeHotArtistListViewController`（HotArtistListContent，sort=hot，含「全部歌手」入口）、`makeArtistListViewController`（ArtistListContent 分页），回调 onArtistClick/onBack。
- `makeDiscoverViewController` 增加 `onHotArtistMore` 并在 DiscoverIntent 分发中接上。
- Swift：`DiscoverRoute` 增加 `hotArtistList`/`allArtistList`；新增两个 SwiftUI 包装 View，歌手点击复用 ArtistDetailView。

### 批次 E：曲库管理（缺口 5）

- `FoxMusicApi` 新增：`fetchAdminMusicPage(page,limit,keyword)` GET `api/admin/music`；`deleteAdminMusic(id)` DELETE；`addAdminMusic(...)` POST（字段对齐 Android `POST api/admin/music`，先核对 Android ManageViewModel「添加单曲」实际链路——若走 findOrCreateMusic 则复用既有 `findOrCreateMusic`）；`importNeteaseCharts(platform="mobile")`、`importHotPlaylists(platform="mobile")`。
- 新建 `iosApp/iosApp/ManageLibraryView.swift`（iOS 原生风格）：
  - 曲库分页列表 + 搜索；每行删除（确认弹窗）；
  - 顶部「添加单曲」→ 搜索页（复用搜索能力）→ 添加；
  - 「更新排行榜」「更新热门歌单」确认弹窗 → 调接口 → Toast/结果提示（对齐 Android 文案）；
  - 需要登录，未登录态引导登录。
- `ProfileRoute.manageLibrary` + `FoxProfileScreen` 的 onManageLibrary 回调接线（若共享层未暴露该回调，在 iosMain 包装层补出口）。

### 批次 F：下载闭环与管理页（缺口 8/9）

- F1. 本地文件联动：统一在 PlayerModel 取播放 URL 处（队列播放/切歌/insertNext）查询 `MusicDownloadManager.shared.localPath(for: String(id))`，命中且文件存在则用 `file://`；下载完成事件发出后若当前正是该歌可在下一次播放生效（不强制中断当前流）。
- F2. `MusicDownloadManager`：引入 NWPathMonitor 做真实 Wi-Fi/蜂窝判断，蜂窝 + wifiOnly 时拦截并回调错误文案；`wifiOnly` 持久化（UserDefaults）。
- F3. `DownloadManagerView` 重做为 iOS List 风格：
  - 顶部存储占用概览（已下载总大小）+「仅 Wi-Fi 下载」开关；
  - 「下载中」「已完成」分组；下载中组支持全部暂停/继续；
  - 已完成项可点击播放（直接 PlayerModel 播本地文件）、删除；失败项重试；
  - 支持空态。

### 批次 G：集成验证

1. `./gradlew :iosShared:linkDebugFrameworkIosSimulatorArm64` 通过。
2. Xcode build（FoxMusicSim）成功并安装运行。
3. Android 兼容：commonMain 回调改动后 `./gradlew :app:assembleDebug` 编译通过（NFR-5）。
4. UI 测试：在 `FoxMusicUITests.swift` 按现有模式补关键路径（选人分享发送成功、语音播放按钮存在、编辑帖子回显、评论回复发送、下载完成走本地、歌手列表可进入、曲库管理页可进入）。
5. 模拟器手测清单（浅/深两色）：
   - A：资料页分享→选人→对方会话出现名片；歌曲分享给好友；音乐/帖子/歌单卡片点击跳转；录音→发送→播放；聊天信息→查找本会话记录。
   - B：自己帖子编辑（媒体/歌曲/话题回显、保存后列表刷新）；评论长按→举报弹窗→历史可见。
   - C：歌曲评论回复展开/发送；通知点音乐评论→播放器评论面板。
   - D：发现页热门歌手更多→列表→歌手详情；全部歌手分页。
   - E：曲库管理增删、两个导入动作（后端权限不足时错误文案正常）。
   - F：下载后飞行模式可播；Wi-Fi 开关拦截；管理页分组/批量/存储概览。

## 依赖与注意事项

- 跨语言接口仍走回调风格，不向 Swift 暴露 suspend/Flow（NFR-2）。
- commonMain 的回调新增一律带默认值，Android 侧调用点与行为不变；批次 G 以安卓编译兜底。
- SelectFriendView 与 ManageLibraryView 为纯新增文件；ReportSheet/SongMoreMenu 等只做增量接线。
- 转发消息的确切后端语义、addAdminMusic 字段、resolveMusicComment 返回结构，在对应批次实施第一步先读 Android 实现核实，不臆造字段。
- 不做车载/桌面歌词/安卓 Widget/自建更新（既定 Non-Goals）。

## 风险与对策

- **编辑帖子媒体回显复杂**（URL 旧媒体 + 本地新媒体混排、视频）：先验证共享层 CreatePostContent 能力边界；不支持时在 iosMain 包装，避免动 androidMain；视频回显用首帧/URL 占位。
- **语音气泡播放态在共享层无状态位**：优先回调驱动；不为此改 Message 模型；必要时 iOS 侧按 messageId 本地标记。
- **音乐评论通知解析依赖 replies 接口返回 musicId**：若 DTO 不含 musicId，降级为仅打开对应歌曲（先核对 ResolveMusicCommentNavigationUseCase 实际取值路径）。
- **admin/import 接口可能 403（内部测试账号无权限）**：UI 与安卓保持一致，错误原文透出 Toast，不做特殊分支。
- **批次较多导致编译漂移**：每个批次结束即 linkDebugFramework + Xcode 增量编译，不积累到最后。
