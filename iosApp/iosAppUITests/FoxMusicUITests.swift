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

        // "为你精选"位于推荐歌单网格之下（20 个卡片 ≈ 5 行），需滚动到可见才会进无障碍树
        var found = false
        for _ in 0..<10 {
            if app.staticTexts["为你精选"].exists {
                found = true
                break
            }
            app.swipeUp()
            sleep(1)
        }
        XCTAssertTrue(found, "首页'为你精选'区未出现（已滚动查找）")

        // 把"为你精选"标题滚到屏幕上半部，确保它下方的首首歌曲整行可见、点按不落到 Tab 栏/安全区
        let section = app.staticTexts["为你精选"]
        for _ in 0..<6 {
            let midY = section.frame.midY
            if midY < app.frame.height * 0.45 { break }
            app.swipeUp()
            sleep(1)
        }
        sleep(3) // 等分页首帧（recommendedMusic 首 5 首）

        // 首首歌曲：标题下方 12dp 间距 + 行内 8dp 上内边距 ≈ +20 进入行；+90 稳稳落在行中央
        let f = section.frame
        let tapX = f.midX
        var tapped = false
        for offset in [90, 120, 60] {
            let tapPoint = CGPoint(x: tapX, y: f.maxY + CGFloat(offset))
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: tapPoint.x, dy: tapPoint.y)).tap()
            let playBtn = app.buttons["miniPlayer.playPause"]
            if playBtn.waitForExistence(timeout: 6) {
                tapped = true
                break
            }
            // 偏移没命中点按区，先滚一点让首歌曲整行重新布局再试
            app.swipeUp()
            sleep(1)
        }
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

        // 榜单卡片是横滑 LazyRow：用卡片内的"首歌曲"副标题文本定位（父级 Card 可点）
        let subtitle = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "首歌曲")).firstMatch
        XCTAssertTrue(subtitle.waitForExistence(timeout: 10), "榜单卡片副标题未出现")
        let f = subtitle.frame
        // 点封面左侧区域（卡片左缘），避开文本自身可能的点击消费
        let tapPoint = CGPoint(x: max(f.minX - 90, 10), y: f.midY)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: tapPoint.x, dy: tapPoint.y)).tap()
        sleep(5)

        snap(app, "06_榜单详情")

        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        let appeared = playAll.waitForExistence(timeout: 15)
        XCTAssertTrue(appeared, "点按榜单后详情页未出现'播放全部'")
        XCTAssertEqual(app.state, .runningForeground)
    }
}
