# FoxMusic iOS 功能补完 - 实施计划

> 每个任务为可独立验证的纵向切片；任务编号即执行顺序参考，依赖显式标注。
> TR 类型仅 `rule`（二元可验）或 `rubric`（评分）。证据含模拟器截图、构建输出或代码位置。

---

## Task 1: 修复主题切换后背景不跟随
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 排查并修复 SwiftUI 外壳与 CMP 页面背景：消除遗漏的硬编码背景；修正 FoxIosTheme 在跟随系统（override=-1）时恒按浅色计算的问题；确认切换主题后窗口补色、Tab 渐变、各导航栈即时重组。
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `rule` TR-1.1: 浅色/深色/跟随系统三种外观下，五个 Tab 根页与 ≥3 个二级页背景全部正确，无旧色残留；证据：模拟器三外观对照截图
  - `rule` TR-1.2: `./gradlew :iosShared:linkDebugFrameworkIosSimulatorArm64` 通过且 Xcode 构建运行不崩；证据：构建输出

## Task 2: 全部歌单页验证与补缺
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 验证 FoxPlaylistListScreen：分类 Tab 加载、推荐/分类分页（pageSize=20）、点击跳详情；补齐发现的缺陷（如分类加载失败态、刷新）。
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `rule` TR-2.1: 分类接口成功时 Tab 数=后端分类数+1；网格可滚动加载 ≥2 页；点击歌单进入正确详情；证据：模拟器操作截图

## Task 3: 推荐/新歌落地页验证与播放队列扩充
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 验证为你精选（recommend）/新歌速递（latest）落地页（虚拟歌单详情、pageSize=30、上限100、两页数据不重复）；首页与发现页点歌的入队池在现有 30 条基础上对照安卓口径核定（必要时扩至 40–50 条），屏幕仍只展示原少量条目。
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `rule` TR-3.1: 两落地页 sort 正确、数据不重复、可分页；证据：两页截图与接口参数
  - `rule` TR-3.2: 首页/发现页点歌后连续「下一首」≥10 次仍有歌；首页列表 UI 未展示全部队列；证据：操作验证与队列构建代码

## Task 4: 全部专辑页验证与补缺
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 验证 FoxAlbumListScreen：GET api/albums 分页（pageSize=20）、4 列网格、TopAppBar 返回、点击进专辑详情；补缺。
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `rule` TR-4.1: 网格可加载 ≥2 页、点击进对应专辑详情；证据：模拟器截图

## Task 5: 搜索页帖子 tab 验证与补缺
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: None
- **Description**:
  - 验证 FoxSearchScreen 帖子 tab：GET api/posts keyword 检索、瀑布流、加载/错误/空态、点击进帖子详情；补缺。
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `rule` TR-5.1: 提交关键词后展示真实帖子分页卡片，可加载更多，点击进详情；证据：截图与请求

## Task 6: 发布队列持久化与崩溃恢复
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 将 FoxPostPublishQueue 由纯内存升级为持久化（JSON 存 Application Support）：启动加载；UPLOADING/PUBLISHING 重启后重置为 QUEUED 重跑；状态/错误实时写盘；保留重试/取消语义。
- **Acceptance Criteria Addressed**: AC-7
- **Test Requirements**:
  - `rule` TR-6.1: 有 queued 任务时杀进程重启，任务仍在并最终成功；证据：持久化文件与重启前后任务页截图
  - `rule` TR-6.2: 失败任务重试可成功、取消后从持久化与列表同时移除；证据：操作截图

## Task 7: 发布动态页 Swift 接线（媒体/歌曲/话题选择器）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - 社区发帖入口以 sheet/fullScreenCover 呈现 FoxCreatePostScreen；Swift 实现媒体选择（PHPicker 图片/视频，写入临时文件并回写 FoxCreatePostStore）、歌曲选择（复用歌曲列表/搜索能力）、话题选择（话题列表页）；发布后关闭并刷新。
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `rule` TR-7.1: 纯文本与图文（含视频）两种发帖均入队并发布成功，社区可见新帖；证据：队列状态与社区列表截图
  - `rule` TR-7.2: 媒体上限 9、可删除；歌曲/话题选择结果正确回显；证据：发帖页截图

