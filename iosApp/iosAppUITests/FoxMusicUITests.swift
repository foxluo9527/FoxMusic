import XCTest

/// FoxMusic iOS 端截屏驱动功能验收（批次 22：对齐原始 app 5 Tab 结构）。
///
/// 覆盖功能点：
/// 1. 首页 = feature:home commonMain 的 HomeContent（品牌头+搜索框+推荐歌单+为你精选）
/// 2. 底部 5 Tab：首页/发现/社区/消息/我的（与 Android AppDestinations 对齐）
/// 3. 发现页 = feature:discover commonMain 的 DiscoverContent（热搜词+热门榜单+新歌速递+热门歌手）
/// 4. 首页点歌单 → PlaylistDetailContent 详情页（真实曲目分页）
/// 5. 点歌播放 → 迷你播放条出现
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

    /// 首页滚动到"为你精选"并点第一首歌；返回是否成功唤起迷你播放条。
    ///
    /// 不依赖固定偏移：先把标题滚到一个"下方能容纳一整行歌曲"的位置（避开底部
    /// Tab 栏与迷你条），再在同一布局下逐个偏移试点（**尝试之间不要滚动** ——
    /// 滚动会重排，上一轮算出的坐标就失效了）。
    @discardableResult
    private func tapFirstRecommendedSong(_ app: XCUIApplication) -> Bool {
        var found = false
        for _ in 0..<12 {
            if app.staticTexts["为你精选"].exists { found = true; break }
            app.swipeUp()
            sleep(1)
        }
        guard found else { return false }

        let section = app.staticTexts["为你精选"]
        // 为下方首歌曲行腾出空间（Tab 栏 ~49pt + 安全区，再加上迷你条高度）
        for _ in 0..<6 {
            if section.frame.maxY + 120 < app.frame.height - 140 { break }
            app.swipeUp()
            sleep(1)
        }
        sleep(3) // 等分页首帧（recommendedMusic 首 5 首）

        // SectionHeader 下内边距 8 + 网格间距 ≈ +20 进入首行，行高约 72 → 中心约 +56
        for offset in [56, 80, 104] {
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: section.frame.midX, dy: section.frame.maxY + CGFloat(offset)))
                .tap()
            if app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 5) { return true }
        }
        return false
    }

    // MARK: - 首页

    /// 首页渲染：品牌头 FoxMusic + 5 Tab 结构。
    func test01HomeRendersBrandHeroAndTabs() throws {
        let app = XCUIApplication()
        app.launch()

        let brand = app.staticTexts["FoxMusic"]
        XCTAssertTrue(brand.waitForExistence(timeout: 20), "首页品牌头 'FoxMusic' 未出现")

        snap(app, "01_首页品牌头")

        print("=== TABBAR DIAG begin ===")
        print("tabBars.count=\(app.tabBars.count)")
        for i in 0..<app.tabBars.buttons.count {
            let b = app.tabBars.buttons.element(boundBy: i)
            print("  tab[\(i)] label='\(b.label)' hittable=\(b.isHittable)")
        }
        print("=== TABBAR DIAG end ===")

        for label in ["首页", "发现", "社区", "消息", "我的"] {
            XCTAssertTrue(app.tabBars.buttons[label].exists, "缺少 Tab：\(label)")
        }
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页"推荐歌单"区出现真实歌单标题（Ktor 拉 /api/playlists/recommended）。
    func test02HomeShowsRecommendedPlaylists() throws {
        let app = XCUIApplication()
        app.launch()

        let header = app.staticTexts["推荐歌单"]
        XCTAssertTrue(header.waitForExistence(timeout: 20), "首页'推荐歌单'区未出现")
        sleep(3) // 等歌单卡片填充
        snap(app, "02_首页推荐歌单")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页点歌单卡片 → 推入真实歌单详情页（PlaylistDetailContent，含'播放全部'）。
    func test03HomePlaylistTapOpensDetail() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 20), "首页'推荐歌单'区未出现")
        sleep(3)

        // 歌单卡片在"推荐歌单"标题下方：网格第一行，点标题下方一点的位置
        let header = app.staticTexts["推荐歌单"]
        let f = header.frame
        let tapPoint = CGPoint(x: f.minX + f.width * 0.15, y: f.maxY + 60)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: tapPoint.x, dy: tapPoint.y)).tap()
        sleep(5) // 等详情页 header + 曲目加载

        snap(app, "03_首页歌单详情")

        // PlaylistDetailContent 的 ActionButtonRow 有"播放全部"按钮
        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        let appeared = playAll.waitForExistence(timeout: 15)
        print("=== A11Y TREE BEGIN (首页歌单详情) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        XCTAssertTrue(appeared, "点按歌单后详情页未出现'播放全部'（详情页未渲染或数据未加载）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页"为你精选"点一首歌 → 迷你播放条出现并开始播放。
    func test04TapSongShowsMiniPlayer() throws {
        let app = XCUIApplication()
        app.launch()

        // 显式选中首页 Tab，规避启动后 Tab 状态不确定
        let homeTab = app.tabBars.buttons["首页"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 10))
        homeTab.tap()

        let tapped = tapFirstRecommendedSong(app)
        snap(app, "04_点歌后迷你条")
        XCTAssertTrue(tapped, "点按'为你精选'下首歌曲后迷你播放条未出现（点按未命中可点击行或播放未触发）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 发现页

    /// 切到"发现"Tab，验证 DiscoverContent 渲染（热门榜单区 + 新歌速递区）。
    func test05DiscoverTabRendersRanks() throws {
        let app = XCUIApplication()
        app.launch()

        let tab = app.tabBars.buttons["发现"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "未找到'发现'Tab")
        tab.tap()

        let ranksHeader = app.staticTexts["热门榜单"]
        let appeared = ranksHeader.waitForExistence(timeout: 25)
        snap(app, "05_发现页")

        print("=== A11Y TREE BEGIN (发现页) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        XCTAssertTrue(appeared, "发现页'热门榜单'区未出现（榜单接口未加载或 Tab 切换失败）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 发现页点榜单卡片 → 榜单详情页（PlaylistDetailContent，tracks 来自 /ranks/{id}）。
    func test06DiscoverRankTapOpensDetail() throws {
        let app = XCUIApplication()
        app.launch()

        let tab = app.tabBars.buttons["发现"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        XCTAssertTrue(app.staticTexts["热门榜单"].waitForExistence(timeout: 25))
        sleep(4) // 等榜单卡片 + rankDetails 预览

        // 把"热门榜单"滚到屏幕上半部，保证下方榜单 Pager 整卡可见
        let header = app.staticTexts["热门榜单"]
        for _ in 0..<6 {
            if header.frame.midY < app.frame.height * 0.45 { break }
            app.swipeUp(); sleep(1)
        }
        sleep(2)

        // 榜单卡片副标题（"N 首歌曲"）是卡片内文本；等不到就退回按标题推算坐标，
        // 避免因卡片副标题尚未渲染而误判（曾出现数据/时序抖动导致的假失败）。
        let subtitle = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "首歌曲")).firstMatch
        let tapPoint: CGPoint
        if subtitle.waitForExistence(timeout: 15) {
            let f = subtitle.frame
            // 点封面左侧区域（卡片左缘），避开文本自身可能的点击消费
            tapPoint = CGPoint(x: max(f.minX - 90, 10), y: f.midY)
        } else {
            // 兜底：卡片内 padding 16 + 封面 80 → 封面中心约在卡片顶部 +56dp
            let hf = header.frame
            tapPoint = CGPoint(x: hf.minX + 70, y: hf.maxY + 72)
        }
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: tapPoint.x, dy: tapPoint.y)).tap()
        sleep(5)

        snap(app, "06_榜单详情")

        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        let appeared = playAll.waitForExistence(timeout: 15)
        XCTAssertTrue(appeared, "点按榜单后详情页未出现'播放全部'")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页点歌 → 迷你条出现 → 点迷你条进入全屏播放器（含标题/歌词）。
    func test07MiniPlayerOpensFullPlayer() throws {
        let app = XCUIApplication()
        app.launch()

        let homeTab = app.tabBars.buttons["首页"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 10))
        homeTab.tap()

        XCTAssertTrue(tapFirstRecommendedSong(app), "点歌后迷你播放条未出现")

        // 点迷你条（标题区）进入全屏播放器
        let miniBar = app.buttons["miniPlayer.bar"]
        XCTAssertTrue(miniBar.waitForExistence(timeout: 5), "迷你条可点区域未出现")
        miniBar.tap()

        let fullTitle = app.staticTexts["fullPlayer.title"]
        let appeared = fullTitle.waitForExistence(timeout: 8)
        snap(app, "07_全屏播放器")
        XCTAssertTrue(appeared, "点迷你条未进入全屏播放器（全屏标题未出现）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页点搜索框 → 搜索页（feature:search 共享 UI）→ 热词面板 → 触发搜索出现结果 Tab。
    func test08HomeSearchOpensSearch() throws {
        let app = XCUIApplication()
        app.launch()

        let homeTab = app.tabBars.buttons["首页"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 10))
        homeTab.tap()

        let brand = app.staticTexts["FoxMusic"]
        XCTAssertTrue(brand.waitForExistence(timeout: 20), "首页品牌头未出现")
        let f = brand.frame
        // 搜索框在品牌标题下方
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: f.midX, dy: f.maxY + 45)).tap()
        sleep(4)
        snap(app, "08_搜索页")

        print("=== A11Y TREE BEGIN (搜索页) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        // 推荐面板：热词区标题
        let rec = app.staticTexts["推荐搜索"]
        XCTAssertTrue(rec.waitForExistence(timeout: 12), "搜索页未出现'推荐搜索'热词面板（SearchContent 未渲染）")

        // 点第一个热词 chip（两列网格第一行）触发搜索 → 结果区 Tab 出现
        let rf = rec.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: rf.midX * 0.5, dy: rf.maxY + 45)).tap()
        sleep(5)
        snap(app, "08_搜索结果")

        let musicTab = app.staticTexts["单曲"]
        XCTAssertTrue(musicTab.waitForExistence(timeout: 12), "点热词后未出现结果 Tab（搜索未触发）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 发现页点歌手 → 歌手详情页（feature:discover ArtistDetailContent，含"热门歌曲"）。
    func test09DiscoverArtistOpensDetail() throws {
        let app = XCUIApplication()
        app.launch()

        let tab = app.tabBars.buttons["发现"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        XCTAssertTrue(app.staticTexts["热门榜单"].waitForExistence(timeout: 25), "发现页未加载")
        sleep(3)

        // 滚动到"热门歌手"区（4 列歌手网格）
        var found = false
        for _ in 0..<10 {
            if app.staticTexts["热门歌手"].exists { found = true; break }
            app.swipeUp(); sleep(1)
        }
        XCTAssertTrue(found, "发现页'热门歌手'区未出现")

        let header = app.staticTexts["热门歌手"]
        for _ in 0..<6 {
            if header.frame.midY < app.frame.height * 0.45 { break }
            app.swipeUp(); sleep(1)
        }
        sleep(2)

        print("=== A11Y TREE BEGIN (发现页-热门歌手) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        // 点歌手网格第一行（第一列封面）
        let hf = header.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: max(hf.minX, 20) + 40, dy: hf.maxY + 60)).tap()
        sleep(5)
        snap(app, "09_歌手详情")

        let hotSongs = app.staticTexts["热门歌曲"]
        XCTAssertTrue(hotSongs.waitForExistence(timeout: 12), "点歌手后未进入歌手详情页（'热门歌曲'未出现）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 详情页返回

    /// 首页点歌单进详情 → 点详情页左上角"返回" → 必须回到首页。
    ///
    /// 回归用例：批次 22~25 里 PlaylistDetailComposeView 把 Kotlin 的 onBack 硬编码成
    /// 空闭包，而详情页又隐藏了系统导航栏（`.navigationBarHidden(true)`），
    /// 导致用户进详情后**无路可退**。
    func test10DetailBackReturnsToHome() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 20), "首页'推荐歌单'区未出现")
        sleep(3)

        // 进歌单详情
        let header = app.staticTexts["推荐歌单"]
        let hf = header.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: hf.minX + hf.width * 0.15, dy: hf.maxY + 60)).tap()
        sleep(5)

        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        XCTAssertTrue(playAll.waitForExistence(timeout: 15), "详情页未打开，无法测试返回")

        // 详情页左上角返回箭头（PlaylistDetailContent 的 navigationIcon，contentDescription="返回"）
        let back = app.buttons["返回"].exists
            ? app.buttons["返回"]
            : app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "返回")).firstMatch
        let hasBack = back.waitForExistence(timeout: 8)
        print("=== A11Y TREE BEGIN (详情页-找返回) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        XCTAssertTrue(hasBack, "详情页未出现'返回'按钮")
        back.tap()
        sleep(3)
        snap(app, "10_详情返回首页")

        // 回到首页：品牌头重新出现，且详情页的"播放全部"消失
        XCTAssertTrue(
            app.staticTexts["FoxMusic"].waitForExistence(timeout: 10),
            "点'返回'后未回到首页（品牌头未出现）"
        )
        XCTAssertFalse(playAll.exists, "点'返回'后仍停留在详情页")
        XCTAssertEqual(app.state, .runningForeground)
    }
}
