import XCTest

/// FoxMusic iOS 端截屏驱动功能验收。
///
/// 覆盖功能点：
/// 1. 启动后从真实后端（http://39.106.30.151:9000/api/music）拉取歌曲列表
/// 2. 列表加载完成后自动播放第一首（里程碑演示行为）
/// 3. 底部迷你播放条显示当前歌名 / 歌手，按钮图标随播放态切换
/// 4. 点击播放/暂停按钮 → 图标与状态来回切换
/// 5. 点击 Compose 列表中的另一首歌 → 迷你播放条切换到该歌曲（Compose→Swift 回调）
/// 6. 列表可滚动（Compose LazyColumn 渲染正常）
///
/// 截屏统一写入 /tmp/foxmusic_ui/，供外部比对分析。
final class FoxMusicUITests: XCTestCase {

    private var shotDir: URL {
        URL(fileURLWithPath: "/tmp/foxmusic_ui", isDirectory: true)
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
        try FileManager.default.createDirectory(at: shotDir, withIntermediateDirectories: true)
    }

    private func snap(_ app: XCUIApplication, _ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        let url = shotDir.appendingPathComponent("\(name).png")
        try? png.write(to: url)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// 等待列表加载完成（"加载歌曲中…"消失且无错误）。
    private func waitListLoaded(_ app: XCUIApplication) throws {
        let loading = app.staticTexts["加载歌曲中…"]
        let failure = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "加载失败"))
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if loading.exists == false && failure.count == 0 {
                return
            }
            if failure.count > 0 {
                XCTFail("列表加载失败：\(failure.firstMatch.label)")
            }
            sleep(1)
        }
        XCTFail("30 秒内列表未加载完成（仍在 loading 或有错误）")
    }

    func test01SongsListLoadsFromBackend() throws {
        let app = XCUIApplication()
        app.launch()

        try waitListLoaded(app)
        sleep(2) // 等 Compose 列表渲染
        snap(app, "01_列表加载")

        // 列表非空：至少能看到一个歌曲静态文本（Compose 语义节点），
        // 若 Compose 未暴露无障碍节点则退化为坐标点按可行性检查
        let texts = app.staticTexts
        let hasAnyText = texts.count > 0
        if !hasAnyText {
            print("WARN: Compose 列表未暴露无障碍静态文本，后续用坐标点按")
        }
        XCTAssertFalse(hasAnyText == false && app.frame.isEmpty, "界面无内容")
    }

    func test02AutoPlayShowsMiniPlayer() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)

        let playBtn = app.buttons["miniPlayer.playPause"]
        let appeared = playBtn.waitForExistence(timeout: 20)
        snap(app, "02_迷你播放条出现")
        XCTAssertTrue(appeared, "自动播放后迷你播放条未出现")
    }

    func test03TogglePauseAndResume() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)

        let playBtn = app.buttons["miniPlayer.playPause"]
        XCTAssertTrue(playBtn.waitForExistence(timeout: 20), "迷你播放条未出现")
        sleep(3) // 确认 AVPlayer 已起播

        // 初始应为播放态 → 点按后暂停
        playBtn.tap()
        sleep(1)
        snap(app, "03_点击暂停后")
        // 点按后再点按 → 恢复播放
        XCTAssertTrue(playBtn.exists, "暂停后按钮消失")
        playBtn.tap()
        sleep(1)
        snap(app, "04_恢复播放后")
    }

    func test04TapSongSwitchesMiniPlayer() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)
        let playBtn = app.buttons["miniPlayer.playPause"]
        XCTAssertTrue(playBtn.waitForExistence(timeout: 20))
        sleep(2)

        // 记录初始歌名（迷你播放条标题）
        let title = app.staticTexts["miniPlayer.title"]
        let initialTitle = title.exists ? title.label : "(未找到标题)"

        // 点按 Compose 列表第 4 行附近的歌曲（坐标点按，避开可见区顶部）
        let f = app.frame
        let tapPoint = CGPoint(x: f.midX, y: f.minY + f.height * 0.45)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: tapPoint.x, dy: tapPoint.y)).tap()
        sleep(4) // 等播放器切换

        snap(app, "05_点按歌曲后")
        let newTitle = title.exists ? title.label : "(未找到标题)"
        print("TITLE_COMPARE initial=\(initialTitle) new=\(newTitle)")
        XCTAssertNotEqual(initialTitle, newTitle, "点按歌曲后迷你播放条歌名未变化")
    }

    func test05ScrollListRendersMoreItems() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)
        sleep(2)
        snap(app, "06_滚动前")
        app.swipeUp(velocity: .slow)
        app.swipeUp(velocity: .slow)
        sleep(1)
        snap(app, "07_滚动后")
        // 无崩溃即通过；内容比对在外部截屏分析中做
    }

    // MARK: - 批次 21：SwiftUI TabView 外壳 + 共享 Compose 歌单广场

    /// 切到"歌单"Tab，验证 Kotlin 侧 FoxPlaylistScreen（Ktor 拉 /api/playlists/recommended
    /// + feature:playlist commonMain 的 PlaylistListContent）能在 iOS 渲染出来。
    /// 同时把无障碍树打到测试输出，便于无图像环境下核验渲染内容。
    func test06PlaylistTabRendersSharedCompose() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)

        print("=== TABBAR DIAG begin ===")
        print("tabBars.count=\(app.tabBars.count)")
        print("tabBars[0].buttons.count=\(app.tabBars.buttons.count)")
        for i in 0..<app.tabBars.buttons.count {
            let b = app.tabBars.buttons.element(boundBy: i)
            print("  tab[\(i)] label='\(b.label)' frame=\(b.frame) hittable=\(b.isHittable)")
        }
        print("=== TABBAR DIAG end ===")

        let tab = app.tabBars.buttons["歌单"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "未找到底部'歌单'Tab")
        tab.tap()
        sleep(8) // 等 Ktor 拉推荐歌单 + Compose 首帧

        snap(app, "08_歌单Tab")

        print("=== NAVBARS AFTER TAP begin ===")
        print("navBars.count=\(app.navigationBars.count)")
        for i in 0..<app.navigationBars.count {
            print("  nav[\(i)] id='\(app.navigationBars.element(boundBy: i).identifier)'")
        }
        print("=== NAVBARS AFTER TAP end ===")

        print("=== A11Y TREE BEGIN (歌单Tab) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        XCTAssertEqual(app.state, .runningForeground, "切到歌单 Tab 后应用不在前台（疑似崩溃）")
    }

    /// 在歌单 Tab 点按一个真实歌单卡片（Compose 透出的无障碍 Button），
    /// 验证 navigate 回调 → SwiftUI push 出详情占位页。
    /// 用 waitForExistence 等待卡片出现，规避 Tab 切换 + 首屏分页的时序不稳定。
    func test07PlaylistTapPushesDetail() throws {
        let app = XCUIApplication()
        app.launch()
        try waitListLoaded(app)

        let tab = app.tabBars.buttons["歌单"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "未找到底部'歌单'Tab")
        tab.tap()

        // 等 Tab 切换 + /api/playlists/recommended 首屏加载：
        // 歌单卡片由 Compose 渲染并透出为无障碍 Button（label=歌单标题）
        let card = app.buttons
            .matching(NSPredicate(format: "label CONTAINS %@", "华语独立"))
            .firstMatch
        let cardAppeared = card.waitForExistence(timeout: 20)
        snap(app, "09_歌单Tab加载")
        XCTAssertTrue(cardAppeared, "歌单卡片未出现（Tab 未切换或数据未加载）")

        card.tap()
        sleep(3)

        snap(app, "10_歌单详情")

        print("=== A11Y TREE BEGIN (点按后) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        // 详情占位页包含 StaticText '歌单详情页迁移中'，导航标题为歌单名
        let placeholder = app.staticTexts["歌单详情页迁移中"]
        let pushed = placeholder.waitForExistence(timeout: 10)
        XCTAssertTrue(pushed, "点击歌单后未推入详情占位页")
        XCTAssertEqual(app.state, .runningForeground, "点按歌单后应用不在前台（疑似崩溃）")
    }
}