## Task 8: 发布任务状态页接线与社区入口
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 6, Task 7
- **Description**:
  - 社区页提供「发布任务/任务状态」入口并 push FoxPostPublishQueueScreen；状态页与持久化队列实时联动。
- **Acceptance Criteria Addressed**: AC-7, AC-6
- **Test Requirements**:
  - `rule` TR-8.1: 从社区可进入任务状态页，任务进度/重试/取消可用并与实际发布一致；证据：导航与操作截图

## Task 9: 举报 API 与 iOS 举报弹窗组件
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - FoxMusicApi 增加 POST api/reports（target_type/target_id/reason/description）与 GET api/reports（page/limit/status）；复用/包装 commonMain ReportBottomSheet 或提供 iOS 风格举报页（理由六选+说明）。
- **Acceptance Criteria Addressed**: AC-8, AC-9
- **Test Requirements**:
  - `rule` TR-9.1: 可从 Swift 侧调起举报页并成功提交一条举报，请求体字段正确；证据：请求日志

## Task 10: 举报各入口接线（歌曲/帖子/评论/用户）
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 9
- **Description**:
  - 帖子详情、评论长按、用户主页更多、歌曲菜单四个入口接入举报页并传入对应 target_type/target_id。
- **Acceptance Criteria Addressed**: AC-8
- **Test Requirements**:
  - `rule` TR-10.1: 四类入口各成功提交一次，target_type 分别为 post/comment/user/music；证据：四条请求体

## Task 11: 举报历史页与设置入口
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 9
- **Description**:
  - 新增举报历史页（可参考 ReportHistoryScreen 结构，iOS List 风格）：分页、状态/类型/理由中文映射、空/错态；设置页「举报历史」改为真实入口。
- **Acceptance Criteria Addressed**: AC-9
- **Test Requirements**:
  - `rule` TR-11.1: Task 10 提交的举报出现在列表，字段与状态中文正确，可加载更多；证据：截图

## Task 12: 本地聊天存储（消息/会话仓库）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 新建 iOS 本地聊天仓库：按会话存消息 JSON（Application Support/chat），内存缓存 + 版本化写入；支持按会话观察消息流、upsert（server id 去重）、会话 upsert/预览/未读/排序/置顶/删除；接口不暴露 suspend 给 Swift（仓库内协程 + StateFlow）。
- **Acceptance Criteria Addressed**: AC-11
- **Test Requirements**:
  - `rule` TR-12.1: 写入消息→杀进程重启后，会话列表与聊天页消息完整存在；证据：存储文件与重启前后截图
  - `rule` TR-12.2: 同 server id 重复写入仅一条；会话按更新时间排序；证据：代码与操作验证

## Task 13: 持久化发送队列（类 WorkManager）与消息状态机
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 12
- **Description**:
  - 实现 outbox：消息先入库 sending → 单例协程串行发送（NWPathMonitor 网络约束、指数退避重试）→ sent/failed；重启扫描未确认消息重发；失败可手动重试。供文本与各类媒体消息共用。
- **Acceptance Criteria Addressed**: AC-12
- **Test Requirements**:
  - `rule` TR-13.1: 断网发送消息留存且标 failed，恢复网络后自动/手动重试成功变 sent；证据：状态变化截图
  - `rule` TR-13.2: 发送中杀进程，重启后消息被重新发送且不产生重复；证据：重启验证

## Task 14: 会话列表与聊天页接入本地源
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 12, Task 13
- **Description**:
  - FoxMessagesScreen 会话列表改由本地仓库驱动（并保留与服务端列表合并）；FoxChatDetailScreen 消息流改读本地仓库，发送走 outbox，移除纯内存与轮询依赖（轮询仅作兜底）。
- **Acceptance Criteria Addressed**: AC-11, AC-12
- **Test Requirements**:
  - `rule` TR-14.1: 发送后会话列表立即出现该会话与最后预览；退出再进、杀进程再进消息均在；证据：全流程截图
  - `rule` TR-14.2: 气泡展示发送中/已发送/失败状态；证据：截图

## Task 15: 实时消息入库对接
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 12
- **Description**:
  - IosRealtimeMessageGateway 收到 ChatMessage 后调用本地仓库 upsert（去重）并更新会话/未读；前台聊天页即时刷新；进入会话清零未读。
- **Acceptance Criteria Addressed**: AC-16
- **Test Requirements**:
  - `rule` TR-15.1: 对端实时消息即时出现在聊天页/会话列表，同一消息多次投递仅一条，未读计数正确；证据：实时日志与截图

## Task 16: 分享选择好友页与用户名片发送
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 13, Task 14
- **Description**:
  - 实现选择好友页（好友列表 + 搜索，单选/多选确认）；用户资料页「分享」改为打开该页并经 POST api/messages/share 发送 share_type=user 名片；去掉系统 share sheet 作为默认名片行为（可保留二级入口）。
- **Acceptance Criteria Addressed**: AC-13
- **Test Requirements**:
  - `rule` TR-16.1: 资料页分享→选好友→发送成功，接收方会话出现名片消息；证据：请求与聊天截图

## Task 17: 名片消息气泡展示与跳转
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 16
- **Description**:
  - ChatThreadContent 名片气泡（头像/昵称/ID/「个人名片」标识）点击跳转对应用户资料页；接收历史名片同样可渲染。
- **Acceptance Criteria Addressed**: AC-13
- **Test Requirements**:
  - `rule` TR-17.1: 名片气泡信息正确、点击打开该用户资料；证据：截图

## Task 18: 图片与视频消息（选择/拍摄/上传/预览+权限）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 13
- **Description**:
  - Swift 侧实现 PHPicker 选图/视频、UIImagePicker（或相机封装）拍照/录像，导出临时文件；Kotlin 侧经 api/upload/image|video 上传并经 outbox 发消息；气泡展示缩略/视频时长，点击全屏看图/播放视频；Info.plist 添加相册/相机描述并运行时申请。
- **Acceptance Criteria Addressed**: AC-14, AC-U1
- **Test Requirements**:
  - `rule` TR-18.1: 相册图片、相册视频、拍照、录像四条路径各发送成功并正确展示/预览；证据：聊天页截图
  - `rule` TR-18.2: 首次使用触发权限弹窗；拒绝后入口有引导且不崩；证据：操作验证

## Task 19: 文件消息（选择/上传/展示/打开）
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 13
- **Description**:
  - UIDocumentPickerViewController 选择文件 → 通用文件上传端点（先确认路径）→ outbox 发送 file 消息；气泡显示文件名/大小/类型，点击下载后用系统预览/分享打开。
- **Acceptance Criteria Addressed**: AC-14
- **Test Requirements**:
  - `rule` TR-19.1: 发送一个文件，气泡信息正确且可打开预览；证据：截图；若上传端点不存在则标注 blocked 与依据

## Task 20: 语音消息（录音/播放/取消 + 麦克风权限）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 13
- **Description**:
  - Swift 实现按住录音（AVAudioRecorder，m4a/合理采样率，时长上限对齐安卓）、上滑取消、松开发送；经上传后 outbox 发送 voice（voice_url/voice_duration）；气泡展示时长与波形/播放状态，AVAudioPlayer 播放，处理扬声器/听筒；Info.plist 麦克风描述 + 运行时申请。
- **Acceptance Criteria Addressed**: AC-15, AC-U1
- **Test Requirements**:
  - `rule` TR-20.1: 录音→发送→播放闭环成功，播放状态可见；证据：操作截图
  - `rule` TR-20.2: 上滑取消不产生消息；首次使用申请麦克风，拒绝有引导；证据：操作验证

## Task 21: 下载引擎（断点续传/持久化/Wi-Fi 策略/本地播放联动）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 参考 MusicDownloadManager：URLSession download（按 musicId 命名存 Documents/Downloads），Range 断点续传与 200 重置策略；下载任务 JSON 持久化（状态/进度/字节/路径），按 ID 去重；暂停/继续/重试；「仅 Wi-Fi 下载」生效（NWPathMonitor）；PlayerModel 播放时优先命中本地文件（file://），离线可播。
- **Acceptance Criteria Addressed**: AC-17
- **Test Requirements**:
  - `rule` TR-21.1: 下载至约 50% 暂停→继续，文件完整可播放；下载中杀进程重启可恢复；证据：任务数据与播放验证
  - `rule` TR-21.2: 已下载歌曲走本地文件且飞行模式可播；重复下载同 ID 不重复任务；蜂窝+仅Wi‑Fi时被拦截并提示；证据：操作与代码

## Task 22: 下载管理页
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 21
- **Description**:
  - iOS 原生下载管理页（可独立设计）：存储占用、下载中/已完成分组、全部暂停/继续/播放全部、单项暂停/继续/重试/删除/播放，进度实时；设置与我的页提供入口。
- **Acceptance Criteria Addressed**: AC-18, AC-U2
- **Test Requirements**:
  - `rubric` TR-22.1: 功能完整性与设计质量；scale 1-5；anchors 1/3/5 同 AC-18；threshold >=4；证据：各状态浅色/深色截图

## Task 23: 歌曲更多菜单（五项功能全打通）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 9, Task 21
- **Description**:
  - 在所有歌曲列表场景接入更多菜单（iOS 可为底部卡片，样式与 commonMain MusicActionBottomSheet 对齐）：下一首播放（PlayerModel 队列插入）、添加到歌单（我的歌单选择页 + 加歌 API，FoxMusicApi 补该接口）、分享给好友（系统分享 + 可选名片）、下载歌曲（调下载引擎）、举报（接 Task 9）。
- **Acceptance Criteria Addressed**: AC-10, AC-U2
- **Test Requirements**:
  - `rule` TR-23.1: 五项各成功一次：下一首插入可立即听到、加歌后歌单内可见、分享面板出现、下载任务出现、举报页打开；证据：逐项结果
  - `rule` TR-23.2: 菜单在首页/发现/歌单/专辑/搜索/我的 ≥6 个场景可打开；证据：操作截图

## Task 24: 编辑资料页（头像裁剪上传流程）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 参考 EditProfileViewModel/EditProfileScreen 与安卓 API：头像区（PHPicker 选图 → 裁剪页 TOCropViewController 或 SwiftUI 裁剪 → 上传头像端点）、昵称、个性签名、邮箱只读；保存按安卓顺序（上传头像→更新资料接口）；FoxMusicApi 补齐对应端点。
- **Acceptance Criteria Addressed**: AC-19
- **Test Requirements**:
  - `rule` TR-24.1: 更换头像+改昵称/签名保存成功，我的页与重启后均显示新资料；证据：接口顺序日志与页面截图
  - `rule` TR-24.2: 未选新头像时不重复上传；邮箱不可编辑；证据：操作验证

## Task 25: 隐私设置页与黑名单
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 参考 PrivacySettingsScreen/VM：私密账号、无法被搜索找到两个开关（对应 API），黑名单分页列表 + 解除黑名单（关系更新）；加载/错误态。
- **Acceptance Criteria Addressed**: AC-20
- **Test Requirements**:
  - `rule` TR-25.1: 开关切换经接口成功，重进页面状态保持；解除黑名单后列表与关系更新；证据：请求与重进截图

## Task 26: 设置页条目接线与移除检查更新
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 11, Task 22, Task 24, Task 25, Task 30
- **Description**:
  - 设置页：编辑资料、隐私设置、下载管理、举报历史、锁屏灵动岛歌词均接真实页面；移除「检查更新」条目；保留版本展示。
- **Acceptance Criteria Addressed**: AC-21
- **Test Requirements**:
  - `rule` TR-26.1: 设置页无「检查更新」；上述条目点击均进对应页面；证据：设置页截图

## Task 27: 新增 Widget Extension target 与 App Group 工程配置
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 修改 project.pbxproj：新增 FoxMusicWidgets Widget Extension target（WidgetKit + ActivityKit）、Info.plist（widgetkit-extension）、嵌入 App Extensions；配置 App Group（主 App 与扩展共享）；主 App 开启 ActivityKit 能力（Info.plist NSSupportsLiveActivities 等）。
- **Acceptance Criteria Addressed**: AC-22, AC-23
- **Test Requirements**:
  - `rule` TR-27.1: Xcode 构建主 App 成功且扩展被嵌入（.app/PlugIns 下存在），可在模拟器安装运行；证据：构建产物

## Task 28: Live Activity 锁屏歌词（含 iOS17 交互按钮）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 27
- **Description**:
  - 扩展内 ActivityConfiguration：锁屏展示歌名/歌手、当前行+下一行、进度；iOS17 提供播放/暂停/上一首/下一首 AppIntent 按钮，经 App Group 命令通道触达 PlayerModel（实测可行方案，兜底打开 App）；处理 16.2/17 版本差异。
- **Acceptance Criteria Addressed**: AC-22
- **Test Requirements**:
  - `rule` TR-28.1: 锁屏观察 1 分钟歌词随播放逐行推进、进度正确；切歌/暂停内容刷新；证据：锁屏录屏
  - `rule` TR-28.2: iOS17 上四个控制按钮各生效一次且不强制开 App（或记录实测兜底行为）；证据：操作录屏；低于17设备记 N/A

## Task 29: 灵动岛紧凑与展开形态
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 27, Task 28
- **Description**:
  - DynamicIsland 各闭包：compact（图标+歌词/歌名）、minimal、expanded（两行歌词+控制+进度）；深色/浅色与多尺寸适配。
- **Acceptance Criteria Addressed**: AC-23
- **Test Requirements**:
  - `rubric` TR-29.1: 灵动岛展示质量；scale 1-5；anchors 1/3/5 同 AC-23；threshold >=4；证据：紧凑/展开截图（无设备可 N/A）

## Task 30: 歌词 Live Activity 生命周期管理与设置入口
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 28, Task 29
- **Description**:
  - 主 App 侧 LiveLyricsManager：授权检查、播放开始/开关开启时 start，逐行/状态变化 update（节流容错），停止播放/关闭时 end，切歌更新；设置页「锁屏与灵动岛歌词」进入功能页（总开关、说明）；与播放器歌词设置共存。
- **Acceptance Criteria Addressed**: AC-22, AC-23
- **Test Requirements**:
  - `rule` TR-30.1: 开→播放→锁屏可见；暂停/停止后活动按预期结束或更新；连续高频更新不崩溃；证据：全流程录屏
  - `rule` TR-30.2: 设置开关重进后状态保持；证据：截图

## Task 31: 全量回归与安卓侧兼容验证
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: 全部前序任务
- **Description**:
  - 五个 Tab 全流程回归；验证 commonMain 改动未破坏安卓（`./gradlew :app:assembleDebug` 或至少编译相关模块）；清理临时代码与无用占位文案。
- **Acceptance Criteria Addressed**: AC-U2, NFR-5
- **Test Requirements**:
  - `rule` TR-31.1: 安卓 App 相关模块编译通过；证据：gradle 输出
  - `rubric` TR-31.2: iOS 全模块视觉一致性；scale 1-5；anchors 同 AC-U2；threshold >=4；证据：全页面浅深截图
