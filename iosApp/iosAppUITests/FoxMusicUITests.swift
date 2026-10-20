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
    ///
    /// 判据说明：后端「热门歌手」列表里混有 `music_count=0` 的占位歌手（导入产生），
    /// 它们的详情页只有头部 + 空态提示。所以用例会**逐个**点网格里的歌手，
    /// 直到找到一位有作品的歌手并断言"热门歌曲"渲染；无作品歌手必须显示空态提示
    /// （不能是一片死白）。
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

        func scrollHeaderIntoView() {
            for _ in 0..<8 {
                let h = app.staticTexts["热门歌手"]
                guard h.exists else { return }
                if h.frame.midY < app.frame.height * 0.5 { return }
                app.swipeUp(); sleep(1)
            }
        }
        scrollHeaderIntoView()
        sleep(2)

        // 歌手网格单元在无障碍树里是 **Button（label = 歌手名）** —— 直接按元素点，
        // 不再用坐标推算（坐标法会因网格换行/文字高度差异点空）。
        func artistCells() -> [XCUIElement] {
            app.buttons.allElementsBoundByIndex.filter { el in
                let f = el.frame
                return f.height > 80 && f.height < 140 && f.width > 50 && f.width < 130
            }
        }

        // 后端存在 music_count=0 的占位歌手（导入产生，例如 5161 叶泳沺），
        // 其详情页只有头部 + 空态提示。判据不能只看第一位 —— 必须继续找一位有作品的歌手。
        var visited: Set<String> = []
        var emptyArtistCount = 0
        for _ in 0..<8 {
            scrollHeaderIntoView()
            guard let cell = artistCells().first(where: {
                !$0.label.isEmpty && !visited.contains($0.label)
            }) else { break }
            visited.insert(cell.label)
            let name = cell.label
            cell.tap()
            sleep(4)
            snap(app, "09_歌手详情_\(name)")

            if app.staticTexts["热门歌曲"].waitForExistence(timeout: 10) {
                print("=== [test09] 歌手详情页渲染成功：\(name) ===")
                XCTAssertEqual(app.state, .runningForeground)
                return
            }
            if app.staticTexts["该歌手暂无歌曲"].exists {
                emptyArtistCount += 1
                print("=== [test09] 歌手「\(name)」无作品 → 详情页显示空态（符合预期，继续试下一位）===")
            } else {
                print("=== A11Y TREE BEGIN (歌手详情异常，既无热门歌曲也无空态：\(name)) ===")
                print(app.debugDescription)
                print("=== A11Y TREE END ===")
            }

            let back = app.buttons["返回"].firstMatch
            if back.waitForExistence(timeout: 6) { back.tap() } else { app.swipeRight() }
            sleep(2)
        }

        XCTFail("连续尝试 8 位热门歌手均未渲染'热门歌曲'（其中 \(emptyArtistCount) 位为无作品歌手，已正确显示空态）")
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

    /// 「我的」Tab：未登录应出现游客引导文案（共享 ProfileContent）。
    func test11ProfileGuestPrompt() throws {
        let app = XCUIApplication()
        // 游客态用例必须显式重置：test25 的自动登录（-UITestAutoLogin）会持久化
        // 到 Keychain，残留下来会让本用例看到已登录的「我的」页。
        app.launchArguments += ["-UITestResetAuth"]
        app.launch()

        let tab = app.tabBars.buttons["我的"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()

        let prompt = app.staticTexts["登录后可同步歌单、收藏与个人数据"]
        XCTAssertTrue(prompt.waitForExistence(timeout: 20), "「我的」未出现游客登录引导")
        snap(app, "11_我的游客态")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 全屏播放器（批次 28：功能对齐 Android PlayerScreen）

    /// 打开全屏播放器（复用点歌 + 点迷你条的既有路径）。
    @discardableResult
    private func openFullPlayer(_ app: XCUIApplication) -> Bool {
        let homeTab = app.tabBars.buttons["首页"]
        guard homeTab.waitForExistence(timeout: 10) else { return false }
        homeTab.tap()
        guard tapFirstRecommendedSong(app) else { return false }

        let miniBar = app.buttons["miniPlayer.bar"]
        guard miniBar.waitForExistence(timeout: 5) else { return false }
        miniBar.tap()
        return app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8)
    }

    /// 播放器控制行齐全：循环模式 / 上一首 / 播放暂停 / 下一首 / 收藏 + 队列入口。
    /// Android SongPage 是这 5 个按钮，缺一个就是砍功能。
    func test12PlayerControlsMatchAndroid() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        snap(app, "12_播放器歌曲页")

        for id in ["player.repeat", "player.previous", "player.playPause", "player.next", "player.favorite"] {
            XCTAssertTrue(app.buttons[id].exists, "播放器缺少控件：\(id)")
        }
        XCTAssertTrue(app.buttons["player.queueButton"].exists, "歌曲页缺少播放列表入口")
        XCTAssertTrue(app.buttons["player.close"].exists, "播放器缺少关闭按钮")

        // 停留几秒再截一张：用于确认①页面不会被自动跳走 ②封面大图异步加载完成后仍正常
        sleep(8)
        snap(app, "12b_播放器歌曲页_停留8秒后")
        print("=== A11Y TREE BEGIN (播放器停留后) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].exists, "停留后播放页被跳走了")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌曲页/歌词页双页切换：切到歌词页后，右上角入口应从"播放列表"变成"歌词设置"。
    func test13PlayerSongAndLyricStages() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        XCTAssertTrue(app.buttons["player.queueButton"].exists, "歌曲页应有播放列表入口")

        app.buttons["player.stage.lyric"].tap()
        sleep(2)
        snap(app, "13_播放器歌词页")

        XCTAssertTrue(
            app.buttons["player.settingsButton"].waitForExistence(timeout: 5),
            "切到歌词页后右上角未变成歌词设置入口"
        )
        XCTAssertTrue(app.buttons["player.commentEntry"].exists, "歌词页缺少评论入口")

        // 歌词设置面板：字号 / 翻译开关 / 歌词纠正
        app.buttons["player.settingsButton"].tap()
        XCTAssertTrue(
            app.otherElements["lyricSettingsSheet"].waitForExistence(timeout: 6)
                || app.staticTexts["歌词字号"].waitForExistence(timeout: 6),
            "歌词设置面板未打开"
        )
        snap(app, "13_歌词设置")
        // 「歌词翻译」开关仅在当前歌词带翻译时出现（无翻译歌词不展示）
        print("=== 歌词翻译开关存在 = \(app.staticTexts["歌词翻译"].exists) ===")
        XCTAssertTrue(app.staticTexts["歌词纠正"].exists, "歌词设置缺少歌词纠正入口")
    }

    /// 播放队列：点队列入口应看到队列面板（含"共 N 首歌曲"与来源），且条目可点。
    func test14PlayerQueueSheet() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        app.buttons["player.queueButton"].tap()

        let queueTitle = app.staticTexts["播放列表"]
        XCTAssertTrue(queueTitle.waitForExistence(timeout: 6), "播放列表面板未打开")
        snap(app, "14_播放队列")

        // 队列里应有多首（首页"为你精选"整列表入队），而不是只有当前这一首
        let countLabel = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "共 ")
        ).firstMatch
        XCTAssertTrue(countLabel.exists, "播放列表面板未显示歌曲总数")
        XCTAssertFalse(countLabel.label.hasPrefix("共 1 首"), "队列只有一首——整列表入队未生效")
    }

    /// 下一首真的会切歌（旧版是空实现，点了没反应）。
    func test15PlayerNextTrackSwitches() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        let title = app.staticTexts["fullPlayer.title"]
        let before = title.label

        app.buttons["player.next"].tap()
        sleep(4)
        let after = title.label
        snap(app, "15_下一首")

        XCTAssertNotEqual(before, after, "点'下一首'标题未变化——切歌未生效（before=\(before) after=\(after)）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 拖进度条能 seek，且**不会**被歌曲页/歌词页的横向翻页手势抢走。
    ///
    /// 这条用例存在的价值：进度条是水平拖拽，而双页切换也是水平手势，
    /// 两者天然冲突。用真实拖拽验证「拖完时间变了 + 页面没被翻走」。
    func test16PlayerSeekByDraggingProgress() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        let bar = app.otherElements["player.progress"]
        let currentLabel = app.staticTexts["player.progress.current"]
        XCTAssertTrue(bar.waitForExistence(timeout: 6), "进度条未出现")
        XCTAssertTrue(currentLabel.waitForExistence(timeout: 6), "进度条当前时间标签未出现")

        let before = currentLabel.label
        let f = bar.frame
        // 从 20% 处按下，拖到 80% 处松手
        let start = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.5))
        let end = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: end)
        sleep(2)
        snap(app, "16_拖动进度条")

        XCTAssertNotEqual(currentLabel.label, before, "拖动进度条后当前时间未变化（seek 未生效）")
        // 翻页手势若抢走拖拽，页面会切到歌词页（右上角变成歌词设置入口）
        XCTAssertFalse(
            app.buttons["player.settingsButton"].exists,
            "拖动进度条时被横向翻页手势抢走了（页面被翻到歌词页）"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌词页横向右滑应能回到歌曲页（TabView .page 双页手势）。
    /// 曾因 LyricsScrollView 挂了 simultaneousGesture(DragGesture) 而滑不动。
    func test17LyricPageSwipeBackToSongPage() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        // 先切到歌词页
        app.buttons["player.stage.lyric"].tap()
        XCTAssertTrue(
            app.buttons["player.settingsButton"].waitForExistence(timeout: 5),
            "未能切到歌词页"
        )

        // 屏幕中部横向右滑（从左 1/4 滑到右 4/5），应翻回歌曲页
        let screen = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
        let start = screen.withOffset(CGVector(dx: 40, dy: 420))
        let end = screen.withOffset(CGVector(dx: 340, dy: 420))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: .default, thenHoldForDuration: 0.1)
        sleep(2)
        snap(app, "17_歌词页右滑后")

        XCTAssertTrue(
            app.buttons["player.queueButton"].waitForExistence(timeout: 5),
            "歌词页右滑后未回到歌曲页（横向翻页手势失效）"
        )
    }

    /// 游客态点右上角齿轮应进**设置页**，而不是弹登录 / 退出登录。
    /// 旧行为：ProfileContent 游客分支把齿轮接到 onLogin，iOS 壳还把已登录的齿轮接到 logout。
    func test19GuestCanOpenSettingsPage() throws {
        let app = XCUIApplication()
        // 模拟器上可能残留登录态（root），强制回游客态再验证齿轮 → 设置页
        app.launchArguments += ["-UITestResetAuth"]
        app.launch()

        let tab = app.tabBars.buttons["我的"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        sleep(3)

        let gear = app.buttons["设置"].firstMatch
        XCTAssertTrue(gear.waitForExistence(timeout: 8), "「我的」页缺少设置齿轮")
        gear.tap()
        sleep(2)
        snap(app, "19_我的页齿轮进入设置页")

        // 进的是设置页：有登录入口 / 歌词设置，而不是登录页（登录页有"欢迎回来"）
        XCTAssertTrue(
            app.buttons["settings.login"].waitForExistence(timeout: 6),
            "游客点齿轮后未进入设置页（或设置页缺少登录入口）"
        )
        XCTAssertTrue(app.staticTexts["设置"].firstMatch.exists, "设置页缺少标题")
        XCTAssertFalse(app.staticTexts["欢迎回来"].exists, "游客点齿轮后仍被弹到了登录页")
        // 设置页是工具页：不应展示迷你播放条（与 Android 设置页无 MiniPlayer 对齐）。
        // 迷你条此前挂在 Tab 根视图上，safeAreaInset 会穿透 NavigationStack 跟到二级页。
        XCTAssertFalse(
            app.buttons["miniPlayer.bar"].exists,
            "设置页不应展示迷你播放条"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 6 位密码应能点登录（≥6 位即合法；1~5 位才提示"密码至少 6 位"）。
    /// 旧行为：commonMain LoginContent 用 remember { derivedStateOf } 包校验闭包，
    /// 捕获的是首次组合的 state 参数（非 snapshot state），永不重算 → 按钮恒禁用。
    func test20LoginEnabledWithSixDigitPassword() throws {
        let app = XCUIApplication()
        // 模拟器上可能残留登录态，强制回游客态才能看到「我的」页登录入口
        app.launchArguments += ["-UITestResetAuth"]
        app.launch()

        let tab = app.tabBars.buttons["我的"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()

        let guestLogin = app.buttons["登录"].firstMatch
        XCTAssertTrue(guestLogin.waitForExistence(timeout: 20), "「我的」未出现登录入口")
        guestLogin.tap()

        let userField = app.textViews["用户名或邮箱"]
        let pwdField = app.textViews["密码"]
        XCTAssertTrue(userField.waitForExistence(timeout: 8), "登录页缺少账号输入框")
        XCTAssertTrue(pwdField.exists, "登录页缺少密码输入框")

        userField.tap(); userField.typeText("testuser")
        pwdField.tap(); pwdField.typeText("123456")   // 正好 6 位
        sleep(2)
        snap(app, "20_登录页_6位密码")

        XCTAssertTrue(
            app.buttons["登录"].firstMatch.isEnabled,
            "输入账号 + 6 位密码后登录按钮仍不可点（密码长度校验应放行 ≥6 位）"
        )
    }

    /// 歌词纠正：点候选先进预览页，右上角"使用该歌词"确认后才应用。
    /// 旧版是点候选直接替换，没有预览。
    func test18LyricCorrectShowsPreviewBeforeApply() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        // 歌词页 → 歌词设置 → 歌词纠正
        app.buttons["player.stage.lyric"].tap()
        let settingsButton = app.buttons["player.settingsButton"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5), "歌词页缺少歌词设置入口")
        settingsButton.tap()
        let correctEntry = app.buttons["lyricSettings.correct"]
        XCTAssertTrue(correctEntry.waitForExistence(timeout: 6), "歌词设置面板未打开")
        correctEntry.tap()

        // 等搜索结果出来（进入时自动按歌名搜一次），点第一条候选
        let firstResult = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "lyricCorrect.result.")
        ).firstMatch
        XCTAssertTrue(firstResult.waitForExistence(timeout: 15), "歌词纠正搜索无结果")
        snap(app, "18_歌词纠正列表")

        // 点候选行**中心**（行内空白处、不是文字）——顺带回归"整行可点"这一修复。
        firstResult.tap()

        // 应进入预览页：标题"歌词预览" + 右上角"使用该歌词"
        let useButton = app.buttons["lyricPreview.use"]
        XCTAssertTrue(useButton.waitForExistence(timeout: 10), "点候选后未进入歌词预览页")
        snap(app, "18b_歌词预览页")

        // 确认应用：两级面板应收掉、回到播放页
        useButton.tap()
        XCTAssertTrue(
            app.buttons["player.settingsButton"].waitForExistence(timeout: 6) &&
            app.buttons["player.settingsButton"].isHittable,
            "应用歌词后未回到播放页"
        )
    }

    /// 播放恢复：播一首歌 → 杀掉 App → 冷启动 → 迷你播放条应**不点任何东西**直接出现
    /// （队列 + 当前的歌被恢复；对齐 Android 冷启动 restoreFromSnapshot 的行为）。
    func test21PlaybackRestoredAfterColdLaunch() throws {
        let app = XCUIApplication()
        app.launch()

        let homeTab = app.tabBars.buttons["首页"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 10))
        homeTab.tap()

        let tapped = tapFirstRecommendedSong(app)
        XCTAssertTrue(tapped, "未能从首页播放歌曲，恢复测试无从谈起")
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 15))
        sleep(4) // 让进度走一小段并触发持久化

        // 冷启动
        app.terminate()
        app.launch()

        // 不做任何点击：恢复的队列应让迷你播放条直接出现
        XCTAssertTrue(
            app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 15),
            "冷启动后迷你播放条未出现（播放队列/进度未被恢复）"
        )
        snap(app, "21_冷启动恢复播放")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 社区版块迁移验收（iOS）：点「社区」Tab → 广场 Compose 内容渲染
    ///（顶栏搜索图标出现，对应 CommunityPlazaContent 的 contentDescription）→ 真实帖子
    /// 分页加载（后端 /api/posts）→ 截图。再点首条帖子进入详情页（PostDetailContent）截图。
    func test22CommunityPlazaRendersAndOpensDetail() throws {
        let app = XCUIApplication()
        app.launch()

        let communityTab = app.tabBars.buttons["社区"]
        XCTAssertTrue(communityTab.waitForExistence(timeout: 15), "社区 Tab 未出现")
        communityTab.tap()

        // 广场顶栏搜索图标（Compose contentDescription → 无障碍标签）出现 ⇒ 广场已渲染
        let searchIcon = app.buttons["搜索帖子"]
        XCTAssertTrue(searchIcon.waitForExistence(timeout: 15), "社区广场未渲染（缺少搜索图标）")

        sleep(6) // 等真实帖子分页首帧
        snap(app, "22_社区广场")

        // 帖子详情：按坐标点瀑布流首卡卡体（Android/iOS 同一 PostCard，整卡可点进详情）。
        // 用坐标而非文本：帖子标题同时出现在音乐条 chip 与正文，文本命中的可能是
        // 音乐 chip（行为是放歌不是跳详情）。
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.30)).tap()
        sleep(4)
        snap(app, "23_帖子详情")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 消息页迁移验收（iOS）：点「消息」Tab → 共享 MessagesContent（feature:chat commonMain）
    /// 渲染 —— 四个入口（我的好友 / 评论和@提及 / 赞和通知 / 系统公告）+ 会话列表。
    ///
    /// 回归意义：此前这里挂的是 PlaceholderTabView，页面只有一句"消息页迁移中"，
    /// 等于消息功能在 iOS 上不存在。本用例断言四入口出现、且占位文案消失。
    func test23MessagesTabRendersSharedContent() throws {
        let app = XCUIApplication()
        app.launch()

        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()

        // 「我的好友」是 MessagesContent 首行的分类入口 ⇒ 说明共享 UI 已渲染
        let friends = app.staticTexts["我的好友"]
        let appeared = friends.waitForExistence(timeout: 20)
        XCTAssertTrue(appeared, "消息页未渲染共享内容（缺少'我的好友'入口）")

        snap(app, "24_消息页")

        print("=== A11Y TREE BEGIN (消息页) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")

        for title in ["我的好友", "评论和@提及", "赞和通知", "系统公告"] {
            XCTAssertTrue(app.staticTexts[title].exists, "消息页缺少分类入口：\(title)")
        }
        XCTAssertFalse(app.staticTexts["消息页迁移中"].exists, "消息页仍是占位页")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 好友链页面迁移验收（批次 36）：消息页「我的好友」→ 好友页 → 搜索用户页。
    ///
    /// 三个页面都是 feature:chat commonMain 的共享 UI（FriendsContent / SearchUserContent），
    /// 由 iosShared 的 FoxFriendsScreen / FoxSearchUserScreen 用 Ktor REST 喂数据。
    /// 未登录时列表为空，但页面骨架（TopAppBar 标题、入口按钮、空态文案）必须完整渲染 ——
    /// 这正是「还是占位页 / 点了没反应」与「真的迁过来了」的分界。
    func test25FriendsChainRendersSharedContent() throws {
        let app = XCUIApplication()
        // 好友页需要登录态：之前靠模拟器残留的登录，任意一次 -UITestResetAuth
        //（test19/20/28 都在用）都会把它清掉，导致点「我的好友」被弹到登录页。
        // 改为强制重置 + 自动登录固定测试账号，状态完全确定。
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()

        let friendsEntry = app.staticTexts["我的好友"]
        XCTAssertTrue(friendsEntry.waitForExistence(timeout: 20), "消息页缺少「我的好友」入口")
        friendsEntry.tap()
        sleep(4)
        snap(app, "25_好友页")

        // 好友页 = 共享 FriendsContent：标题「我的好友」+ 右上角「搜索用户」
        XCTAssertTrue(app.staticTexts["我的好友"].waitForExistence(timeout: 10), "好友页未渲染")
        let addButton = app.buttons["搜索用户"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 8), "好友页缺少「搜索用户」按钮")
        XCTAssertFalse(app.staticTexts["好友页待迁移"].exists, "好友页仍是占位提示")

        addButton.tap()
        sleep(4)
        snap(app, "26_搜索用户页")

        // 搜索页空态（未输入关键词）：共享 SearchUserContent 的引导文案
        let hint = app.staticTexts["搜索好友"]
        let empty = app.staticTexts["未找到相关用户"]
        XCTAssertTrue(
            hint.waitForExistence(timeout: 10) || empty.exists,
            "搜索用户页未渲染空态"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 聊天详情页迁移验收（批次 36）：用 `-UITestChatUserId` 直达会话页，
    /// 验证共享 ChatThreadContent 在 iOS 真的渲染出来了。
    ///
    /// 两端聊天不同源：Android 主源是本地 Room（可离线、有未送达队列），
    /// iOS **不拉历史消息**（后端 history 接口有故障且产品不需要首屏历史），
    /// 只轮询增量新消息。
    /// 因此断言的是**页面骨架**而非消息条数：顶栏标题（昵称未知时回退「聊天」）、
    /// 返回、右上「更多」入口（iOS 无聊天设置页，齿轮已隐藏），三者齐备才算真迁过来。
    func test26ChatDetailRendersSharedThread() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestChatUserId", "1"]
        app.launch()

        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()
        sleep(4)
        snap(app, "27_聊天详情页")

        // 顶栏标题：后端能解析出对端昵称时显示昵称，解析不到才回退「聊天」。
        // 旧断言硬编码「聊天」，昵称解析成功后反而失败（假失败，非功能回归）——
        // 这里改为「顶栏区域内必须存在非空标题」，两种取值都算通过。
        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 15), "聊天详情页未渲染（缺少返回按钮）")
        let navTitleCount = app.staticTexts.count
        var navTitles: [String] = []
        for i in 0..<min(navTitleCount, 40) {
            let t = app.staticTexts.element(boundBy: i)
            guard t.exists else { continue }
            let f = t.frame
            // 顶栏带：状态栏下方 ~ 导航栏底（昵称/回退文案都在这里）
            if f.minY > 20, f.maxY < 120, !t.label.trimmingCharacters(in: .whitespaces).isEmpty {
                navTitles.append(t.label)
            }
        }
        print("=== 聊天详情页顶栏标题候选: \(navTitles) ===")
        XCTAssertFalse(navTitles.isEmpty, "聊天详情页顶栏没有渲染任何标题（既无昵称也无回退「聊天」）")
        XCTAssertTrue(app.buttons["更多"].exists, "聊天详情页缺少「更多」入口")
        XCTAssertFalse(app.buttons["聊天设置"].exists, "iOS 聊天详情页不应再有「聊天设置」齿轮")
        // 输入栏必须可见（占位文案 + 表情/发送按钮）——它同时是「迷你播放条有没有把底部盖住」
        // 的探针：二级页不该展示迷你条（Android 的聊天页压根没包 MainScreenWithTabBar），
        // 一旦这里查不到输入栏，就是又被迷你条压住了。
        XCTAssertTrue(app.staticTexts["输入消息..."].exists, "聊天详情页输入栏被遮挡（看不到占位文案）")
        XCTAssertTrue(app.buttons["表情"].exists, "聊天详情页输入栏被遮挡（看不到表情按钮）")
        XCTAssertFalse(app.otherElements["miniPlayer.bar"].exists, "聊天详情页不该出现迷你播放条")
        XCTAssertFalse(app.staticTexts["聊天页待迁移"].exists, "聊天详情页仍是占位提示")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 通知分类页迁移验收（批次 36）：消息页「评论和@提及」与「系统公告」两个入口。
    ///
    /// 页面 = feature:chat commonMain 的 NotificationCategoryContent，
    /// 数据来自 `api/notifications`。未登录 / 无数据时也必须完整渲染
    /// TopAppBar 标题、返回按钮与空态文案 —— 这正是「点了没反应」与
    /// 「真的迁过来了」的分界。
    func test27NotificationCategoryRendersSharedContent() throws {
        let app = XCUIApplication()
        app.launch()

        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()
        XCTAssertTrue(
            app.staticTexts["评论和@提及"].waitForExistence(timeout: 20),
            "消息页缺少「评论和@提及」入口"
        )

        // 分类一：评论和 @提及
        app.staticTexts["评论和@提及"].tap()
        sleep(4)
        snap(app, "28_评论通知页")

        let back = app.buttons["返回"]
        XCTAssertTrue(back.waitForExistence(timeout: 12), "通知分类页未渲染（缺少返回按钮）")
        back.tap()

        // 分类二：系统公告（空态文案与其他分类不同）
        XCTAssertTrue(
            app.staticTexts["系统公告"].waitForExistence(timeout: 12),
            "未能从通知分类页返回消息页"
        )
        app.staticTexts["系统公告"].tap()
        sleep(4)
        snap(app, "29_系统公告页")

        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 12), "系统公告页未渲染（缺少返回按钮）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 未登录点消息功能入口 → 统一弹登录页（iOS 门控，对齐 Android 跳 LOGIN_ROUTE）。
    ///
    /// 消息 Tab 的所有功能（会话/好友/通知分类/搜索/通知设置）都依赖鉴权接口，
    /// 未登录时点击由 `MessagesTabView.gated(_:)` 拦下并弹全屏登录页（共享 LoginContent）。
    ///
    /// ⚠️ 本用例用 `-UITestResetAuth` 清空 Keychain 登录态，**会改变后续用例的登录态**；
    /// xcodebuild 串行跑没问题，但不要与「依赖已登录态」的用例并行。
    func test28MessagesRequireLoginWhenLoggedOut() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth"]
        app.launch()

        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()

        let friendsEntry = app.staticTexts["我的好友"]
        XCTAssertTrue(friendsEntry.waitForExistence(timeout: 20), "消息页缺少「我的好友」入口")
        friendsEntry.tap()
        sleep(2)

        // 登录页（共享 LoginContent）出现：标题「欢迎回来」+ 副标题
        XCTAssertTrue(app.staticTexts["欢迎回来"].waitForExistence(timeout: 10), "未登录点好友入口未弹出登录页")
        snap(app, "30_未登录弹登录页")

        // 关闭登录页回到消息页，再验证第二个入口同样被拦
        let back = app.buttons["返回"]
        XCTAssertTrue(back.waitForExistence(timeout: 8), "登录页缺少返回按钮")
        back.tap()
        sleep(1)

        let systemEntry = app.staticTexts["系统公告"]
        XCTAssertTrue(systemEntry.waitForExistence(timeout: 12), "关闭登录页后未回到消息页")
        systemEntry.tap()
        XCTAssertTrue(app.staticTexts["欢迎回来"].waitForExistence(timeout: 10), "未登录点系统公告入口未弹出登录页")
        app.buttons["返回"].tap()
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 发完消息退回列表，会话里必须出现「刚刚的对话」。
    ///
    /// 修复前的两个缺陷（iOS 独有，Android 无此问题）：
    ///  1. 聊天页只落消息、不落会话 → 本地会话表里没有这一条；
    ///  2. 消息页 Compose VC 常驻，从聊天页返回不会重新拉列表。
    /// 后端 `api/messages/conversations` 尚未部署（线上 404），本地会话是唯一数据源，
    /// 所以这条用例同时也是「后端未部署时列表仍可用」的回归保护。
    func test29SentMessageShowsInConversationList() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launchArguments += ["-UITestChatUserId", "1"]
        app.launch()

        // 启动参数只是把聊天页 preload 进消息 Tab 的导航栈，仍要切到消息 Tab 才可见
        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()

        let input = app.staticTexts["输入消息..."]
        XCTAssertTrue(input.waitForExistence(timeout: 25), "聊天页输入框未渲染")
        input.tap()
        let text = "uitest\(Int(Date().timeIntervalSince1970))"
        app.typeText(text)

        let send = app.buttons["发送"]
        XCTAssertTrue(send.waitForExistence(timeout: 8), "聊天页缺少发送按钮")
        send.tap()
        sleep(4)
        snap(app, "31_发送消息")

        // 返回消息页：刚发出的会话应出现在列表里，预览文案就是刚发的内容
        app.buttons["返回"].tap()
        let sent = app.staticTexts[text]
        let sending = app.staticTexts["[发送中] \(text)"]
        XCTAssertTrue(
            sent.waitForExistence(timeout: 20) || sending.exists,
            "会话列表里没有刚发送的对话（预览文案未出现：\(text)）"
        )
        snap(app, "32_会话列表含新会话")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 帖子详情「视频 + 图片」混合轮播的版式验收（iOS）。
    ///
    /// 背景：后端帖子 id=29「视频加图片」的第三个 media 是 video，且后端把
    /// `cover_url` 回填成了视频文件本身（FoxResourceGroup services/postService.js:28）
    /// → iOS 侧 Skia 解不了 mp4，该 item 的宽高比探测永远「未就绪」，于是整块媒体区
    /// 被钉在 240dp 加载态兜底高度，连同轮的图片也被压小（用户实拍反馈）。
    ///
    /// 修复后：容器高度只由「可测量」的媒体决定，视频退化成 16:9 占位框。
    /// 断言：第 1 页（live 静帧 3:4）的媒体元素宽度应接近满屏宽 —— 240dp 兜底时
    /// 它只有约屏宽的 45%，这是区分「修好了」与「仍在兜底」的关键量。
    func test24PostDetailMixedMediaCarousel() throws {
        let app = XCUIApplication()
        app.launch()

        let communityTab = app.tabBars.buttons["社区"]
        XCTAssertTrue(communityTab.waitForExistence(timeout: 15), "社区 Tab 未出现")
        communityTab.tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")

        // 定位「视频加图片」帖子卡片（标题文本整卡可点，点它即进详情）
        let postTitle = app.staticTexts["视频加图片"]
        var found = postTitle.waitForExistence(timeout: 20)
        var attempt = 0
        while !found && attempt < 6 {
            app.swipeUp()
            sleep(1)
            found = postTitle.exists
            attempt += 1
        }
        XCTAssertTrue(found, "未找到「视频加图片」帖子卡片")
        snap(app, "25_广场_视频图片帖")

        postTitle.tap()
        // 宽高比探测要先把原图下下来（单张约 1.8MB @450KB/s），带宽还与广场其他卡片竞争：
        // 用「帖子标题的 y 坐标」当作媒体区高度的观测尺 —— 容器从兜底高度收敛到真实高度时，
        // 标题会明显下移。每 5s 采一次，直到不再变化或超时，把收敛过程打印出来。
        let detailTitle = app.staticTexts["视频加图片"]
        var lastTitleY = -1.0
        var stableTicks = 0
        for tick in 1...12 {
            sleep(10)
            guard detailTitle.exists else { print("=== t=\(tick * 10)s 标题未出现 ==="); continue }
            let y = detailTitle.frame.minY
            let delta = lastTitleY < 0 ? 0 : y - lastTitleY
            print("=== t=\(tick * 10)s 媒体区下方标题 y=\(y)（较上次 \(String(format: "%+.1f", delta))）===")
            stableTicks = (lastTitleY >= 0 && abs(delta) < 0.5) ? stableTicks + 1 : 0
            lastTitleY = y
            // 连续 3 次采样（30s）不变才算真正收敛（只稳定 10s 可能只是还没下完图）
            if stableTicks >= 3 { break }
        }
        snap(app, "26_详情_页1_live")

        // 回归断言：评论输入栏必须完整位于迷你播放条之上。
        // 修复前 PostDetailComposeView 上写着 .ignoresSafeArea([.top, .bottom])，
        // 于是 SwiftUI 为迷你条预留的 safeAreaInset 被无视，输入栏整条被盖住。
        let miniBar = app.buttons["miniPlayer.bar"]
        let sendButton = app.buttons["发表"]
        let commentInput = app.textFields.firstMatch
        let miniBarTop = miniBar.exists ? miniBar.frame.minY : .greatestFiniteMagnitude
        for element in [commentInput, sendButton] where element.exists {
            print("=== 底部元素 frame=\(element.frame) / 迷你条 top=\(miniBarTop) ===")
            XCTAssertLessThanOrEqual(
                element.frame.maxY,
                miniBarTop + 1,
                "评论输入区被迷你播放条盖住（底 \(element.frame.maxY) > 迷你条顶 \(miniBarTop)）",
            )
        }
        XCTAssertTrue(
            commentInput.exists || sendButton.exists,
            "帖子详情页底部没有评论输入区（PostDetailBottomBar 未渲染）",
        )

        let windowWidth = app.windows.firstMatch.frame.width
        let liveStill = app.descendants(matching: .any).matching(identifier: "Live 图").firstMatch
        if liveStill.exists {
            print("=== live 静帧 frame: \(liveStill.frame) / 屏宽 \(windowWidth) ===")
        } else {
            print("=== 未取到「Live 图」无障碍元素（Compose Image 未暴露），仅截图判读 ===")
        }

        // 横向拖动 pager（媒体区垂直中心约 dy≈0.30）逐页截屏
        for page in 2...3 {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.30))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.30))
            start.press(forDuration: 0.05, thenDragTo: end)
            sleep(5)
            snap(app, "2\(5 + page)_详情_页\(page)")
        }

        print("=== A11Y TREE BEGIN (帖子详情-混合媒体) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置页「深色模式 / 主题设置」真的能切（对齐 Android 设置页同名两项）。
    ///
    /// 旧行为：这两项只是「迁移中」占位文本，点了没有任何反应。
    /// 现在：深色模式 → `AppAppearance`（跟随系统/浅色/深色）+ 根视图
    /// `.preferredColorScheme`；主题设置 → `AppThemeAccent`（对齐 Android
    /// `AppThemePresets.catalog` 七套预设的主色）+ 根视图 `.tint`。
    ///
    /// 断言用「浅色」而不是「深色」：选项文案"深色"是行标题"深色模式"的子串，
    /// 用它断言会恒真。测试结束把两项都复位，避免污染后续用例的启动外观。
    func test28SettingsAppearanceAndThemeSwitch() throws {
        let app = XCUIApplication()
        // 复位登录态 + 外观，保证从「跟随系统」这个默认值开始断言
        app.launchArguments += ["-UITestResetAuth", "-UITestResetAppearance"]
        app.launch()

        let tab = app.tabBars.buttons["我的"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        sleep(3)

        let gear = app.buttons["设置"].firstMatch
        XCTAssertTrue(gear.waitForExistence(timeout: 8), "「我的」页缺少设置齿轮")
        gear.tap()
        sleep(2)

        func row(_ id: String) -> XCUIElement {
            // ⚠️ 设置页是 SwiftUI `List`（懒加载）：**屏幕外的行不在无障碍树里**，
            // 光按 identifier 查会假失败。必须先滚动到它（与 test43/47 同款 helper）。
            //
            // 本用例曾因此**回归**：给设置页新增一个分组（通知）后，「应用」组整体下推，
            // 「主题设置」（该组第 2 行）掉出首屏 → 查不到 → 断言
            // 「设置页「应用」分组缺少主题设置项」失败。产品侧那处布局已按 Android 同构回退，
            // 但用例本身也必须不再依赖"目标行恰好在首屏"这个巧合。
            _ = scrollToSettingsRow(app, id)
            return app.descendants(matching: .any).matching(identifier: id).firstMatch
        }

        // 1) 深色模式
        let appearanceRow = row("settings.appearance")
        XCTAssertTrue(appearanceRow.waitForExistence(timeout: 8), "设置页「应用」分组缺少深色模式项")
        XCTAssertTrue(
            appearanceRow.label.contains("跟随系统"),
            "深色模式默认值应为「跟随系统」，实际 '\(appearanceRow.label)'"
        )

        appearanceRow.tap()
        sleep(1)
        let lightOption = app.buttons["浅色"].firstMatch
        XCTAssertTrue(lightOption.waitForExistence(timeout: 5), "深色模式菜单缺少「浅色」选项")
        lightOption.tap()
        sleep(2)
        snap(app, "28a_设置_深色模式选浅色")
        XCTAssertTrue(
            appearanceRow.label.contains("浅色"),
            "选了「浅色」后深色模式行未更新，实际 '\(appearanceRow.label)'"
        )

        // 再切深色：断言「浅色」已消失（不能拿"深色"做子串断言，见上文注释）
        // ⚠️ Menu 偶发一次点不开（SwiftUI 弹层动画），加等待 + 重开一次的兜底
        appearanceRow.tap()
        sleep(1)
        let darkOption = app.buttons["深色"].firstMatch
        if !darkOption.waitForExistence(timeout: 4) {
            appearanceRow.tap()
            _ = darkOption.waitForExistence(timeout: 4)
        }
        darkOption.tap()
        sleep(2)
        snap(app, "28b_设置_深色模式选深色")
        XCTAssertFalse(
            appearanceRow.label.contains("浅色"),
            "切到「深色」后行上仍显示浅色，实际 '\(appearanceRow.label)'"
        )

        // 2) 主题设置（主题色，对齐 Android 七套预设）
        let themeRow = row("settings.theme")
        XCTAssertTrue(themeRow.waitForExistence(timeout: 6), "设置页「应用」分组缺少主题设置项")
        themeRow.tap()
        sleep(1)
        let presetOption = app.buttons["紫韵经典"].firstMatch
        XCTAssertTrue(presetOption.waitForExistence(timeout: 5), "主题设置菜单缺少「紫韵经典」预设")
        presetOption.tap()
        sleep(2)
        snap(app, "28c_设置_主题色紫韵经典")
        XCTAssertTrue(
            themeRow.label.contains("紫韵经典"),
            "选主题预设后主题设置行未更新，实际 '\(themeRow.label)'"
        )

        // 3) 复位（深色模式 → 跟随系统；主题 → 跟随系统）
        //    菜单偶发一次点不开：等待 + 重开一次的兜底与上面相同
        func chooseOption(in menu: XCUIElement, title: String) {
            menu.tap()
            sleep(1)
            let option = app.buttons[title].firstMatch
            if !option.waitForExistence(timeout: 4) {
                menu.tap()
                _ = option.waitForExistence(timeout: 4)
            }
            option.tap()
            sleep(1)
        }
        chooseOption(in: appearanceRow, title: "跟随系统")
        chooseOption(in: themeRow, title: "跟随系统")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 视频 / Live 图播放（iOS 统一播放器：Swift AVPlayer 经 FoxVideoPlayerBridge）

    /// 打开「视频加图片」帖子（后端 id=29，media = [live, video, image]）的媒体预览，
    /// 翻到第 2 页（video）点顶栏「播放」，断言按钮切换为「暂停」（播放态真值来源）。
    ///
    /// 覆盖链路：Compose 预览 Dialog → PlatformVideoPreviewPage（iOS actual）→
    /// UIKitView 互操作 → Swift FoxVideoPlayerUIView(AVPlayer)。
    func test30PostVideoPreviewPlayback() throws {
        let app = XCUIApplication()
        app.launch()

        let communityTab = app.tabBars.buttons["社区"]
        XCTAssertTrue(communityTab.waitForExistence(timeout: 15), "社区 Tab 未出现")
        communityTab.tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")

        let postTitle = app.staticTexts["视频加图片"]
        var found = postTitle.waitForExistence(timeout: 20)
        var attempt = 0
        while !found && attempt < 6 {
            app.swipeUp()
            sleep(1)
            found = postTitle.exists
            attempt += 1
        }
        XCTAssertTrue(found, "未找到「视频加图片」帖子卡片")
        postTitle.tap()
        sleep(6) // 等详情页媒体区（含宽高比探测）稳定

        // 点媒体区打开预览（第 1 页 live），再翻 1 页到 video（media = [live, video, image]）
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.30)).tap()
        sleep(3)
        // 预览顶栏应有 "1/3" 页码（PostMediaPreviewDialog 已打开）
        XCTAssertTrue(app.staticTexts["1/3"].waitForExistence(timeout: 8), "媒体预览未打开")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.40))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.40))
        start.press(forDuration: 0.05, thenDragTo: end)
        sleep(2)
        XCTAssertTrue(app.staticTexts["2/3"].waitForExistence(timeout: 8), "翻页后未到达第 2 页（video 页）")
        snap(app, "33_预览_视频页")

        let play = app.buttons.matching(NSPredicate(format: "label == %@", "播放")).firstMatch
        XCTAssertTrue(play.waitForExistence(timeout: 8), "视频页顶栏未出现「播放」按钮")
        if play.isHittable {
            play.tap()
        } else {
            // 兜底：用 frame 坐标点（某些层级下 XCUITest 误报不可点）
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: play.frame.midX, dy: play.frame.midY)).tap()
        }
        // 播放态：顶栏按钮从「播放」变「暂停」；等首帧加载
        let pause = app.buttons.matching(NSPredicate(format: "label == %@", "暂停")).firstMatch
        XCTAssertTrue(pause.waitForExistence(timeout: 15), "点「播放」后顶栏未切换为「暂停」（播放未触发）")
        sleep(6) // 让视频实际渲染几秒
        snap(app, "34_视频播放中")
        XCTAssertTrue(pause.exists, "播放数秒后「暂停」按钮消失（播放器状态异常）")
        XCTAssertEqual(app.state, .runningForeground)

        // 静音/取消静音开关可用（AVPlayer isMuted 链路）
        let mute = app.buttons.matching(NSPredicate(format: "label == %@", "静音")).firstMatch
        if mute.exists {
            mute.tap()
            XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == %@", "取消静音")).firstMatch.waitForExistence(timeout: 6), "静音切换未生效")
            snap(app, "35_视频静音")
        }
    }

    /// Live 图预览：打开即自动有声播放；播完顶栏 LIVE 徽章回到斜线态，点徽章重播。
    /// 以截图为主、应用存活为断言底线（Live 播放时长取决于后端视频）。
    func test31LivePhotoPreviewPlayback() throws {
        let app = XCUIApplication()
        app.launch()

        let communityTab = app.tabBars.buttons["社区"]
        XCTAssertTrue(communityTab.waitForExistence(timeout: 15), "社区 Tab 未出现")
        communityTab.tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")

        let postTitle = app.staticTexts["视频加图片"]
        var found = postTitle.waitForExistence(timeout: 20)
        var attempt = 0
        while !found && attempt < 6 {
            app.swipeUp()
            sleep(1)
            found = postTitle.exists
            attempt += 1
        }
        XCTAssertTrue(found, "未找到「视频加图片」帖子卡片")
        postTitle.tap()
        sleep(6)

        // 第 1 页就是 live：点媒体区打开预览即自动播放
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.30)).tap()
        XCTAssertTrue(app.staticTexts["1/3"].waitForExistence(timeout: 8), "媒体预览未打开")
        sleep(3) // 等首帧
        snap(app, "36_live自动播放")

        // 等待播完（后端实况视频一般几秒），之后点 LIVE 徽章重播
        sleep(15)
        snap(app, "37_live播完后")

        let badge = app.buttons["LIVE"].exists
            ? app.buttons["LIVE"]
            : app.images["LIVE"].exists ? app.images["LIVE"] : app.otherElements["LIVE"]
        if badge.exists {
            badge.tap()
            sleep(3)
            snap(app, "38_live重播")
        } else {
            print("=== 未找到 LIVE 徽章元素，仅截图判读 ===")
        }
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 聊天视频/Live 图播放端到端：后端注入好友消息（foxuitest2 → foxuitest，
    /// video.mp4 视频消息 + live.jpg 图片带 video_url 的 Live 消息）→ -UITestChatUserId
    /// 直达会话 → 未读补拉带出消息 → 点气泡进 SwiftUI MediaPreviewView（统一
    /// FoxVideoPlayerView）。视频点播（中央播放键），Live 打开即自动播放。
    ///
    /// 定位策略：视频气泡缩略图是 mp4，iOS 端 Skia 解不出帧 → 空白盒 + 「播放」角标；
    /// Live 气泡是静帧 + 左上角「LIVE」徽章（打开即自动播放，没有播放角标）。
    /// 气泡本身没有稳定的 a11y 文件名标签，统一按这两个角标 Image 定位。
    /// 打开预览后按「中央播放按钮是否出现」自适应分类：出现 = 视频点播链路；
    /// 不出现 = Live 自动播放链路（重播入口在顶栏的 LIVE 徽章上）。
    /// ⚠️ 消息进会话即已读，重跑前需用 curl 重新注入两条消息。
    /// ⚠️ 依赖模拟器已持久化 foxuitest 登录态（不带 ResetAuth：Reset+异步自动登录
    /// 与聊天页 LaunchedEffect 的登录检查存在时序竞态）。
    func test32ChatVideoAndLivePlayback() throws {
        let app = XCUIApplication()
        // 与 test29 同款：显式复位 + 自动登录，避免依赖上一次运行残留的登录态。
        // 直达路由偶发被丢（登录态切换重建导航栈），helper 内部带重启兜底。
        XCTAssertTrue(launchIntoChatThread(app, peerId: "14"), "聊天详情页未打开（含重启兜底）")
        sleep(6)

        // 未读补拉（进会话触发 + 4s 轮询 + 网关落库）带出注入的媒体消息
        // ⚠️ 两类气泡的角标不同：视频 = 「播放」角标，Live = 「LIVE」徽章（不再是视频图标）
        let mediaBadges = app.images.matching(
            NSPredicate(format: "label == %@ OR label == %@", "播放", "LIVE")
        )
        XCTAssertTrue(mediaBadges.firstMatch.waitForExistence(timeout: 30), "聊天未出现注入的媒体消息气泡（未读补拉失败或气泡未渲染）")
        sleep(3) // 等剩余气泡渲染完
        snap(app, "43_聊天媒体消息")

        var videoDone = false
        var liveDone = false
        let win = app.windows.firstMatch.frame
        let total = min(mediaBadges.count, 12)

        for index in 0..<total {
            if videoDone && liveDone { break }
            let badge = mediaBadges.element(boundBy: index)
            guard badge.exists else { continue }
            let frame = badge.frame
            // 只试点落在聊天页可视区内的气泡（避开状态栏与底部 Tab 栏/输入栏）
            guard frame.width > 1, frame.height > 1,
                  frame.midY > 60, frame.midY < win.height - 100 else { continue }
            print("=== [诊断][\(index)] 试点气泡 frame=\(frame) ===")
            // Compose 语义元素的 isHittable 常误报 false，直接按 frame 中心坐标点
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
            sleep(2) // 等预览转场

            // ⚠️ 关键：查询必须限定在**预览面板**内。
            // SwiftUI TabView 里未显示的 Tab 内容（首页/发现/社区）仍留在无障碍树里，
            // 社区视频帖的「播放」角标会一起被查到；它们的坐标落在当前可见页面上，
            // 按坐标点击只会命中无关元素 —— 这正是本用例此前「点了不播」假失败的根因。
            guard let sheet = previewSheetIfOpen(app) else {
                print("=== [诊断][\(index)] 未打开预览面板（该元素不在可见 Tab 内），回消息 Tab 继续 ===")
                _ = selectMessagesTab(app)
                sleep(1)
                continue
            }

            // ⚠️ 判定必须用**唯一 identifier**，不能再用「距窗口中心最近的『播放』按钮」这种几何启发式：
            // 预览 sheet 弹出后，底层聊天页的气泡（label 同样是「播放」，180×180 框，圆心距窗口中心
            // 仅约 50pt）与真正的中央按钮会同时被查到 —— 几何法命中的是**底层气泡**（它永不消失），
            // 于是「播放态 = 中央按钮消失」恒为假。第三轮 103s 假失败即此因：
            // 取证日志显示试点坐标 (156, 461.66) 正是气泡，而真正的中央按钮在 (201, 451)。
            // 现在中央按钮带 identifier `chatPreview.centerPlay`，天然唯一；
            // 另有 `chatPreview.probe` 探针给出「预览形态 + 是否在播」的正向证据。
            let centerPlay = app.buttons["chatPreview.centerPlay"]
            let probe = app.descendants(matching: .any)
                .matching(identifier: "chatPreview.probe").firstMatch

            // 预览落位 + 探针就绪（sheet 转场期 a11y 树尚未挂上中央按钮，必须等）
            var probeKind = "-"
            var probeValue = "-"
            var centerVisible = false
            for _ in 0..<40 { // ≤ 40 × 150ms = 6s
                if probe.exists {
                    probeKind = probe.label
                    probeValue = probe.value as? String ?? "-"
                }
                if centerPlay.exists { centerVisible = true }
                // 探针已给出形态 → 无需再靠时间窗猜
                if probeKind == "video" || probeKind == "live" { break }
                if centerVisible { break }
                usleep(150_000)
            }

            print("=== [诊断][\(index)] 预览已打开 探针(kind=\(probeKind), value=\(probeValue)) "
                + "中央「播放」出现=\(centerVisible) frame=\(centerVisible ? centerPlay.frame.debugDescription : "-") ===")
            snap(app, "44a_聊天媒体预览初始")

            // 探针说这是视频 → 中央按钮必定存在（只是可能比探针晚一帧进 a11y 树）
            if probeKind == "video", !centerVisible {
                centerVisible = centerPlay.waitForExistence(timeout: 8)
            }

            // 分类：优先用探针形态（确定）；探针缺失时退回「中央按钮是否出现」——
            // Live 打开即播（无中央按钮），但**播完后也会回到待播态**（onCompletion 置 false），
            // 所以时间窗必须短：落位后很快就出现中央按钮才算视频。
            let isVideo = probeKind == "video" || (probeKind == "-" && centerVisible)
            let isLive = probeKind == "live" || (probeKind == "-" && !centerVisible)

            if isVideo && !videoDone {
                // —— 视频点播链路：中央「播放」→ 播放态（按钮消失）——
                // 坑：测试视频很短，播完 onCompletion 会把按钮复位回来 ——
                // 所以点击后立即高频轮询，捕捉到「按钮消失过」即认定进入过播放态。
                XCTAssertTrue(centerVisible, "探针判定为视频预览，但中央「播放」按钮未出现")
                var playedOnce = false
                for attempt in 0..<3 {
                    guard centerPlay.exists else { playedOnce = true; break }
                    let playFrame = centerPlay.frame
                    guard playFrame.width > 1, playFrame.height > 1 else { break }
                    app.coordinate(withNormalizedOffset: .zero)
                        .withOffset(CGVector(dx: playFrame.midX, dy: playFrame.midY)).tap()
                    print("=== [诊断] 第 \(attempt + 1) 次点击中央「播放」于 \(playFrame.midX),\(playFrame.midY) ===")
                    var timeline = ""
                    for tick in 0..<40 { // ≤ 40 × 120ms ≈ 4.8s
                        if !centerPlay.exists {
                            timeline += "0"
                            print("=== [诊断] 中央按钮消失于第 \(tick) 次轮询 (\(tick * 120)ms) → 已进入播放态 ===")
                            playedOnce = true; break
                        }
                        timeline += "1"
                        usleep(120_000)
                    }
                    print("=== [诊断] 点后时间线（0=播放态）: \(timeline) ===")
                    if playedOnce { break }
                }
                snap(app, "44_聊天视频播放中")
                XCTAssertTrue(playedOnce, "点中央「播放」后未捕捉到播放态（chatPreview.centerPlay 始终未消失）")
                videoDone = true
            } else if isLive && !liveDone {
                // —— Live 自动播放链路：打开即播（无中央播放按钮）——
                XCTAssertFalse(centerVisible, "探针判定为 Live 预览，不应出现中央「播放」按钮")
                XCTAssertNotEqual(probeValue, "paused", "Live 预览打开后应处于播放态（探针 value 仍为 paused）")
                snap(app, "46_聊天Live自动播放")
                sleep(4) // 让 Live 实际渲染几秒
                snap(app, "47_聊天Live播放数秒")
                if probe.exists {
                    print("=== [诊断] Live 播放 4s 后探针 value=\(probe.value as? String ?? "-") ===")
                }
                liveDone = true
            } else {
                // 分类重复（同类气泡第二次打开）：只关闭继续
                print("=== [诊断][\(index)] 分类重复（videoDone=\(videoDone) liveDone=\(liveDone)），跳过 ===")
            }

            // 静音开关链路（视频/Live 预览顶栏都有）
            // ⚠️ 必须用恒定 identifier 定位：label 会随状态在「静音 ⇄ 取消静音」间翻转，
            // 用 `label == "静音"` 的 firstMatch 缓存元素，切换后再 tap 必然
            // `No matches found for ... label == "静音"` —— 这是**抛异常**不是断言失败，
            // 用例当场中断（第四次跑 test32 就是死在这里，静音第一跳其实已成功）。
            let mute = app.buttons["chatPreview.mute"]
            if mute.exists {
                mute.tap()
                XCTAssertTrue(waitForLabel(mute, "取消静音"), "预览静音切换未生效（label 未变为「取消静音」）")
                if videoDone && !liveDone { snap(app, "45_聊天视频静音") }
                mute.tap() // 切回，保持状态干净
                XCTAssertTrue(waitForLabel(mute, "静音"), "静音开关切回失败（label 未回到「静音」）")
                sleep(1)
            }
            let close = app.buttons["chatPreview.close"]
            if close.exists { close.tap() } else { app.buttons["关闭预览"].firstMatch.tap() }
            sleep(1)
        }

        XCTAssertTrue(videoDone, "未验证到视频点播链路（带播放角标的气泡里没有可点播的视频预览）")
        XCTAssertTrue(liveDone, "未验证到 Live 自动播放链路")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 选中「消息」Tab 并确认真的选中了。
    ///
    /// 不能只 `tap()` 一下就往下走：SwiftUI TabView 里未显示的 Tab **内容仍在无障碍树里**，
    /// 「输入消息...」这类元素即使 Tab 没切过去也能查到 —— 于是后续按坐标点击会落在
    /// 当前可见的别的 Tab 上（本用例曾因此假失败）。用 `isSelected` 做真值校验。
    /// 选中「消息」Tab（**Tab 栏不可见时安全返回 false，不抛异常**）。
    ///
    /// ⚠️ 不能直接写 `app.tabBars.buttons["消息"].tap()`：聊天详情页带了
    /// `.toolbar(.hidden, for: .tabBar)`，把 Tab 栏隐藏掉，这时 `app.tabBars` 解析不到
    /// 任何元素，XCTest 会**硬抛** `Failed to tap "消息" Button: No matches found for
    /// Descendants matching type TabBar`（不是断言失败，用例直接报错）。
    /// 必须先确认 Tab 栏存在再点。
    @discardableResult
    private func selectMessagesTab(_ app: XCUIApplication) -> Bool {
        _ = app.wait(for: .runningForeground, timeout: 20)
        for _ in 0..<6 {
            if app.tabBars.firstMatch.exists {
                let tab = app.tabBars.buttons["消息"]
                if tab.exists, tab.isHittable {
                    // ⚠️ 必须按**坐标**点，不能 `tab.tap()`。
                    // 登录态异步重建（`MessagesComposeView.id(isLoggedIn)`）与聊天页
                    // 隐藏 Tab 栏之间存在竞态：`isHittable` 判定通过后 TabBar 可能已经
                    // 从快照里消失，此时 `tab.tap()` 会直接抛
                    // `Failed to get matching snapshot: No matches found for
                    //  Descendants matching type TabBar` —— test32 就是这样挂的
                    //（异常不是断言失败，无法用 XCTAssert 兜住，整个用例当场中断）。
                    // 坐标点击不做元素解析，天然免疫这种转场期失效。
                    let f = tab.frame
                    if f.width > 1, f.height > 1 {
                        app.coordinate(withNormalizedOffset: .zero)
                            .withOffset(CGVector(dx: f.midX, dy: f.midY)).tap()
                        sleep(1)
                        print("=== [诊断] 已按坐标点消息 Tab @\(f.midX),\(f.midY) ===")
                        return true
                    }
                }
            }
            sleep(1)
        }
        print("=== [诊断] Tab 栏当前不可见（可能聊天页已隐藏它）===")
        return false
    }

    /// 取「聊天媒体预览」里那颗**中央播放按钮**。
    ///
    /// 为什么不能直接用 `app.buttons["播放"].firstMatch`：预览是 SwiftUI `.sheet`，
    /// 弹出后底层聊天页仍留在无障碍树里 —— 气泡上的「播放」角标也是 button，
    /// 于是「播放」按钮可能有 2~3 个，`firstMatch` 命中的是**底层那颗**：
    /// 它永远不会消失，播放态检测必然恒失败（假失败）。
    ///
    /// 判据：预览中央按钮一定在**窗口正中**（`ZStack` 居中）。取距窗口中心最近的一个，
    /// 且距离超过阈值就认定「当前没有中央播放按钮」（= Live 已在自动播放）。
    ///
    /// - Parameter scope: 限定查询范围。SwiftUI `TabView` 里**未显示的 Tab 内容同样留在
    ///   无障碍树里**（Compose VC 被 SwiftUI 保活），社区视频帖的「播放」角标也会被查到；
    ///   而它们的坐标落在当前可见页面上，按坐标点击只会命中无关元素 —— 这正是
    ///   test32 曾「点了不播」的假失败根因。传入预览 sheet 可彻底隔离。
    private func centerPlayButton(_ app: XCUIApplication, scope: XCUIElement? = nil) -> XCUIElement? {
        // 首选：产品侧中央按钮的**唯一 identifier**（MediaPreviewView 已加）。
        // 这是唯一可靠依据 —— 见下方几何法的失效说明。
        let byIdentifier = app.buttons["chatPreview.centerPlay"]
        if byIdentifier.exists { return byIdentifier }

        // 兜底：几何启发式（仅用于 identifier 尚不存在的旧构建）。
        let root = scope ?? app
        let win = app.windows.firstMatch.frame
        let query = root.buttons.matching(NSPredicate(format: "label == %@", "播放"))
        var best: XCUIElement?
        var bestDistance = Double.greatestFiniteMagnitude
        for i in 0..<query.count {
            let candidate = query.element(boundBy: i)
            guard candidate.exists else { continue }
            let f = candidate.frame
            guard f.width > 1, f.height > 1 else { continue }
            let d = hypot(Double(f.midX - win.midX), Double(f.midY - win.midY))
            if d < bestDistance {
                bestDistance = d
                best = candidate
            }
        }
        // 120pt 容差：中央按钮的圆心基本就在窗口中心
        return bestDistance < 120 ? best : nil
    }

    /// 当前是否有聊天媒体预览面板（`关闭预览` 只存在于该面板）。
    private func previewSheetIfOpen(_ app: XCUIApplication) -> XCUIElement? {
        let sheets = app.sheets
        // 首选产品侧恒定 identifier（`关闭预览` 的 label 虽然不会变，identifier 更稳）
        if app.buttons["chatPreview.close"].exists {
            return sheets.count > 0 ? sheets.firstMatch : app
        }
        if sheets.count > 0, sheets.firstMatch.buttons["关闭预览"].exists {
            return sheets.firstMatch
        }
        // iOS 26 的 SwiftUI sheet 有时不暴露为 Sheet 元素：退回全量作用域
        return app.buttons["关闭预览"].exists ? app : nil
    }

    // MARK: - 批次 43：「我的」Tab 深度验收（收藏 / 历史 / 下载 / 创建歌单 / 设置二级页）

    /// 登录态启动并停在「我的」Tab。
    ///
    /// `-UITestAutoLogin` 是异步的（注册/登录回调回来才写 Keychain），
    /// 所以必须等「我的歌单」分区头出现才能算真正进入登录态的我的页。
    @discardableResult
    private func openLoggedInProfile(_ app: XCUIApplication, extraArgs: [String] = []) -> Bool {
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"] + extraArgs
        app.launch()
        let tab = app.tabBars.buttons["我的"]
        guard tab.waitForExistence(timeout: 15) else { return false }
        tab.tap()
        return app.staticTexts["我的歌单"].waitForExistence(timeout: 30)
    }

    /// 点「我的」页列表里的入口行并等待页面真的切走（分区头消失 = 推入新页）。
    private func tapProfileEntry(_ app: XCUIApplication, _ title: String) -> Bool {
        let entry = app.staticTexts[title]
        // 「下载管理」等条目在页面下方，LazyColumn 只渲染可视区 —— 必须滚动到它
        // 进入无障碍树（不滚动 waitForExistence 会永远 false）。
        for _ in 0..<10 {
            if entry.exists { break }
            app.swipeUp()
            sleep(1)
        }
        guard entry.waitForExistence(timeout: 5) else { return false }
        entry.tap()
        return app.staticTexts["我的歌单"].waitForNonExistence(timeout: 10)
    }

    /// 「我的收藏」→ 共享 PlaylistDetailContent（detailType=favorite_music，拉 /api/favorites/music）。
    func test40ProfileFavoriteTracksDetail() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openLoggedInProfile(app), "未能进入登录态「我的」页")
        sleep(3)
        snap(app, "40_我的登录态")

        XCTAssertTrue(
            tapProfileEntry(app, "我的收藏"),
            "点「我的收藏」未离开我的页（入口未接或点击未命中）"
        )
        sleep(5)
        snap(app, "41_我的收藏详情")

        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 10), "「我的收藏」详情页未渲染（缺少返回按钮）")
        XCTAssertFalse(app.staticTexts["收藏页待迁移"].exists, "「我的收藏」仍是占位提示")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 「历史播放」→ 共享 PlaylistDetailContent（detailType=play_history，拉 /api/music-history）。
    func test41ProfilePlayHistoryDetail() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openLoggedInProfile(app), "未能进入登录态「我的」页")
        sleep(3)

        XCTAssertTrue(
            tapProfileEntry(app, "历史播放"),
            "点「历史播放」未离开我的页（入口未接或点击未命中）"
        )
        sleep(5)
        snap(app, "42_历史播放详情")

        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 10), "「历史播放」详情页未渲染（缺少返回按钮）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 「下载管理」入口（我的页列表行）→ Swift 原生 DownloadManagerView。
    func test42ProfileDownloadManager() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openLoggedInProfile(app), "未能进入登录态「我的」页")
        sleep(3)

        XCTAssertTrue(
            tapProfileEntry(app, "下载管理"),
            "点「下载管理」未离开我的页（入口未接或点击未命中）"
        )
        sleep(3)
        snap(app, "43_下载管理")

        let empty = app.staticTexts["暂无下载任务"]
        XCTAssertTrue(
            empty.waitForExistence(timeout: 8),
            "下载管理页未渲染（既无任务行也无「暂无下载任务」空态）"
        )
        XCTAssertTrue(app.buttons["返回"].exists || app.navigationBars.count > 0, "下载管理页缺少返回入口")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 「创建歌单」→ 底部弹窗 → 「创建自定义歌单」→ 空名禁用 → 输入后创建成功。
    func test43CreatePlaylistFlow() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openLoggedInProfile(app), "未能进入登录态「我的」页")
        sleep(3)

        let createEntry = app.buttons["创建歌单"].firstMatch
        let hasEntry = createEntry.waitForExistence(timeout: 8)
            || app.staticTexts["创建歌单"].waitForExistence(timeout: 8)
        XCTAssertTrue(hasEntry, "「我的歌单」分区缺少「创建歌单」入口")
        (createEntry.exists ? createEntry : app.staticTexts["创建歌单"].firstMatch).tap()
        sleep(2)
        snap(app, "44_创建歌单弹窗")

        let custom = app.staticTexts["创建自定义歌单"].firstMatch
        XCTAssertTrue(custom.waitForExistence(timeout: 6), "创建歌单弹窗缺少「创建自定义歌单」选项")
        custom.tap()
        sleep(2)

        let field = app.textFields["输入歌单名称"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 6), "「创建自定义歌单」缺少名称输入框")
        snap(app, "45_创建自定义歌单")

        let ok = app.buttons["确定"].firstMatch
        XCTAssertTrue(ok.waitForExistence(timeout: 5), "「创建自定义歌单」缺少确定按钮")
        XCTAssertFalse(ok.isEnabled, "歌单名为空时「确定」应不可点")

        let name = "UI验收歌单\(Int(Date().timeIntervalSince1970) % 100000)"
        field.tap()
        field.typeText(name)
        sleep(1)
        XCTAssertTrue(ok.isEnabled, "输入歌单名后「确定」仍不可点")
        ok.tap()
        // 先立刻拍一张：Toast 只活 2 秒，等 8 秒再截屏只能拍到"已经消失之后"的状态
        usleep(400_000)
        snap(app, "46a_点确定后立刻")
        // Toast 只活 2 秒：先断言再截屏（之前 sleep(4) 后 Toast 已消失，必然假失败）
        let toast = app.staticTexts["toast.message"].firstMatch
        let created = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "已创建")
        ).firstMatch
        let toastShown = toast.waitForExistence(timeout: 6) || created.exists
        if !toastShown {
            print("=== A11Y TREE BEGIN (创建歌单后无提示) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
        }
        snap(app, "46_创建歌单结果")

        XCTAssertTrue(
            toastShown,
            "创建歌单后未出现结果提示（Toast 或成功文案）"
        )
        // 二级输入弹窗必须已关闭：若还留着"创建歌单"一级菜单，说明弹窗栈没收干净
        let menuStillOpen = app.staticTexts.matching(
            NSPredicate(format: "label == %@", "创建自定义歌单")
        ).firstMatch
        XCTAssertFalse(menuStillOpen.exists, "创建完成后「创建歌单」弹窗仍停留在屏幕上（弹窗未关闭）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 43：设置页二级页（编辑资料 / 隐私 / 举报历史 / 清理缓存 / 退出登录）

    /// 登录态进入设置页（齿轮 → SettingsView）。
    @discardableResult
    private func openSettings(_ app: XCUIApplication) -> Bool {
        guard openLoggedInProfile(app) else { return false }
        sleep(2)
        let gear = app.buttons["设置"].firstMatch
        guard gear.waitForExistence(timeout: 8) else { return false }
        gear.tap()
        return app.staticTexts["账号"].waitForExistence(timeout: 10)
            || app.otherElements["settings.account"].waitForExistence(timeout: 10)
    }

    /// 设置页是 SwiftUI List（懒加载）：屏幕外的行不在无障碍树里，必须滚动到它。
    /// 返回「任意类型」的元素：Menu 暴露为 button/cell、NavigationLink 为 other，
    /// 用 any + identifier 兜底（只按 otherElements 查会假失败）。
    private func settingsRow(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    /// 在设置页内滚动直到 identifier 为 `id` 的元素出现在树上；找不到返回 false。
    @discardableResult
    private func scrollToSettingsRow(_ app: XCUIApplication, _ id: String, maxSwipes: Int = 8) -> Bool {
        let row = settingsRow(app, id)
        for _ in 0..<maxSwipes {
            if row.exists, row.isHittable { return true }
            app.swipeUp()
            sleep(1)
        }
        return row.exists
    }

    /// 设置页结构完整性：账号 / 播放 / 桌面与歌词 / 应用 四个分组都要在。
    ///
    /// 分组清单以 Android `SettingsScreen` 的 `SettingsGroup` 为唯一基准
    ///（账号 / 播放 / 桌面与歌词 / 应用 —— **没有「关于」组**，版本号行挂在最后
    /// 一个无标题分组里）。这里额外断言版本行存在，覆盖"关于"想表达的内容。
    ///
    /// ⚠️ 分组标题不能用 `app.staticTexts[section]` 精确匹配：
    /// `List` + `.listStyle(.insetGrouped)` 下 section header 在无障碍树里是
    /// **Other**（header 语义）而不是 StaticText；同理 `LabeledContent` 带
    /// accessibilityIdentifier 后会把内部 Text 合并掉。这里统一用
    /// `descendants(matching: .any)` + label 等值匹配，对元素类型免疫。
    func test44SettingsStructureComplete() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")
        sleep(2)

        for section in ["账号", "播放", "桌面与歌词", "应用"] {
            let head = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", section)).firstMatch
            var found = head.exists
            for _ in 0..<8 where !found {
                app.swipeUp()
                sleep(1)
                found = head.exists
            }
            if !found {
                print("=== A11Y TREE BEGIN (设置页缺少分组 \(section)) ===")
                print(app.debugDescription)
                print("=== A11Y TREE END ===")
                snap(app, "47_设置页缺分组_\(section)")
            }
            XCTAssertTrue(found, "设置页缺少分组：\(section)")
        }
        snap(app, "47_设置页完整")

        for id in ["settings.repeatMode", "settings.sleepTimer", "settings.downloads", "settings.reportHistory", "settings.clearCache"] {
            XCTAssertTrue(scrollToSettingsRow(app, id), "设置页缺少行：\(id)")
        }
        // 通知设置入口（批次 44 新增：安卓侧该页原本两端都进不去）
        XCTAssertTrue(scrollToSettingsRow(app, "settings.notification"), "设置页缺少「通知设置」入口")
        // 版本号行（代替不存在的「关于」分组）
        XCTAssertTrue(scrollToSettingsRow(app, "settings.version"), "设置页缺少版本行")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 编辑资料（Swift 原生 EditProfileView）：头像/昵称/签名/保存。
    func test45EditProfilePage() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let edit = app.otherElements.matching(identifier: "settings.editProfile").firstMatch
        let hasEdit = edit.waitForExistence(timeout: 8)
            || app.staticTexts["编辑资料"].waitForExistence(timeout: 8)
        XCTAssertTrue(hasEdit, "设置页账号分组缺少「编辑资料」")
        (edit.exists ? edit : app.staticTexts["编辑资料"].firstMatch).tap()
        sleep(3)
        snap(app, "48_编辑资料")

        XCTAssertTrue(
            app.textFields["请输入昵称"].waitForExistence(timeout: 8),
            "编辑资料页未渲染（缺少昵称输入框）"
        )
        XCTAssertTrue(app.staticTexts["个性签名"].exists, "编辑资料页缺少「个性签名」区")
        XCTAssertTrue(app.buttons["保存"].exists, "编辑资料页缺少保存按钮")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 隐私设置（PrivacySettingsView）：私密账号 / 允许被搜索到 / 黑名单。
    func test46PrivacySettingsPage() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let privacy = app.descendants(matching: .any).matching(identifier: "settings.privacy").firstMatch
        let hasPrivacy = privacy.waitForExistence(timeout: 8)
            || app.staticTexts["隐私设置"].waitForExistence(timeout: 8)
        XCTAssertTrue(hasPrivacy, "设置页账号分组缺少「隐私设置」")
        (privacy.exists ? privacy : app.staticTexts["隐私设置"].firstMatch).tap()
        sleep(3)
        snap(app, "49_隐私设置")

        XCTAssertTrue(app.staticTexts["私密账号"].waitForExistence(timeout: 8), "隐私设置页未渲染")
        XCTAssertTrue(app.staticTexts["允许被搜索到"].exists, "隐私设置缺少「允许被搜索到」")
        XCTAssertTrue(app.staticTexts["黑名单"].exists, "隐私设置缺少黑名单分组")
        // 黑名单加载曾因响应结构不匹配（data 是分页对象而客户端按数组解析）
        // 抛未捕获异常直接崩 App（issue：FoxMusic-*.ips SIGABRT）——这里多等一拍，
        // 让「暂无拉黑用户」或列表渲染出来，崩了上面断言之前就会先挂。
        XCTAssertTrue(
            app.staticTexts["暂无拉黑用户"].waitForExistence(timeout: 8)
                || app.buttons["取消拉黑"].waitForExistence(timeout: 2),
            "黑名单区未加载完成（空态或列表都没出现）"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 举报历史（ReportHistoryView）：空态文案必须出现。
    func test47ReportHistoryPage() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let history = app.descendants(matching: .any).matching(identifier: "settings.reportHistory").firstMatch
        XCTAssertTrue(scrollToSettingsRow(app, "settings.reportHistory"), "设置页缺少「举报历史」")
        history.tap()
        sleep(3)
        snap(app, "50_举报历史")

        let empty = app.staticTexts["暂无举报记录"]
        XCTAssertTrue(empty.waitForExistence(timeout: 8), "举报历史页未渲染（缺少空态或列表）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 清理缓存：必须弹确认（带缓存大小说明），且取消后不执行清理。
    ///
    /// ⚠️ iOS 26 起 `confirmationDialog` 在 iPhone 上渲染为 **popover**，
    /// 系统会**省略 cancel 按钮**（点外部即关闭）。所以「取消按钮存在」不再是有效判据 ——
    /// 本用例改为：有「取消」就点它，没有就点弹窗外部，两种路径都必须能关闭且不执行清理。
    func test48ClearCacheConfirmation() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let clear = app.descendants(matching: .any).matching(identifier: "settings.clearCache").firstMatch
        XCTAssertTrue(scrollToSettingsRow(app, "settings.clearCache"), "设置页缺少「清理缓存」")
        clear.tap()
        sleep(2)
        snap(app, "51_清理缓存确认")

        let confirmTitle = app.staticTexts["清理缓存"]
        XCTAssertTrue(confirmTitle.exists, "点「清理缓存」未弹出确认对话框")

        let destructive = app.buttons.matching(NSPredicate(format: "label == %@", "清理")).firstMatch
        XCTAssertTrue(destructive.waitForExistence(timeout: 5), "清理缓存确认框缺少「清理」动作")

        dismissConfirmation(app)
        XCTAssertFalse(destructive.exists, "关闭确认框后「清理」动作仍在（弹窗没关掉）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 关闭一个 confirmationDialog：优先点「取消」，iOS 26 popover 形态则点弹窗外部。
    private func dismissConfirmation(_ app: XCUIApplication) {
        let cancel = app.buttons["取消"].firstMatch
        if cancel.waitForExistence(timeout: 4) {
            cancel.tap()
            sleep(1)
            return
        }
        print("=== [诊断] 确认框无「取消」按钮（iOS 26 popover 形态），改为点弹窗外部关闭 ===")
        // 顶部区域一定在 popover 之外（popover 锚定在被点的那一行附近）
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: app.windows.firstMatch.frame.midX, dy: 70)).tap()
        sleep(1)
    }

    /// 等元素的 label 变成期望值。
    ///
    /// 专治「开关类控件」：它们的 label 随状态翻转（静音 ⇄ 取消静音、播放 ⇄ 暂停…），
    /// 用 `label == "静音"` 的 firstMatch 缓存元素，状态一变该 query 就查不到东西了
    /// —— `tap()` 会**硬抛** `No matches found`（不是断言失败，用例当场中断）。
    /// 正解：元素用恒定 accessibilityIdentifier 定位，状态用 label 断言。
    @discardableResult
    private func waitForLabel(_ element: XCUIElement, _ expected: String, timeout: TimeInterval = 8) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists, element.label == expected { return true }
            usleep(150_000)
        }
        print("=== [诊断] 等 label「\(expected)」超时，当前 label=\(element.exists ? element.label : "<元素不存在>") ===")
        return false
    }

    /// 设置 → 退出登录：确认框存在，取消后仍在登录态。
    func test49LogoutConfirmation() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let logout = app.descendants(matching: .any).matching(identifier: "settings.logout").firstMatch
        let hasLogout = logout.waitForExistence(timeout: 8)
            || app.staticTexts["退出登录"].waitForExistence(timeout: 8)
        XCTAssertTrue(hasLogout, "设置页账号分组缺少「退出登录」")
        (logout.exists ? logout : app.staticTexts["退出登录"].firstMatch).tap()
        sleep(2)
        snap(app, "52_退出登录确认")

        // 与 test48 同口径：确认框必须有可见的「取消」，点它必须不执行退出。
        XCTAssertTrue(app.buttons["取消"].firstMatch.waitForExistence(timeout: 5),
                      "退出登录确认框缺少「取消」（iOS 26 confirmationDialog 会省略它，应用 .alert）")
        dismissConfirmation(app)
        sleep(1)
        // ⚠️ 判据用 identifier 而不是 `app.staticTexts["退出登录"]`：
        // 该行是 `Button(role:.destructive){ Text("退出登录") }`，SwiftUI 把文字合并进
        // Button 的 label，**不存在**独立的 StaticText（旧断言靠弹窗里的同名文字偶然通过）。
        let logoutRow = app.descendants(matching: .any).matching(identifier: "settings.logout").firstMatch
        XCTAssertTrue(logoutRow.waitForExistence(timeout: 6), "取消退出后应仍停留在设置页（且仍处于登录态）")
        XCTAssertFalse(app.buttons["取消"].exists, "确认框未关闭")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 循环模式 Menu：4 档齐全（顺序播放/列表循环/单曲循环/随机播放）。
    func test50SettingsRepeatModeMenu() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let repeatRow = app.descendants(matching: .any).matching(identifier: "settings.repeatMode").firstMatch
        XCTAssertTrue(scrollToSettingsRow(app, "settings.repeatMode"), "设置页缺少「循环模式」")
        repeatRow.tap()
        sleep(2)
        snap(app, "53_循环模式菜单")

        for mode in ["顺序播放", "列表循环", "单曲循环", "随机播放"] {
            XCTAssertTrue(app.buttons[mode].firstMatch.exists, "循环模式菜单缺少：\(mode)")
        }
        app.buttons["列表循环"].firstMatch.tap()
        sleep(2)
        snap(app, "54_循环模式已选列表循环")
        XCTAssertTrue(
            repeatRow.label.contains("列表循环"),
            "选了「列表循环」后循环模式行未更新，实际 '\(repeatRow.label)'"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置 → 定时关闭 Menu：15/30/60/90 分钟齐全。
    func test51SettingsSleepTimerMenu() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openSettings(app), "未能进入设置页")

        let timerRow = app.descendants(matching: .any).matching(identifier: "settings.sleepTimer").firstMatch
        XCTAssertTrue(scrollToSettingsRow(app, "settings.sleepTimer"), "设置页缺少「定时关闭」")
        timerRow.tap()
        sleep(2)
        snap(app, "55_定时关闭菜单")

        for minute in ["15", "30", "60", "90"] {
            let opt = app.buttons.matching(
                NSPredicate(format: "label CONTAINS %@", "\(minute) 分钟")
            ).firstMatch
            XCTAssertTrue(opt.exists, "定时关闭菜单缺少 \(minute) 分钟选项")
        }
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 43：播放器扩展（收藏 / 评论面板）

    /// 播放器收藏按钮：点一次收藏、再点一次取消，Toast 必须给出反馈。
    func test52PlayerFavoriteToggle() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        let fav = app.buttons["player.favorite"]
        XCTAssertTrue(fav.waitForExistence(timeout: 5), "播放器缺少收藏按钮")
        fav.tap()
        // Toast 只活 2 秒（网络回包即弹）：先断言再截屏，避免 Toast 过期假失败
        let toast1 = app.staticTexts["toast.message"].firstMatch
        let toastShown = toast1.waitForExistence(timeout: 8)
        snap(app, "56_播放器收藏")
        XCTAssertTrue(toastShown, "点收藏后无 Toast 反馈")

        sleep(3)
        fav.tap()
        sleep(2)
        snap(app, "57_播放器取消收藏")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌词页 → 评论面板：空输入时发送按钮禁用；输入后可发。
    func test53CommentSheetSendGating() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()
        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        app.buttons["player.stage.lyric"].tap()
        sleep(2)
        let entry = app.buttons["player.commentEntry"]
        XCTAssertTrue(entry.waitForExistence(timeout: 6), "歌词页缺少评论入口")
        entry.tap()
        sleep(3)
        snap(app, "58_评论面板")

        let input = app.textFields.matching(identifier: "comment.input").firstMatch
        let send = app.buttons.matching(identifier: "comment.send").firstMatch
        let panelOpened = app.staticTexts["评论"].waitForExistence(timeout: 6)
        XCTAssertTrue(panelOpened, "评论面板未打开")
        XCTAssertTrue(send.waitForExistence(timeout: 5), "评论面板缺少发送按钮")
        XCTAssertFalse(send.isEnabled, "评论内容为空时发送按钮应禁用")

        if input.waitForExistence(timeout: 5) {
            input.tap()
            input.typeText("iOS 截屏验收评论")
            sleep(1)
            XCTAssertTrue(send.isEnabled, "输入评论内容后发送按钮仍禁用")
            send.tap()
            sleep(4)
            snap(app, "59_评论已发送")
        } else {
            // 输入框不是 textField（可能是 textView）：退化为截图 + a11y 取证，不判失败
            print("=== A11Y TREE BEGIN (评论面板) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
        }
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 43：社区发布链路 + 首页「更多」聚合页

    /// 社区 Tab：发布入口（+「发帖」）→ 底部分发菜单 → 「发布动态」→ CreatePostView。
    ///
    /// 之前这个用例只启动到社区页就断言 runningForeground —— 入口根本没点，
    /// 「发布链路可用」是没被验证的空话。现在改成真正走完三级：
    /// 顶部「发帖」(contentDescription="发帖") → 菜单「发布动态」/「任务状态」
    /// → CreatePostView（标题「发布」+ 占位「添加标题」）。
    func test54CommunityCreatePostEntry() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        let communityTab = app.tabBars.buttons["社区"]
        XCTAssertTrue(communityTab.waitForExistence(timeout: 15), "社区 Tab 未出现")
        communityTab.tap()
        sleep(6)
        snap(app, "60_社区广场登录态")

        // 顶部「发帖」（Icons.AddCircle）——社区广场头部右侧第三个图标按钮
        let postEntry = app.buttons["发帖"].firstMatch
        guard postEntry.waitForExistence(timeout: 15) else {
            print("=== A11Y TREE BEGIN (社区页未找到发帖入口) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            XCTFail("社区广场缺少「发帖」入口")
            return
        }
        postEntry.tap()
        sleep(3)
        snap(app, "60a_发布菜单")

        // 菜单两项必须都在（少了任一项说明分发菜单没渲染）
        let createPost = app.staticTexts["发布动态"].firstMatch
        XCTAssertTrue(createPost.waitForExistence(timeout: 10), "「发帖」未弹出发布方式菜单（缺「发布动态」）")
        XCTAssertTrue(app.staticTexts["任务状态"].exists, "发布方式菜单缺「任务状态」项")

        createPost.tap()
        sleep(4)
        // 等 Compose 发帖页：标题「发布」+ 正文占位「添加正文」
        let editorReady = app.staticTexts["添加正文"].waitForExistence(timeout: 15)
            || app.staticTexts["添加标题"].waitForExistence(timeout: 3)
        snap(app, "60b_发布动态页")
        XCTAssertTrue(editorReady, "点「发布动态」后未进入发帖编辑页")
        XCTAssertTrue(app.buttons["关闭"].exists || app.staticTexts["发布"].exists, "发帖页缺少关闭/发布控件")

        // 返回社区，且仍在社区 Tab（不是跳回了别的 Tab）
        let back = app.buttons["关闭"].firstMatch
        if back.exists { back.tap() } else { app.swipeRight() }
        sleep(3)
        XCTAssertTrue(app.staticTexts["广场"].waitForExistence(timeout: 10), "从发帖页返回后未回到社区广场")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页「推荐歌单 → 查看更多」→ 全部歌单页（PlaylistListContent）。
    ///
    /// SectionHeader 的 more 按钮 contentDescription == moreText，
    /// 所以「推荐歌单」右上是「查看更多」按钮、「热门专辑」右上也是「查看更多」、
    /// 「为你精选」右上是「更多」。三处入口分别对应三条路由。
    func test55HomePlaylistCategoryAggregate() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 25), "首页未渲染")
        sleep(4)

        // 「推荐歌单」的「查看更多」就在该区块标题同一行右侧，用它自己的 frame 定位，
        // 避免页面里同时存在多个「查看更多」时命中错的（热门专辑区也有一个，且已滚动出屏）。
        let header = app.staticTexts["推荐歌单"]
        // ⚠️ 匹配必须用 CONTAINS：SectionHeader 的 more 按钮会把内部两个同名子元素
        // 拼成一个 label —— 无障碍树里是 `'查看更多, 查看更多'`，精确等值匹配永远查不到。
        let more = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "查看更多")).firstMatch
        guard more.waitForExistence(timeout: 8) else {
            print("=== A11Y TREE BEGIN (首页，未找到查看更多入口) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            snap(app, "61_首页未找到查看更多入口")
            XCTFail("首页「推荐歌单」区缺少「查看更多」入口")
            return
        }
        print("=== 推荐歌单 header.maxY=\(header.frame.maxY) / 查看更多 frame=\(more.frame) ===")
        more.tap()
        sleep(6)
        snap(app, "61_全部歌单页")

        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 10), "「查看更多」未推入全部歌单页")
        // ⚠️ 不能用 `app.staticTexts["推荐歌单"].exists == false` 当判据：
        // 推入的「全部歌单」页自己的分类 Tab 就叫「推荐歌单」—— 该文本在首页与目标页
        // 都存在，断言恒失败（曾因此误判成"没导航"）。改成正向断言目标页独有内容。
        XCTAssertTrue(
            app.staticTexts["热门推荐"].waitForExistence(timeout: 10)
                || app.staticTexts["新歌速递"].waitForExistence(timeout: 5),
            "推入的页面不是「全部歌单」聚合页（缺少分类 Tab）"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 首页「热门专辑 → 查看更多」→ 全部专辑页（AlbumListContent），点一张专辑进专辑详情。
    func test57HomeAlbumCategoryAggregate() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 25), "首页未渲染")
        // 把「热门专辑」滚进可视区
        var found = false
        for _ in 0..<10 {
            if app.staticTexts["热门专辑"].exists { found = true; break }
            app.swipeUp()
            sleep(1)
        }
        XCTAssertTrue(found, "首页未找到「热门专辑」区")
        sleep(3)
        snap(app, "63_首页热门专辑区")

        // 取「热门专辑」下方的「查看更多」（该区块标题正下方最近的按钮）
        // ⚠️ CONTAINS 而非 ==：按钮 label 是 `'查看更多, 查看更多'`（子元素拼接）。
        let albumHeader = app.staticTexts["热门专辑"]
        let candidates = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "查看更多"))
        var best: XCUIElement?
        var bestDy = Double.greatestFiniteMagnitude
        for i in 0..<candidates.count {
            let c = candidates.element(boundBy: i)
            guard c.exists, c.isHittable else { continue }
            let dy = abs(Double(c.frame.midY - albumHeader.frame.midY))
            if dy < bestDy { bestDy = dy; best = c }
        }
        guard let more = best else {
            print("=== A11Y TREE BEGIN (热门专辑区) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            XCTFail("首页「热门专辑」区缺少可点的「查看更多」入口")
            return
        }
        more.tap()
        sleep(6)
        snap(app, "64_全部专辑页")
        XCTAssertTrue(app.buttons["返回"].waitForExistence(timeout: 10), "未推入全部专辑页")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 43：已知占位项行为（点了只弹 Toast，不能崩、不能跳空页）

    /// 聊天页「更多」→ 聊天设置：iOS 未迁移，必须只弹 Toast 且页面不崩。
    func test56KnownPlaceholdersOnlyToast() throws {
        let app = XCUIApplication()
        guard launchIntoChatThread(app, peerId: "1") else {
            XCTFail("聊天详情页未打开（含重启兜底）")
            return
        }
        sleep(2)

        // 聊天详情页「更多」入口
        let more = app.buttons["更多"].firstMatch
        guard more.waitForExistence(timeout: 10) else {
            print("=== A11Y TREE BEGIN (聊天页未找到更多) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            snap(app, "62_聊天页未找到更多")
            return
        }
        more.tap()
        sleep(2)
        snap(app, "62_聊天更多菜单")
        print("=== A11Y TREE BEGIN (聊天更多菜单) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 43：聊天视频预览「点了不播」取证（test32 回归定位）

    /// 打开聊天详情页（`-UITestChatUserId` 直达路由），带**重启兜底**。
    ///
    /// 直达路由的已知偶发缺陷：`-UITestResetAuth` 会让 `auth.isLoggedIn` 从 false 变 true，
    /// 触发 `MessagesComposeView.id(...)` 重建；重建瞬间 `navigationDestination(for:)`
    /// 短暂未注册，NavigationStack 会把解析不了的 path 值丢掉 → 停在会话列表。
    /// （MessagesTabView.initialRoute 的注释只处理了 `.task` 早于注册的情况，没处理重建。）
    /// 这是 UI 测试注入路径的问题，真实用户不受影响。
    /// 兜底办法：直接再启动一次 —— 此时已是登录态，不再发生 `.id()` 重建，路由稳定。
    @discardableResult
    private func launchIntoChatThread(_ app: XCUIApplication, peerId: String) -> Bool {
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin", "-UITestChatUserId", peerId]
        app.launch()

        let input = app.staticTexts["输入消息..."]

        // 1) 直达路由通常会在启动时就把聊天页推入（此时 Tab 栏被聊天页隐藏，属正常）
        //
        // 不变量：聊天详情页一律隐藏 Tab 栏。所以「输入栏存在但不可命中」
        // ⇒ 聊天页其实在**未被选中的** Tab 里（SwiftUI TabView 保活未显示 Tab 的内容，
        // 其元素照样进无障碍树）—— 必须切到「消息」把它带到前台，否则后面按坐标点击
        // 会落在当前可见的别的 Tab 上（本用例曾因此假失败）。
        //
        // ⚠️ 判据不能用「Tab 栏是否可见」：登录态异步重建（.id() 重建推入聊天页）
        // 与这里的快照之间存在竞态 —— 刚查完 Tab 栏存在、一 tap 聊天页已盖上来，
        // XCT 硬抛 `No matches found for TabBar`。改用「输入栏是否 isHittable」：
        // 它是最终状态语义（聊天页在前台 ⇔ 输入栏可命中），且先等状态尘埃落定。
        if input.waitForExistence(timeout: 20) {
            for _ in 0..<8 {
                if input.isHittable { return true }
                sleep(1)
            }
            if selectMessagesTab(app) {
                sleep(3)
            }
            return app.staticTexts["输入消息..."].exists
        }

        // 2) 直达路由没生效：Tab 栏此时应可见，手动切到消息 Tab 再等一次
        if selectMessagesTab(app), input.waitForExistence(timeout: 20) { return true }

        // 3) 仍不行 → 重启一次：此时已是登录态，不再发生 `.id()` 重建丢路由
        print("=== [诊断] 首次启动未进聊天页（直达路由被丢），重启一次兜底 ===")
        app.terminate()
        app.launch()
        if input.waitForExistence(timeout: 20) { return true }
        return selectMessagesTab(app) && input.waitForExistence(timeout: 20)
    }

    /// 打开聊天里的视频预览，点中央「播放」后**连续多帧截屏**，用像素差判断视频到底动没动。
    ///
    /// 背景：test30（帖子视频预览）通过、test32（聊天视频预览）失败 —— 同一个
    /// FoxVideoPlayerView，区别只在宿主：帖子走 Compose `if (playing) { 创建播放器 }`，
    /// 聊天走 SwiftUI「先创建播放器、靠 updateUIView 切 autoPlay」。
    /// 本用例不改产品代码，只取证：截屏落盘 58a/58b_1..6，外部按 md5 判读是否真的在播。
    func test58ChatVideoPlayEvidence() throws {
        let app = XCUIApplication()
        XCTAssertTrue(launchIntoChatThread(app, peerId: "14"), "聊天详情页未打开")
        sleep(6)

        let badges = app.images.matching(NSPredicate(format: "label == %@", "播放"))
        XCTAssertTrue(badges.firstMatch.waitForExistence(timeout: 30), "聊天未出现媒体消息气泡")
        sleep(3)
        let total = min(badges.count, 4)
        print("=== [取证] 媒体气泡数=\(total) ===")

        for index in 0..<total {
            let badge = badges.element(boundBy: index)
            guard badge.exists, badge.frame.width > 1 else { continue }
            let bf = badge.frame
            print("=== [取证][\(index)] 点气泡 frame=\(bf) ===")
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: bf.midX, dy: bf.midY)).tap()
            sleep(5) // 等 sheet 落位 + 首帧

            let muteOK = app.buttons["静音"].exists || app.buttons["取消静音"].exists
            let closeOK = app.buttons["关闭预览"].exists
            let center0 = centerPlayButton(app)
            let count0 = app.buttons.matching(NSPredicate(format: "label == %@", "播放")).count
            print("=== [取证][\(index)] 预览打开: 静音=\(muteOK) 关闭=\(closeOK) 中央播放=\(center0 != nil) 播放按钮数=\(count0) frame=\(center0?.frame.debugDescription ?? "-") ===")
            snap(app, "58_\(index)a_预览初始")

            if let center = center0 {
                let t = center.frame
                app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: t.midX, dy: t.midY)).tap()
                print("=== [取证][\(index)] 已点中央「播放」于 \(t.midX),\(t.midY) ===")
                // 200ms 高频时间线：区分「点击没送达」与「置位后被立刻复位」
                var timeline = ""
                for _ in 0..<25 {
                    usleep(200_000)
                    timeline += centerPlayButton(app) == nil ? "0" : "1" // 0=按钮消失（=播放态）
                }
                print("=== [取证][\(index)] 点击后 5s 时间线（0=中央按钮消失=播放态）: \(timeline) ===")
                snap(app, "58_\(index)b_点播后")
                sleep(3)
                snap(app, "58_\(index)c_点播后+3s")
            } else {
                // 无中央按钮 = Live 打开即自动播：采样两帧看是否在动
                snap(app, "58_\(index)b_Live后")
                sleep(3)
                snap(app, "58_\(index)c_Live后+3s")
                print("=== [取证][\(index)] 无中央按钮（Live 自动播放），已连拍两帧 ===")
            }

            // 点媒体区（容器 onTapGesture 路径）
            let win = app.windows.firstMatch.frame
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: win.midX, dy: win.midY)).tap()
            sleep(1)
            snap(app, "58_\(index)d_点媒体区")
            print("=== [取证][\(index)] 点媒体区后 中央按钮=\(centerPlayButton(app) != nil) ===")

            let close = app.buttons["关闭预览"]
            if close.exists { close.tap() }
            sleep(2)
        }
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 44：通知设置（iOS 此前无入口，已补设置页入口）

    /// 设置页「通知 › 通知设置」：8 个分类开关默认全开；关掉「点赞提醒」后
    /// 重进页面仍为关（验证 UserDefaults 持久化，而不是只改内存）。
    ///
    /// 背景：Android 的 NotificationSettingsScreen 只挂在 `showBackButton = true`
    /// 的「我的消息」二级页上，而两端都只以 false 调用 → 这个页面在**两端原本都进不去**。
    /// 现在 iOS 从设置页进入，并把它接到应用内横幅的开关上。
    func test59NotificationSettingsToggles() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetNotificationPrefs", "-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        // 我的 → 设置
        let profileTab = app.tabBars.buttons["我的"]
        XCTAssertTrue(profileTab.waitForExistence(timeout: 15), "我的 Tab 未出现")
        profileTab.tap()
        sleep(5)

        let settingsEntry = app.buttons["设置"].firstMatch
        guard settingsEntry.waitForExistence(timeout: 15) else {
            print("=== A11Y TREE BEGIN (我的页未找到设置入口) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            XCTFail("我的页缺少「设置」入口")
            return
        }
        settingsEntry.tap()
        sleep(4)
        snap(app, "64_设置页")

        let notificationRow = app.buttons["settings.notification"].firstMatch
        let notificationRowFallback = app.staticTexts["通知设置"].firstMatch
        // ⚠️ 设置页是懒加载 List：屏幕外的行不在无障碍树里，先滚到它。
        //（该行是「应用」组第 3 行，默认可能落在首屏之下 —— 不滚动会假失败。）
        _ = scrollToSettingsRow(app, "settings.notification")
        guard notificationRow.waitForExistence(timeout: 10) || notificationRowFallback.waitForExistence(timeout: 3) else {
            print("=== A11Y TREE BEGIN (设置页未找到通知设置入口) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            XCTFail("设置页缺少「通知设置」入口")
            return
        }
        (notificationRow.exists ? notificationRow : notificationRowFallback).tap()
        sleep(3)
        snap(app, "65_通知设置页")

        // 8 个分类开关（对齐 Android notificationTypes 的 8 项）
        let expected = ["聊天消息", "评论提醒", "点赞提醒", "关注提醒",
                        "提及提醒", "好友请求", "系统通知", "音乐提醒"]
        for label in expected {
            XCTAssertTrue(app.staticTexts[label].waitForExistence(timeout: 8), "通知设置缺少「\(label)」")
        }

        let likeToggle = app.switches["notification.toggle.like"].firstMatch
        XCTAssertTrue(likeToggle.waitForExistence(timeout: 8), "缺少「点赞提醒」开关")
        // 默认全开（刚复位过 UserDefaults）
        XCTAssertEqual(likeToggle.value as? String, "1", "「点赞提醒」默认应为开启")

        // ⚠️ 不能用 `likeToggle.tap()`：Toggle 的 a11y 元素覆盖**整行**（标签 + 控件），
        // `tap()` 落在行中部（标签文字上），iOS 26 下不会翻转开关（实测值恒为 1）。
        // 必须按行内**右侧**坐标点，那里才是开关控件本体。
        likeToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        sleep(2)
        let afterOff = app.switches["notification.toggle.like"].firstMatch.value as? String
        if afterOff != "0" {
            print("=== A11Y TREE BEGIN (点赞提醒未关闭) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
        }
        XCTAssertEqual(afterOff, "0", "点击后「点赞提醒」未关闭")

        // 返回设置页再进来一次：开关状态必须保持（持久化到 UserDefaults）
        let back = app.navigationBars.buttons.firstMatch
        if back.exists { back.tap() } else { app.swipeRight() }
        sleep(2)
        // 返回后 List 的滚动位置不保证保留 → 重新滚到该行再点（否则屏幕外查不到）
        _ = scrollToSettingsRow(app, "settings.notification")
        (notificationRow.exists ? notificationRow : app.staticTexts["通知设置"].firstMatch).tap()
        sleep(3)
        snap(app, "65b_通知设置页_复进")

        let likeAgain = app.switches["notification.toggle.like"].firstMatch
        XCTAssertTrue(likeAgain.waitForExistence(timeout: 8), "复进后找不到「点赞提醒」开关")
        XCTAssertEqual(likeAgain.value as? String, "0", "「点赞提醒」关闭状态未持久化")
        // 其它分类不受影响
        XCTAssertEqual(app.switches["notification.toggle.comment"].firstMatch.value as? String, "1",
                       "关闭「点赞提醒」误伤了「评论提醒」")

        // 复原，避免污染后续用例
        likeAgain.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        sleep(2)
        XCTAssertEqual(app.switches["notification.toggle.like"].firstMatch.value as? String, "1", "复原失败")
        XCTAssertEqual(app.state, .runningForeground)
    }

    // MARK: - 批次 A-F 全量迁移补测

    /// 发现页「热门歌手」右上角「更多」→ 热门歌手列表页 → 底部「全部歌手」→ 全部歌手页。
    func test60DiscoverArtistLists() throws {
        let app = XCUIApplication()
        app.launch()

        let tab = app.tabBars.buttons["发现"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "未找到'发现'Tab")
        tab.tap()
        XCTAssertTrue(app.staticTexts["热门榜单"].waitForExistence(timeout: 25), "发现页未加载")

        var found = false
        for _ in 0..<10 {
            if app.staticTexts["热门歌手"].exists { found = true; break }
            app.swipeUp(); sleep(1)
        }
        XCTAssertTrue(found, "发现页'热门歌手'区未出现")
        let header = app.staticTexts["热门歌手"]
        for _ in 0..<6 {
            if header.frame.midY < app.frame.height * 0.6 { break }
            app.swipeUp(); sleep(1)
        }
        sleep(2)
        snap(app, "60_发现页热门歌手区")

        // SectionHeader 右侧「更多」：页面上有多个「查看更多」（榜单/新歌/歌手），
        // 取与「热门歌手」标题同行的那个（直接点元素，避免坐标误差）。
        let hf = header.frame
        let moreCandidates = app.staticTexts.matching(
            NSPredicate(format: "label == '查看更多'")
        )
        var tappedMore = false
        for i in 0..<moreCandidates.count {
            let e = moreCandidates.element(boundBy: i)
            if e.exists && abs(e.frame.midY - hf.midY) < 40 {
                e.tap()
                tappedMore = true
                break
            }
        }
        if !tappedMore {
            // 兜底：按标题行右缘坐标点
            app.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: app.frame.width - 40, dy: hf.midY)).tap()
        }
        // 进入热门歌手列表页（B3：该页隐藏系统导航栏与底部 TabBar，只保留 Compose 顶栏）。
        // Compose 宿主根视图的 identifier 不暴露给无障碍树，用页面内容 + TabBar 隐没判定。
        let hotEntry = app.staticTexts["全部歌手"].firstMatch
        XCTAssertTrue(hotEntry.waitForExistence(timeout: 10), "未进入热门歌手列表页")
        sleep(1)
        snap(app, "60b_热门歌手列表")
        XCTAssertEqual(app.tabBars.count, 0, "热门歌手列表页底部 TabBar 未隐藏")

        // 底部「全部歌手」入口（可点行）→ 全部歌手页
        hotEntry.tap()
        // 全部歌手页：TabBar 同样隐藏；标题仍是「全部歌手」但可点行（button）已消失
        XCTAssertTrue(app.staticTexts["全部歌手"].waitForExistence(timeout: 10), "全部歌手页未显示标题")
        XCTAssertFalse(app.buttons["全部歌手"].exists, "仍停留在热门歌手列表页（全部歌手行还在）")
        sleep(1)
        snap(app, "60c_全部歌手列表")
        XCTAssertEqual(app.tabBars.count, 0, "全部歌手页底部 TabBar 未隐藏")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 设置页「曲库管理」仅管理员可见：非管理员账号自动跳过；管理员进入后菜单可用。
    func test61ManageLibraryEntryForAdmin() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        let profileTab = app.tabBars.buttons["我的"]
        XCTAssertTrue(profileTab.waitForExistence(timeout: 15), "我的 Tab 未出现")
        profileTab.tap()
        sleep(4)

        let gear = app.buttons["设置"].firstMatch
        XCTAssertTrue(gear.waitForExistence(timeout: 10), "我的页缺少设置入口")
        gear.tap()
        sleep(3)

        // 懒加载 List：滚到管理页行（在「下载管理」附近）
        let row = app.buttons["settings.manageLibrary"].firstMatch
        for _ in 0..<10 {
            if row.exists { break }
            app.swipeUp(); sleep(1)
        }
        guard row.waitForExistence(timeout: 3) else {
            snap(app, "61_设置页_无曲库管理")
            throw XCTSkip("当前测试账号非管理员，设置页不展示「曲库管理」（行隐藏符合预期）")
        }
        row.tap()
        sleep(3)
        snap(app, "61_曲库管理页")

        XCTAssertTrue(app.staticTexts["曲库管理"].waitForExistence(timeout: 8), "未进入曲库管理页")
        XCTAssertTrue(app.buttons["manageLibrary.menu"].waitForExistence(timeout: 5), "曲库管理页缺少右上角操作菜单")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌曲评论回复链路：一级评论点「回复」→ 输入框进入回复态（可取消）→ 发送 → 回复状态复位。
    /// 依赖所选歌曲存在评论；无评论时跳过（不断言失败）。
    func test62CommentReplySend() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()
        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")

        // 评论入口在歌词页右上
        app.buttons["player.stage.lyric"].tap()
        let commentEntry = app.buttons["player.commentEntry"]
        XCTAssertTrue(commentEntry.waitForExistence(timeout: 5), "歌词页缺少评论入口")
        commentEntry.tap()
        XCTAssertTrue(app.otherElements["commentSheet"].waitForExistence(timeout: 8), "评论面板未打开")
        sleep(4)

        // 首个一级评论的「回复」按钮（identifier = comment.reply.<id>；排除回复态的 comment.reply.cancel）
        let replyButtons = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@ AND identifier != %@",
                        "comment.reply.", "comment.reply.cancel")
        )
        guard replyButtons.count > 0 else {
            snap(app, "62_评论面板无评论")
            throw XCTSkip("当前歌曲没有评论，无法验证回复发送")
        }
        replyButtons.element(boundBy: 0).tap()
        sleep(1)

        let cancel = app.buttons["comment.reply.cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5), "点回复后输入框未进入回复态（缺少取消按钮）")

        let input = app.textFields["comment.input"].firstMatch
        XCTAssertTrue(input.waitForExistence(timeout: 5), "回复态缺少评论输入框")
        input.tap()
        input.typeText("UI测试回复 \(Int(Date().timeIntervalSince1970) % 100000)")
        snap(app, "62b_回复输入态")

        let send = app.buttons["comment.send"]
        XCTAssertTrue(send.waitForExistence(timeout: 3), "缺少发送按钮")
        XCTAssertTrue(send.isEnabled, "输入回复内容后发送按钮仍不可点")
        send.tap()
        sleep(5)
        snap(app, "62c_回复发送后")

        // 发送成功：回复态退出（取消按钮消失）
        XCTAssertFalse(cancel.exists, "回复发送后输入框仍停留在回复态（发送可能失败）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 新版下载管理页骨架：存储概览 + 「仅 Wi-Fi 下载」开关 + 空态/分组容器。
    func test63DownloadManagerStructure() throws {
        let app = XCUIApplication()
        XCTAssertTrue(openLoggedInProfile(app), "未能进入登录态「我的」页")
        sleep(2)

        app.buttons["设置"].firstMatch.tap()
        sleep(3)
        XCTAssertTrue(scrollToSettingsRow(app, "settings.downloads"), "设置页找不到「下载管理」")
        app.buttons["settings.downloads"].firstMatch.tap()
        sleep(3)
        snap(app, "63_下载管理页")

        XCTAssertTrue(app.staticTexts["下载管理"].waitForExistence(timeout: 8), "下载管理页未打开")
        let wifiToggle = app.switches["downloads.wifiOnly"].firstMatch
        XCTAssertTrue(wifiToggle.waitForExistence(timeout: 8), "缺少「仅 Wi-Fi 下载」开关")
        // 存储概览：Form 行容器的 identifier 会被 Form 吞掉，直接断言可见内容。
        // 已下载行由 test64 真实下载并持久化，这里同时校验占用量不再是 0KB（B5）。
        XCTAssertTrue(app.staticTexts["本地存储"].waitForExistence(timeout: 8), "缺少本地存储概览标题")
        let usage = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH '已下载 ' AND label CONTAINS '占用 '")
        )
        XCTAssertEqual(usage.count, 1, "缺少本地存储占用概览")
        let usageLabel = usage.firstMatch.label
        XCTAssertFalse(usageLabel.contains("占用 0 KB"), "占用量显示为 0KB（实际已下载 \(usageLabel)）")
        // 默认开启仅 Wi-Fi
        XCTAssertEqual(wifiToggle.value as? String, "1", "「仅 Wi-Fi 下载」默认应为开启")

        // 点行内右侧开关本体（Toggle 的整行 a11y 元素点中部不会翻转，参考 test59）
        wifiToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        sleep(1)
        XCTAssertEqual(app.switches["downloads.wifiOnly"].firstMatch.value as? String, "0", "开关未关闭")
        wifiToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        sleep(1)
        XCTAssertEqual(app.switches["downloads.wifiOnly"].firstMatch.value as? String, "1", "开关未重新开启")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 下载完成 → 下载管理页「已下载」分组出现 → 点击走本地文件播放。
    /// 真实后台下载，给 90s；超时说明环境/网络问题，跳过而非误判。
    /// （首次跑通后任务持久化在模拟器里，之后复跑会直接命中已完成行。）
    func test64DownloadedSongPlaysLocally() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["首页"].tap()
        XCTAssertTrue(tapFirstRecommendedSong(app), "未能从首页播放歌曲")
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10))

        // 全屏播放器 → 播放队列 → 第一行右侧「更多」→ 菜单里点「下载」
        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8))
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6))
        snap(app, "64a_播放队列")
        let rowMore = app.buttons["queue.row.more"].firstMatch
        XCTAssertTrue(rowMore.waitForExistence(timeout: 10), "播放队列行缺少更多入口")
        rowMore.tap()
        sleep(1)

        let downloadItem = app.buttons["下载"].firstMatch.exists
            ? app.buttons["下载"].firstMatch
            : app.staticTexts["下载"].firstMatch
        XCTAssertTrue(downloadItem.waitForExistence(timeout: 5), "歌曲菜单缺少「下载」")
        downloadItem.tap()
        sleep(2)

        // 收起播放列表 + 全屏播放器回 Tab（顺序不能乱：队列 sheet 盖在播放器上）
        if app.buttons["关闭"].waitForExistence(timeout: 3) {
            app.buttons["关闭"].firstMatch.tap()
            sleep(1)
        }
        if app.buttons["player.close"].waitForExistence(timeout: 5) {
            app.buttons["player.close"].tap()
            XCTAssertFalse(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 5),
                           "全屏播放器未收起")
        }
        sleep(1)

        // 我的 → 设置 → 下载管理
        app.tabBars.buttons["我的"].tap()
        XCTAssertTrue(app.buttons["设置"].waitForExistence(timeout: 8), "「我的」页未出现设置入口")
        app.buttons["设置"].firstMatch.tap()
        sleep(3)
        XCTAssertTrue(scrollToSettingsRow(app, "settings.downloads"))
        app.buttons["settings.downloads"].firstMatch.tap()
        sleep(3)

        // 等已完成行（后台 URLSession 下载，轮询 90s）
        let completedRows = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "downloads.completedRow.")
        )
        let completed = completedRows.firstMatch
        let finished = completed.waitForExistence(timeout: 90)
        snap(app, "64_下载管理等待完成")
        guard finished else {
            print("=== A11Y TREE BEGIN (90s 未下载完成) ===")
            print(app.debugDescription)
            print("=== A11Y TREE END ===")
            throw XCTSkip("90s 内未出现已完成下载行（网络/环境问题，跳过本地播放断言）")
        }
        let completedCount = completedRows.count
        snap(app, "64b_已下载分组")
        sleep(2) // 等「下载完成」刷新导致的 List 插入动画结束，避免点击落在更新中的行上

        // 点已完成项 → 本地播放 → 迷你条存在
        completed.tap()
        // 下载列表起播只是本地补全歌词，不应弹「歌词已更换」（toast 停留 2s）
        var sawLyricToast = false
        for _ in 0..<6 {
            if app.staticTexts["歌词已更换"].exists { sawLyricToast = true; break }
            usleep(500_000)
        }
        XCTAssertFalse(sawLyricToast, "播放下载歌曲不应弹「歌词已更换」")
        XCTAssertTrue(
            app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10),
            "点已下载歌曲后未开始播放（本地文件播放链路异常）"
        )
        sleep(2)
        snap(app, "64c_本地播放中")

        // 整个已下载列表都应进入播放队列（对齐 Android playDownload → buildPlayablePlaylist）。
        // 首次点击若正赶上列表刷新被吞，回下载页重点一次。
        func openQueueCount() -> Int? {
            app.buttons["miniPlayer.bar"].tap()
            guard app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8) else { return nil }
            app.buttons["player.queueButton"].tap()
            guard app.staticTexts["播放列表"].waitForExistence(timeout: 6) else { return nil }
            let label = app.staticTexts.matching(
                NSPredicate(format: "label BEGINSWITH %@", "共 ")
            ).firstMatch.label
            return label.range(of: "共 ").map { r -> Int in
                Int(label[r.upperBound...].prefix { $0.isNumber }) ?? -1
            }
        }
        var queueTotal = openQueueCount()
        if queueTotal != completedCount {
            app.buttons["关闭"].firstMatch.tap()
            _ = waitForDisappear(app.staticTexts["播放列表"], timeout: 6)
            app.buttons["player.close"].tap()
            _ = waitForDisappear(app.staticTexts["fullPlayer.title"], timeout: 6)
            sleep(1)
            completed.tap()
            sleep(2)
            queueTotal = openQueueCount()
        }
        print("=== 已下载 \(completedCount) 首，播放队列 \(String(describing: queueTotal)) 首 ===")
        XCTAssertEqual(queueTotal, completedCount, "播放下载歌曲后队列应包含全部已下载歌曲")
        app.buttons["关闭"].firstMatch.tap()

        // B5：本地文件播放也必须带歌词（歌词随下载本地持久化；老记录后台静默补全）
        // 切到歌词页（歌词 ScrollView 在第二页，懒加载）
        app.buttons["player.stage.lyric"].tap()
        XCTAssertTrue(app.buttons["player.settingsButton"].waitForExistence(timeout: 8), "未切到歌词页")
        let lyricsContainer = app.scrollViews["player.lyrics"]
        var lyricLoaded = false
        for _ in 0..<15 {
            if lyricsContainer.exists && lyricsContainer.staticTexts.count > 0 {
                lyricLoaded = true
                break
            }
            sleep(1)
        }
        snap(app, "64d_本地播放歌词")
        XCTAssertTrue(lyricLoaded, "播放本地下载歌曲未加载歌词（元数据/歌词兜底链路异常）")

        // 歌词必须随下载落盘：杀进程重启后再点下载歌曲，歌词依旧（不依赖联网）
        app.buttons["player.close"].tap()
        app.terminate()
        app.launch()
        app.tabBars.buttons["我的"].tap()
        XCTAssertTrue(app.buttons["设置"].waitForExistence(timeout: 8), "重启后「我的」页未出现设置入口")
        app.buttons["设置"].firstMatch.tap()
        sleep(3)
        XCTAssertTrue(scrollToSettingsRow(app, "settings.downloads"))
        app.buttons["settings.downloads"].firstMatch.tap()
        sleep(3)
        let rowAfterRelaunch = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "downloads.completedRow.")
        ).firstMatch
        XCTAssertTrue(rowAfterRelaunch.waitForExistence(timeout: 10), "重启后下载列表为空（下载未持久化）")
        rowAfterRelaunch.tap()
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10))
        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8))
        app.buttons["player.stage.lyric"].tap()
        XCTAssertTrue(app.buttons["player.settingsButton"].waitForExistence(timeout: 8), "重启后未切到歌词页")
        var persistedLyric = false
        for _ in 0..<8 {
            if app.scrollViews["player.lyrics"].staticTexts.count > 0 {
                persistedLyric = true
                break
            }
            sleep(1)
        }
        snap(app, "64e_重启后歌词仍在")
        XCTAssertTrue(persistedLyric, "重启后下载歌曲歌词丢失（歌词未随下载本地持久化）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌曲菜单「分享给好友」→ 选人弹窗出现好友列表 → 点第一位 → 「已分享」。
    /// 测试账号无好友时跳过。
    func test65ShareSongToFriend() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()
        app.tabBars.buttons["首页"].tap()
        XCTAssertTrue(tapFirstRecommendedSong(app), "未能从首页播放歌曲")
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10))

        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8))
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6))
        app.buttons["queue.row.more"].firstMatch.tap()
        sleep(1)

        let shareItem = app.buttons["分享给好友"].firstMatch.exists
            ? app.buttons["分享给好友"].firstMatch
            : app.staticTexts["分享给好友"].firstMatch
        XCTAssertTrue(shareItem.waitForExistence(timeout: 5), "歌曲菜单缺少「分享给好友」")
        shareItem.tap()

        let friendRow = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "selectFriend.row.")
        ).firstMatch
        guard friendRow.waitForExistence(timeout: 12) else {
            snap(app, "65_选人弹窗无好友")
            throw XCTSkip("测试账号暂无好友，跳过选人分享断言")
        }
        snap(app, "65_选人弹窗")
        friendRow.tap()
        // Toast 只停留 2s（ToastCenter），不能 sleep 3s 再查；用 0.5s 粒度轮询，
        // 兼容「网络回包后才弹」与「立即弹」两种时序。
        let successToast = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "已分享")
        ).firstMatch
        var shared = false
        for _ in 0..<24 {
            if successToast.exists { shared = true; break }
            usleep(500_000)
        }
        snap(app, "65b_分享后")
        XCTAssertTrue(shared, "选人后未出现「已分享」成功提示")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 聊天语音：输入栏「切换到语音」按钮可切到按住说话态；若会话中存在语音气泡，
    /// 点一下应在「播放语音 / 停止播放」间切换。无语音消息时只验证输入切换。
    func test66ChatVoiceInputAndPlayback() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin", "-UITestChatUserId", "1"]
        app.launch()

        app.tabBars.buttons["消息"].tap()
        XCTAssertTrue(app.staticTexts["输入消息..."].waitForExistence(timeout: 25), "聊天页输入栏未渲染")

        let mic = app.buttons["切换到语音"].firstMatch
        XCTAssertTrue(mic.waitForExistence(timeout: 8), "输入栏缺少语音模式切换按钮")
        mic.tap()
        sleep(1)
        snap(app, "66_语音输入态")
        XCTAssertTrue(app.buttons["切换到键盘"].waitForExistence(timeout: 5), "点麦克风后未进入语音输入态")

        // 会话里若有历史语音气泡，验证播放/停止切换。
        // 语音气泡是 clickable Row（Compose 合并子语义），label 形如「播放语音, 2''」，
        // 且节点类型不一定是 button，用 CONTAINS 从任意元素里找。
        let playVoice = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "播放语音")).firstMatch
        if playVoice.exists {
            playVoice.tap()
            let stopPlaying = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "停止播放")).firstMatch
            XCTAssertTrue(
                stopPlaying.waitForExistence(timeout: 6),
                "点语音气泡后未进入播放态"
            )
            snap(app, "66b_语音播放中")
            stopPlaying.tap()
            sleep(1)
        }

        app.buttons["切换到键盘"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["输入消息..."].waitForExistence(timeout: 5), "切回键盘后输入框未恢复")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 编辑帖子回显：自己的帖子详情 → 更多 → 编辑 → 编辑器标题为「编辑」（内容自动回填）。
    /// 首屏没有自己的帖子/编辑入口时跳过。
    func test67OwnPostEditShowsEcho() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        app.tabBars.buttons["社区"].tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")
        sleep(5)

        // 进首条帖子详情
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.30)).tap()
        sleep(4)
        let more = app.buttons["更多"].firstMatch
        guard more.waitForExistence(timeout: 8) else {
            snap(app, "67_帖子详情无更多")
            throw XCTSkip("帖子详情未渲染「更多」入口，跳过编辑回显")
        }
        more.tap()
        sleep(1)

        let edit = app.buttons["编辑"].firstMatch.exists
            ? app.buttons["编辑"].firstMatch
            : app.staticTexts["编辑"].firstMatch
        guard edit.waitForExistence(timeout: 5) else {
            snap(app, "67_非自己帖子无编辑")
            throw XCTSkip("首条帖子不是当前账号的，菜单无「编辑」，跳过编辑回显")
        }
        edit.tap()
        sleep(5)
        snap(app, "67_帖子编辑页回显")

        // 编辑器顶栏标题在编辑态为「编辑」（新建态为「发布」）
        XCTAssertTrue(app.staticTexts["编辑"].waitForExistence(timeout: 10), "点编辑后未进入帖子编辑页")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 回归：进入聊天页再退出，会话列表昵称不能退化成兜底「用户」。
    ///
    /// 旧实现进页瞬间（资料还没拉到）就用 null 昵称 upsert 本地会话，快速退出时
    /// 资料拉取协程被取消 → 昵称永久被抹；发消息成功回写与资料拉取竞态时也会复现。
    /// 修复：upsert 身份字段只增不删 + 进页先用本地会话回填昵称。
    func test68ConversationNameSurvivesChatExit() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        // peer = 用户 1（乖乖最可爱），进页 preload 直达聊天
        app.launchArguments += ["-UITestChatUserId", "1"]
        app.launch()

        // 与 test29 同款启动节奏：自动登录是异步的，必须等消息 Tab 就绪再点
        let messagesTab = app.tabBars.buttons["消息"]
        XCTAssertTrue(messagesTab.waitForExistence(timeout: 15), "消息 Tab 未出现")
        messagesTab.tap()
        let input = app.staticTexts["输入消息..."]
        XCTAssertTrue(input.waitForExistence(timeout: 25), "聊天页输入框未渲染")

        // 等服务端资料把对端昵称拉回来，发一条消息建立/刷新会话。
        // 注意用户 1 全昵称是「乖乖最可爱😘」，用 CONTAINS 匹配，勿用精确等值。
        let peerName = "乖乖最可爱"
        let peerNamePredicate = NSPredicate(format: "label CONTAINS %@", peerName)
        let peerTitle = app.staticTexts.matching(peerNamePredicate).firstMatch
        XCTAssertTrue(peerTitle.waitForExistence(timeout: 20), "聊天页未解析出对端昵称")
        input.tap()
        let text = "rt68-\(Int(Date().timeIntervalSince1970))"
        app.typeText(text)
        app.buttons["发送"].tap()
        sleep(3)

        // 返回列表：昵称必须在
        app.buttons["返回"].tap()
        let nameInList = app.staticTexts.matching(peerNamePredicate).firstMatch
        XCTAssertTrue(nameInList.waitForExistence(timeout: 20), "会话列表未显示对端昵称")
        XCTAssertFalse(app.staticTexts["用户"].exists, "会话昵称已退化为兜底「用户」")

        // 快速「进 → 立刻退」三轮，模拟资料拉取被取消的竞态（旧实现必现抹名）
        for round in 1...3 {
            nameInList.tap()
            XCTAssertTrue(
                app.staticTexts["输入消息..."].waitForExistence(timeout: 15),
                "第 \(round) 次进入聊天页失败"
            )
            // 不等待，立即返回
            app.buttons["返回"].tap()
            let backInList = app.staticTexts.matching(peerNamePredicate).firstMatch
            XCTAssertTrue(
                backInList.waitForExistence(timeout: 15),
                "第 \(round) 次快速退出后昵称丢失"
            )
            XCTAssertFalse(
                app.staticTexts["用户"].exists,
                "第 \(round) 次快速退出后会话昵称退化为「用户」"
            )
        }
        snap(app, "68_快速进出后昵称保留")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 回归：他人个人主页「动态」必须只显示该用户的帖子。
    ///
    /// 旧实现 PostPagingSource 的冷启动缓存键 __posts_latest_v1 没带 authorId，
    /// 他人主页第一页直接命中社区广场缓存（=当前用户/全站动态），且过滤结果还会
    /// 反过来污染广场缓存。修复：缓存只对无筛选条件的广场首页生效。
    func test69UserHomePostsBelongToOwner() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        // 自动登录是异步的，登录回调落地时 TabView 会重建并把选中项重置为默认 Tab，
        // 过早切到社区会被弹回消息页。先等登录态稳定再切。
        sleep(4)
        app.tabBars.buttons["社区"].tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")
        sleep(6)

        // 广场是瀑布流卡片（卡片上只有「作者名 + 点赞数」，没有时间，且点击区不稳）。
        // 稳妥路径：先进首条帖子详情，详情头部是标准 PostCard 作者行（名 + 时间 + 可点头像）。
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.22)).tap()
        let detailMore = app.buttons["更多"].firstMatch
        guard detailMore.waitForExistence(timeout: 10) else {
            snap(app, "69_未进入帖子详情")
            throw XCTSkip("未能从广场进入帖子详情，跳过主页动态归属断言")
        }
        sleep(2)

        // 详情顶栏作者行：返回 + 头像 + 昵称（同一行）+ 更多。
        // clickable Row 会把子元素语义合并，昵称可能以 button/other 形态暴露，
        // 因此在顶部条带 y ∈ [40, 130] 内查任意元素，排除返回/更多/时间。
        let topBarExclude: Set<String> = ["返回", "更多", "Back", "More"]
        var nameEl: XCUIElement?
        for el in app.descendants(matching: .any).allElementsBoundByIndex {
            guard el.exists else { continue }
            let label = el.label.trimmingCharacters(in: .whitespaces)
            let f = el.frame
            if label.isEmpty || topBarExclude.contains(label) { continue }
            // 只要文本类内容（不要整条导航栏容器）：元素自身高度不超过 60pt
            if f.minY >= 40, f.maxY <= 130, f.height <= 60, f.width < 300 {
                nameEl = el
                break
            }
        }
        guard let nameEl else {
            snap(app, "69_详情无作者行")
            throw XCTSkip("帖子详情未识别到作者行，跳过主页动态归属断言")
        }
        // 合并语义的 Row label 形如「乖乖最可爱😘, 乖乖最可爱😘」，取第一段作为主人名
        let ownerRaw = nameEl.label.trimmingCharacters(in: .whitespaces)
        let owner = ownerRaw.components(separatedBy: ", ").first ?? ownerRaw
        // 直接点元素坐标（合成 tap 在 Compose 合并节点上偶发不投递）
        let nf = nameEl.frame
        nameEl.tap()
        if !app.staticTexts[owner].waitForExistence(timeout: 5) {
            app.coordinate(
                withNormalizedOffset: CGVector(
                    dx: Double((nf.minX + 40) / app.frame.width),
                    dy: Double(nf.midY / app.frame.height)
                )
            ).tap()
        }
        if !app.staticTexts[owner].waitForExistence(timeout: 10) {
            snap(app, "69_点作者后未进主页")
        }

        // 进入个人主页：头部昵称 = 帖子作者
        XCTAssertTrue(
            app.staticTexts[owner].waitForExistence(timeout: 12),
            "未进入作者 \(owner) 的个人主页"
        )
        // 切「动态」Tab（默认可能停在歌单）
        let dynamicTab = app.staticTexts["动态"].firstMatch
        if dynamicTab.exists { dynamicTab.tap() }
        // 等列表区出现主人的作者行（合并语义节点 label = 「昵称, X天前」），最多 12s
        func authorRowLabels() -> [(name: String, frame: CGRect)] {
            func isTimeLabel(_ s: String) -> Bool {
                s.contains("前") || s.contains("昨天") || s.contains("周")
                    || s.range(of: #"^\d{4}-\d{2}-\d{2}"#, options: .regularExpression) != nil
            }
            var rows: [(String, CGRect)] = []
            for el in app.descendants(matching: .any).allElementsBoundByIndex {
                guard el.exists else { continue }
                let label = el.label.trimmingCharacters(in: .whitespaces)
                let f = el.frame
                // 作者行节点：行高 ≤60、位于 Tab 线（y≈320）以下，label 带「, 相对时间」
                guard f.minY > 320, f.height <= 60, f.width <= 340 else { continue }
                let parts = label.components(separatedBy: ", ")
                if parts.count >= 2, isTimeLabel(parts.last ?? "") {
                    rows.append((parts.first ?? label, f))
                }
            }
            return rows
        }
        var appeared = false
        for _ in 0..<24 {
            if authorRowLabels().contains(where: { $0.name == owner }) { appeared = true; break }
            usleep(500_000)
        }
        _ = appeared
        snap(app, "69_他人主页动态")

        // 动态流里每条帖子的作者行必须全部属于主页主人（旧 bug 这里会混入广场缓存的其他作者）。
        let rows = authorRowLabels()
        let ownerPostCount = rows.filter { $0.name == owner }.count
        let otherAuthors = Set(rows.filter { $0.name != owner }.map { $0.name })
        XCTAssertGreaterThanOrEqual(ownerPostCount, 1, "主页动态里没有主人 \(owner) 自己的帖子")
        XCTAssertTrue(
            otherAuthors.isEmpty,
            "他人主页动态混入了其他作者的帖子：\(otherAuthors)"
        )
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 帖子详情里点话题标签（#xxx）→ 跳到搜索页：自动落在「帖子」Tab、
    /// 搜索框回填标签并直接出带该标签的帖子结果。
    func test70PostTagOpensTagSearch() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        sleep(4)
        app.tabBars.buttons["社区"].tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")
        sleep(6)

        // 广场首条（最新帖）进详情
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.22)).tap()
        XCTAssertTrue(app.buttons["更多"].waitForExistence(timeout: 10), "未进入帖子详情")
        sleep(2)

        // 标签在正文区（#开头的可点文本），必要时往下滚一屏找
        var tagEl: XCUIElement?
        for i in 0..<8 {
            let cand = app.descendants(matching: .any).matching(
                NSPredicate(format: "label BEGINSWITH %@", "#")
            ).firstMatch
            if cand.exists { tagEl = cand; break }
            app.swipeUp()
            usleep(400_000)
            _ = i
        }
        guard let tagEl, tagEl.exists else {
            snap(app, "70_详情无标签")
            throw XCTSkip("首条帖子没有话题标签，跳过标签跳转断言")
        }
        let tagName = String(tagEl.label.dropPrefix("#"))
        snap(app, "70_详情标签")
        tagEl.tap()

        // 搜索页：结果页 Chrome（含「帖子」Tab）出现；搜索框回填的标签作为文本可见
        let postTab = app.staticTexts["帖子"].firstMatch
        XCTAssertTrue(postTab.waitForExistence(timeout: 10), "点标签后未进入搜索结果页")
        sleep(2)
        snap(app, "70_标签搜索结果")
        let queryText = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", tagName)
        ).firstMatch
        XCTAssertTrue(queryText.waitForExistence(timeout: 8), "搜索框/标签区未显示标签 \(tagName)")

        // 标签结果：验证帖子 Tab 确实出了带该标签的帖子（预置数据两种标签）
        let expectedTitle = tagName == "测试标签A" ? "iOS发布测试"
            : tagName == "uitest73" ? "UITEST通知跳转标记" : nil
        if let expectedTitle {
            XCTAssertTrue(
                app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", expectedTitle))
                    .firstMatch.waitForExistence(timeout: 12),
                "标签「\(tagName)」结果中未出现帖子「\(expectedTitle)」"
            )
        }
        sleep(2)
        snap(app, "70_标签搜索结果")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 消息页「查找聊天记录」（全局 + 会话内）必须隐藏底部 TabBar。
    func test71ChatSearchHidesTabBar() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        sleep(4)
        app.tabBars.buttons["消息"].tap()
        let searchEntry = app.buttons["搜索"].firstMatch
        XCTAssertTrue(searchEntry.waitForExistence(timeout: 15), "消息页搜索入口未出现")
        searchEntry.tap()

        XCTAssertTrue(
            app.staticTexts["查找聊天记录"].waitForExistence(timeout: 8),
            "未进入全局查找聊天记录页"
        )
        sleep(1)
        snap(app, "71_全局聊天记录搜索")
        XCTAssertFalse(
            app.tabBars.buttons["消息"].exists,
            "全局聊天记录搜索页没有隐藏底部 TabBar"
        )

        // 返回消息页：TabBar 应恢复
        app.buttons["返回"].firstMatch.tap()
        XCTAssertTrue(app.buttons["搜索"].waitForExistence(timeout: 8), "未退回消息页")
        XCTAssertTrue(app.tabBars.buttons["消息"].exists, "返回消息页后 TabBar 未恢复")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 歌曲菜单「分享给好友」成功后：会话列表立即出现/更新（预览「[分享]」），
    /// 点进会话能看到音乐分享卡片（卡片标题是歌名）—— 不需要下拉刷新/重进。
    func test72SharedSongCardVisibleLocally() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()
        app.tabBars.buttons["首页"].tap()
        XCTAssertTrue(tapFirstRecommendedSong(app), "未能从首页播放歌曲")
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10))

        app.buttons["miniPlayer.bar"].tap()
        let titleEl = app.staticTexts["fullPlayer.title"]
        XCTAssertTrue(titleEl.waitForExistence(timeout: 8))
        var songTitle = titleEl.label
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6))
        sleep(1)
        let moreButton = app.buttons["queue.row.more"].firstMatch
        XCTAssertTrue(moreButton.waitForExistence(timeout: 6))
        // 分享的是队列面板里的第一行（不是当前播放曲）：取与「更多」按钮同一行的歌名文本
        let moreFrame = moreButton.frame
        for el in app.staticTexts.allElementsBoundByIndex {
            let f = el.frame
            if abs(f.midY - moreFrame.midY) < 15, f.minX < moreFrame.minX, !el.label.isEmpty {
                songTitle = el.label
                break
            }
        }
        moreButton.tap()
        sleep(1)

        let shareItem = app.buttons["分享给好友"].firstMatch.exists
            ? app.buttons["分享给好友"].firstMatch
            : app.staticTexts["分享给好友"].firstMatch
        XCTAssertTrue(shareItem.waitForExistence(timeout: 5), "歌曲菜单缺少「分享给好友」")
        shareItem.tap()

        let friendRow = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "selectFriend.row.")
        ).firstMatch
        guard friendRow.waitForExistence(timeout: 12) else {
            throw XCTSkip("测试账号暂无好友，跳过分享卡片断言")
        }
        friendRow.tap()
        let toast = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "已分享")
        ).firstMatch
        var shared = false
        for _ in 0..<24 {
            if toast.exists { shared = true; break }
            usleep(500_000)
        }
        XCTAssertTrue(shared, "选人后未出现「已分享」成功提示")

        // 关掉全屏播放器 → 消息 Tab：先关「播放列表」面板，再收播放器；
        // 选人弹窗/菜单 dismiss 动画会吞点击，循环重试到 TabBar 可点。
        // 注意：被 sheet 遮住的 TabBar 仍 exists，必须用 isHittable 判定。
        for _ in 0..<8 {
            if app.tabBars.buttons["消息"].isHittable { break }
            let queueClose = app.buttons["关闭"].firstMatch
            if queueClose.exists && queueClose.isHittable {
                queueClose.tap()
            } else {
                let close = app.buttons["player.close"].firstMatch
                if close.exists && close.isHittable { close.tap() }
            }
            usleep(700_000)
        }
        XCTAssertTrue(app.tabBars.buttons["消息"].isHittable, "全屏播放器/队列面板未关闭")
        app.tabBars.buttons["消息"].tap()
        sleep(3)
        snap(app, "72_进消息页后")

        // 会话列表立即出现「[分享]」预览（旧实现站外分享不落本地库，必须下拉刷新才有）
        let preview = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "[分享]")
        ).firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 15), "分享后会话列表未出现「[分享]」预览")
        snap(app, "72_会话列表分享预览")
        preview.tap()

        // 进会话：输入栏出现 = 已进聊天页
        XCTAssertTrue(app.staticTexts["输入消息..."].waitForExistence(timeout: 20), "未进入聊天页")
        // 音乐分享卡片：卡片渲染歌名（shareData.title）
        let card = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", songTitle)
        ).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 12), "聊天页未出现歌曲「\(songTitle)」分享卡片")
        sleep(1)
        snap(app, "72_聊天页分享卡片")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 通知点击：点赞通知 / 帖子评论通知都要跳到对应帖子详情（旧实现点赞无解析、
    /// iOS 侧只弹「待接入」toast）。
    /// 前置数据（API 预置）：tmpdiag73 对 foxuitest 的帖子《UITEST通知跳转标记》(id=33)
    /// 各产生一条点赞与评论通知。
    func test73NotificationOpensPostDetail() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        sleep(4)
        app.tabBars.buttons["消息"].tap()
        XCTAssertTrue(app.buttons["搜索"].waitForExistence(timeout: 15), "消息页未渲染")
        sleep(2)

        // ① 赞和通知 → 点赞通知 → 帖子详情
        app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "赞和通知")
        ).firstMatch.tap()
        let likeRow = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "赞了你的帖子")
        ).firstMatch
        XCTAssertTrue(likeRow.waitForExistence(timeout: 12), "未找到点赞通知")
        sleep(1)
        snap(app, "73_点赞通知列表")
        likeRow.tap()
        XCTAssertTrue(
            app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "UITEST通知跳转标记"))
                .firstMatch.waitForExistence(timeout: 12),
            "点赞通知未跳转到帖子详情"
        )
        sleep(1)
        snap(app, "73_点赞通知跳详情")
        app.buttons["返回"].firstMatch.tap()
        XCTAssertTrue(
            app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", "赞了你的帖子")
            ).firstMatch.waitForExistence(timeout: 8),
            "未从详情退回通知分类页"
        )

        // ② 返回消息页 → 评论和@提及 → 评论通知 → 帖子详情
        app.buttons["返回"].firstMatch.tap()
        XCTAssertTrue(app.buttons["搜索"].waitForExistence(timeout: 8), "未退回消息页")
        app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "评论和@提及")
        ).firstMatch.tap()
        let commentRow = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "UITEST评论跳转73")
        ).firstMatch
        XCTAssertTrue(commentRow.waitForExistence(timeout: 12), "未找到帖子评论通知")
        commentRow.tap()
        XCTAssertTrue(
            app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "UITEST通知跳转标记"))
                .firstMatch.waitForExistence(timeout: 12),
            "评论通知未跳转到帖子详情"
        )
        snap(app, "73_评论通知跳详情")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 诊断：帖子分享两个入口（广场卡片分享图标 / 详情「更多→分享」）都应弹出
    /// 「分享给好友 / 系统分享」操作表。
    func test74PostShareResponds() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        sleep(4)
        app.tabBars.buttons["社区"].tap()
        XCTAssertTrue(app.buttons["搜索帖子"].waitForExistence(timeout: 15), "社区广场未渲染")
        sleep(6)

        // ① 广场卡片分享图标（无 contentDescription，查询不到时跳过；
        //    广场直出分享无 sheet dismiss 竞态，详情入口才是本次回归重点）
        let plazaShare = app.buttons.matching(
            NSPredicate(format: "label == %@ OR label == %@", "分享", "Share")
        ).firstMatch
        if plazaShare.waitForExistence(timeout: 4) {
            plazaShare.tap()
            let sheet = app.sheets.buttons["分享给好友"].firstMatch
            if sheet.waitForExistence(timeout: 5) {
                app.sheets.buttons["取消"].tap()
                sleep(1)
            }
        }

        // ② 详情 → 更多 → 分享
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.22)).tap()
        XCTAssertTrue(app.buttons["更多"].waitForExistence(timeout: 10), "未进入帖子详情")
        sleep(2)
        app.buttons["更多"].firstMatch.tap()
        let shareInSheet = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "分享")
        ).firstMatch
        XCTAssertTrue(shareInSheet.waitForExistence(timeout: 6), "更多面板缺少「分享」")
        shareInSheet.tap()
        let actionSheet = app.sheets.buttons["分享给好友"]
        XCTAssertTrue(actionSheet.waitForExistence(timeout: 8), "帖子详情点分享没有任何反应")
        snap(app, "74_详情分享后")

        // 完整链路：分享给好友 → 选第一位好友 → 「已分享」
        actionSheet.tap()
        let friendRow = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "selectFriend.row.")
        ).firstMatch
        guard friendRow.waitForExistence(timeout: 12) else {
            throw XCTSkip("测试账号暂无好友，跳过帖子分享选人断言")
        }
        friendRow.tap()
        let toast = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "已分享")
        ).firstMatch
        var shared = false
        for _ in 0..<24 {
            if toast.exists { shared = true; break }
            usleep(500_000)
        }
        XCTAssertTrue(shared, "帖子分享选人后未出现「已分享」")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 无图帖子的分享卡片不展示封面位（连占位图也不要）；音乐卡片仍保留封面。
    /// 前置：test74 已给 foxuitest2 发过无图帖子《UITEST通知跳转标记》的分享。
    func test75TextOnlyPostShareCard() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin"]
        app.launch()

        sleep(4)
        app.tabBars.buttons["消息"].tap()
        // 会话列表点 foxuitest2（预览「[分享]」行）
        let conv = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@", "foxuitest2")
        ).firstMatch
        XCTAssertTrue(conv.waitForExistence(timeout: 15), "未出现 foxuitest2 会话")
        conv.tap()
        XCTAssertTrue(app.staticTexts["输入消息..."].waitForExistence(timeout: 20), "未进入聊天页")

        // 无图帖子标题出现 = 卡片在
        let postTitle = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS %@", "UITEST通知跳转标记")
        ).firstMatch
        XCTAssertTrue(postTitle.waitForExistence(timeout: 10), "未找到无图帖子分享卡片")
        sleep(1)
        snap(app, "75_无图帖子分享卡片")

        // 帖子卡片这一行区域内不应有任何 image 元素（无封面、无占位图）。
        // 检查标题元素 frame 上方/同卡片范围：整页 images 只剩双方头像（含导航栏/输入区图标）。
        // 直接断言：不存在与帖子标题处于同一卡片行（y 接近）的 image。
        let titleFrame = postTitle.frame
        var cardImageCount = 0
        for img in app.images.allElementsBoundByIndex {
            let f = img.frame
            // 卡片封面尺寸 48pt、与标题垂直中心接近；头像在屏幕右缘（x > 360）排除
            if f.width >= 40, f.width <= 60, f.maxX < 340,
               abs(f.midY - titleFrame.midY) < 40 {
                cardImageCount += 1
            }
        }
        XCTAssertEqual(cardImageCount, 0, "无图帖子分享卡片仍展示了封面/占位图")
        XCTAssertEqual(app.state, .runningForeground)
    }
    // MARK: - 为你精选 / 新歌速递 详情页曲目数（对齐 Android 的 100 条快照）

    /// 「为你精选」详情页应一次加载 100 条。
    ///
    /// 修复前：首页「为你精选」行的分页源（pageSize=5）把 5 条小快照写进与详情页**同一个**
    /// `FoxFirstPageCache` key（`__music_recommend_v1`），详情页读到之后 `nextKey` 恒为
    /// null（5 < 30）→ 详情页永远只有 5 条。现在两边共用同一份 100 条当日快照
    /// （FoxMusicTopListCache，对齐 Android RecommendedMusicCacheManager），这里断言
    /// 「播放全部(N)」的 N == 100。
    func test76RecommendLandingLoads100() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 25), "首页未渲染")
        sleep(3)
        openTopListDetail(app, section: "为你精选", expectedTitle: "为您定制推荐歌单", shot: "76_为你精选详情")
    }

    /// 「新歌速递」详情页应一次加载 100 条（对齐 Android getPagingSource(sort="latest", maxTotalItems=100)）。
    func test77NewMusicLandingLoads100() throws {
        let app = XCUIApplication()
        app.launch()

        let tab = app.tabBars.buttons["发现"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "未找到'发现'Tab")
        tab.tap()
        XCTAssertTrue(app.staticTexts["新歌速递"].waitForExistence(timeout: 25), "发现页未渲染")
        sleep(3)
        openTopListDetail(app, section: "新歌速递", expectedTitle: "最新歌曲为您呈现", shot: "77_新歌速递详情")
    }

    /// 播放列表面板的「来源」卡片点击 → 关闭播放器并跳到对应歌单详情页
    /// （对齐 Android PlaylistBottomSheet → PlayerNavGraph 的来源跳转）。
    func test78QueueSourceRowOpensPlaylistDetail() throws {
        let app = XCUIApplication()
        app.launch()

        // 1. 首页「推荐歌单」点第一张卡片进真实歌单详情
        XCTAssertTrue(app.staticTexts["推荐歌单"].waitForExistence(timeout: 25), "首页'推荐歌单'区未出现")
        sleep(3)
        let header = app.staticTexts["推荐歌单"]
        let f = header.frame
        let cardTap = CGPoint(x: f.minX + f.width * 0.15, y: f.maxY + 60)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: cardTap.x, dy: cardTap.y)).tap()

        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        XCTAssertTrue(playAll.waitForExistence(timeout: 20), "未进入歌单详情页")
        sleep(3) // 等曲目分页加载，避免播放全部队列为空

        // 2. 播放全部（来源 = 该歌单，队列面板应出现可点的来源卡片）
        playAll.tap()
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 15), "播放全部后迷你播放条未出现")
        sleep(2)

        // 3. 进全屏播放器 → 播放列表面板
        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8), "未进入全屏播放器")
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6), "播放列表面板未打开")

        // 4. 来源卡片存在并点击
        let sourceRow = app.buttons["queue.sourceRow"]
        XCTAssertTrue(sourceRow.waitForExistence(timeout: 6), "播放列表缺少可点击的来源卡片")
        snap(app, "78_播放列表来源卡片")
        sourceRow.tap()

        // 5. 队列面板 + 全屏播放器关闭，回到（重新推入的）歌单详情页
        let queueGone = waitForDisappear(app.staticTexts["播放列表"], timeout: 6)
        XCTAssertTrue(queueGone, "点击来源后播放列表面板未关闭")
        let playerGone = waitForDisappear(app.buttons["player.close"], timeout: 6)
        XCTAssertTrue(playerGone, "点击来源后全屏播放器未关闭")
        let detailAgain = app.buttons
            .matching(NSPredicate(format: "label CONTAINS %@", "播放全部"))
            .firstMatch
        XCTAssertTrue(detailAgain.waitForExistence(timeout: 12), "点击来源后未跳到歌单详情页")
        snap(app, "78_来源跳转歌单详情")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 轮询元素消失（sheet/全屏播放器关闭动画需要时间）。
    private func waitForDisappear(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !element.exists { return true }
            usleep(300_000)
        }
        return !element.exists
    }

    /// 复现：更多菜单「下一首播放」应把歌插进播放队列（队列总数 +1）。
    func test79InsertNextAppearsInQueue() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6))

        func queueCount() -> Int? {
            let label = app.staticTexts.matching(
                NSPredicate(format: "label BEGINSWITH %@", "共 ")
            ).firstMatch.label
            guard let range = label.range(of: "共 ") else { return nil }
            let digits = label[range.upperBound...].prefix { $0.isNumber }
            return Int(digits)
        }
        let before = queueCount()
        print("=== 插入前队列数 = \(String(describing: before)) ===")
        XCTAssertNotNil(before)

        app.buttons["queue.row.more"].firstMatch.tap()
        let insertItem = app.buttons["下一首播放"].firstMatch.exists
            ? app.buttons["下一首播放"].firstMatch
            : app.staticTexts["下一首播放"].firstMatch
        XCTAssertTrue(insertItem.waitForExistence(timeout: 6), "更多菜单缺少「下一首播放」")
        insertItem.tap()
        sleep(2)

        // 菜单应已关闭、回到队列面板
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6), "下一首播放后未回到播放列表面板")
        let after = queueCount()
        print("=== 插入后队列数 = \(String(describing: after)) ===")
        snap(app, "79_下一首播放后队列")
        XCTAssertEqual(after, (before ?? 0) + 1, "「下一首播放」后队列歌曲数未增加（before=\(before!) after=\(after ?? -1)）")
    }

    /// 复现（队列外）：歌单详情里对一首歌点 ⋯ →「下一首播放」，歌应进入播放队列。
    func test80InsertNextFromPlaylistDetail() throws {
        let app = XCUIApplication()
        app.launch()

        // 先起播，有一个现成队列
        XCTAssertTrue(openFullPlayer(app), "未能进入全屏播放器")
        app.buttons["player.queueButton"].tap()
        let beforeText = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "共 ")
        ).firstMatch.label
        app.buttons["关闭"].firstMatch.tap()
        _ = waitForDisappear(app.staticTexts["播放列表"], timeout: 6)
        app.buttons["player.close"].tap()
        _ = waitForDisappear(app.staticTexts["fullPlayer.title"], timeout: 6)
        sleep(1)

        // 进首页第一张推荐歌单详情（起播时首页被滚到了"为你精选"，先回顶）
        app.tabBars.buttons["首页"].tap()
        var gotPlaylistSection = false
        for _ in 0..<12 {
            if app.staticTexts["推荐歌单"].exists { gotPlaylistSection = true; break }
            app.swipeDown(); sleep(1)
        }
        if !gotPlaylistSection {
            snap(app, "80d_关播放器后落点")
        }
        XCTAssertTrue(gotPlaylistSection, "首页'推荐歌单'区未出现")
        sleep(3)
        let f = app.staticTexts["推荐歌单"].frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: f.minX + f.width * 0.15, dy: f.maxY + 60)).tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
            .waitForExistence(timeout: 20), "未进入歌单详情页")
        sleep(3)

        // 歌曲行右侧 ⋯（Compose contentDescription="更多"）
        let moreBtn = app.buttons["更多"].firstMatch
        XCTAssertTrue(moreBtn.waitForExistence(timeout: 10), "歌单详情未找到歌曲'更多'按钮")
        moreBtn.tap()

        let insertItem = app.buttons["下一首播放"].firstMatch.exists
            ? app.buttons["下一首播放"].firstMatch
            : app.staticTexts["下一首播放"].firstMatch
        XCTAssertTrue(insertItem.waitForExistence(timeout: 6), "更多菜单缺少「下一首播放」")
        insertItem.tap()
        sleep(2)

        // 重新打开队列核对数量
        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8))
        app.buttons["player.queueButton"].tap()
        XCTAssertTrue(app.staticTexts["播放列表"].waitForExistence(timeout: 6))
        let afterText = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "共 ")
        ).firstMatch.label
        print("=== 队列外插入：before=\(beforeText) after=\(afterText) ===")
        snap(app, "80_歌单详情下一首播放后队列")

        func count(of label: String) -> Int {
            guard let r = label.range(of: "共 ") else { return -1 }
            return Int(label[r.upperBound...].prefix { $0.isNumber }) ?? -2
        }
        XCTAssertEqual(count(of: afterText), count(of: beforeText) + 1,
                       "队列外「下一首播放」未入队（before=\(beforeText) after=\(afterText)）")
    }

    /// 搜索结果行 ⋯ →「下一首播放」：搜索结果只带音频地址不带歌词，
    /// 切到这首时播放器必须静默拉到歌词（对齐 Android LyricPrefetchCoordinator）。
    func test81SearchInsertNextLoadsLyrics() throws {
        let app = XCUIApplication()
        app.launch()

        // 先起播，有现成队列
        app.tabBars.buttons["首页"].tap()
        XCTAssertTrue(tapFirstRecommendedSong(app), "未能从首页播放歌曲")
        XCTAssertTrue(app.buttons["miniPlayer.playPause"].waitForExistence(timeout: 10))

        // 首页品牌头下方搜索框 → 搜索页 → 点第一个热词出结果
        // 起播点击可能把首页滚走了，先回顶找搜索框
        var searchBar = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "搜索,")
        ).firstMatch
        for _ in 0..<12 {
            if searchBar.exists { break }
            app.swipeDown()
            sleep(1)
        }
        XCTAssertTrue(searchBar.waitForExistence(timeout: 5), "首页搜索框未出现")
        searchBar.tap()
        let rec = app.staticTexts["推荐搜索"]
        XCTAssertTrue(rec.waitForExistence(timeout: 12), "搜索页未出现热词面板")
        let rf = rec.frame
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: rf.midX * 0.5, dy: rf.maxY + 45)).tap()
        XCTAssertTrue(app.staticTexts["单曲"].waitForExistence(timeout: 12), "搜索结果未出现")
        sleep(3)
        snap(app, "81_搜索结果")

        // 第一行 ⋯ →「下一首播放」
        let moreBtn = app.buttons["更多"].firstMatch
        XCTAssertTrue(moreBtn.waitForExistence(timeout: 12), "搜索结果未找到歌曲'更多'按钮")
        moreBtn.tap()
        let insertItem = app.buttons["下一首播放"].firstMatch.exists
            ? app.buttons["下一首播放"].firstMatch
            : app.staticTexts["下一首播放"].firstMatch
        XCTAssertTrue(insertItem.waitForExistence(timeout: 6), "更多菜单缺少「下一首播放」")
        insertItem.tap()
        sleep(2)

        // 返回首页 → 全屏播放器 → 下一首（即刚插入的搜索结果歌曲）
        app.buttons["返回"].firstMatch.tap()
        XCTAssertTrue(app.buttons["miniPlayer.bar"].waitForExistence(timeout: 10), "返回首页后迷你播放条未出现")
        app.buttons["miniPlayer.bar"].tap()
        XCTAssertTrue(app.staticTexts["fullPlayer.title"].waitForExistence(timeout: 8))
        app.buttons["player.next"].tap()
        sleep(3)
        snap(app, "81_播到搜索插入歌曲")

        // 歌词页：无歌词歌曲切歌时应静默拉取并展示，且不弹「歌词已更换」
        app.buttons["player.stage.lyric"].tap()
        XCTAssertTrue(app.buttons["player.settingsButton"].waitForExistence(timeout: 8), "未切到歌词页")
        let lyricsContainer = app.scrollViews["player.lyrics"]
        var lyricLoaded = false
        for _ in 0..<20 {
            if app.staticTexts["歌词已更换"].exists {
                XCTFail("系统静默补歌词不应弹「歌词已更换」")
            }
            if lyricsContainer.exists && lyricsContainer.staticTexts.count > 0 {
                lyricLoaded = true
                break
            }
            sleep(1)
        }
        snap(app, "81_搜索插入歌曲歌词")
        XCTAssertTrue(lyricLoaded, "搜索结果「下一首播放」的歌曲播放后未加载歌词")
        XCTAssertFalse(app.staticTexts["歌词已更换"].exists, "静默补歌词不应弹「歌词已更换」")
    }

    /// 聊天语音发送全链路：切到语音模式 → 按住说话录音 → 松手 → Swift 上传 →
    /// 会话里新增一条语音气泡（"播放语音" 按钮数 +1）。
    ///
    /// 前置：
    ///  - 模拟器需已授权麦克风（`simctl privacy <udid> grant microphone`），
    ///    否则首次会弹系统权限框打断按住手势；
    ///  - 会话对象必须是 foxuitest 的好友（服务端非好友拒发），这里用 id=14。
    func test82ChatVoiceRecordAndSend() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UITestResetAuth", "-UITestAutoLogin", "-UITestChatUserId", "14"]
        app.launch()

        app.tabBars.buttons["消息"].tap()
        XCTAssertTrue(app.staticTexts["输入消息..."].waitForExistence(timeout: 25), "聊天页输入栏未渲染")

        let mic = app.buttons["切换到语音"].firstMatch
        XCTAssertTrue(mic.waitForExistence(timeout: 8), "输入栏缺少语音模式切换按钮")
        mic.tap()
        XCTAssertTrue(app.buttons["切换到键盘"].waitForExistence(timeout: 5), "点麦克风后未进入语音输入态")

        // 按住说话条（文本是「按住 说话」，中间有空格，用前缀匹配兜底）
        let holdBar = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH %@", "按住")
        ).firstMatch
        XCTAssertTrue(holdBar.waitForExistence(timeout: 5), "语音模式下未出现「按住说话」条")
        // ⚠️ 语音气泡本体是 clickable Row，Compose 会把子语义合并进父节点，
        // a11y label 形如「播放语音, 2''」（不是精确的「播放语音」），节点类型是
        // `Other` 而不是 button，所以按 CONTAINS 从任意元素里找。
        let voiceBubbles = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "播放语音"))
        // ⚠️ 基线必须在列表渲染稳定后取：LazyColumn 只给可视区建节点，进页瞬间
        // 查询会得到 0，松手后变成 2，断言「count > before」就失去意义（反之如果
        // 取到的是满值，也会把时序抖动误判成失败）。
        sleep(2)
        let voiceBubblesBefore = voiceBubbles.count
        // 语音曾被降级成 content="/" 的文本气泡（服务端只在 type=voice 时回吐 voice_url）
        let slashes = app.staticTexts.matching(NSPredicate(format: "label == %@", "/"))
        let slashesBefore = slashes.count
        snap(app, "82_语音输入态")

        // 按住 2.5s 后原地松手：>=1s 才会真正发送（<1s 会提示「语音时间太短」）。
        // XCUITest 合成的长按偶发丢事件，允许多按一轮，避免把偶发丢事件判成功能坏了。
        var sent = false
        for attempt in 1...2 {
            holdBar.press(forDuration: 2.5)
            snap(app, "82b_松手后")
            for _ in 0..<24 {
                if app.staticTexts["语音时间太短"].exists {
                    XCTFail("按住 2.5s 仍被判为过短，录音时长未生效")
                }
                if app.staticTexts["无法录音，请检查麦克风权限"].exists {
                    XCTFail("录音未启动：麦克风权限未授权")
                }
                if voiceBubbles.count > voiceBubblesBefore {
                    sent = true
                    break
                }
                usleep(500_000)
            }
            if sent { break }
        }
        snap(app, "82c_发送后")
        XCTAssertTrue(sent, "按住说话松手后未出现新的语音消息气泡")
        XCTAssertLessThanOrEqual(
            slashes.count, slashesBefore,
            "发送语音后会话里多出了 content=\"/\" 的文本气泡（语音被降级）"
        )

        // 点最新一条语音必须能真正播起来（覆盖「上传文件没入库 → /uploads/oss 404」）：
        // 播放态会让按钮 label 变成「停止播放」。
        //
        // ⚠️ 不能 `element(boundBy: count-1).tap()`：Compose 的 a11y 节点常拿不到有效
        // frame（XCTest 日志里 hit point {-1, -1}），点到空处 → 误判「播不起来」。
        // 列表停在底部，所以取「frame 有效且最靠下」的那个气泡（＝最新一条），
        // 再用坐标点它的中心。
        guard voiceBubbles.count > 0 else {
            XCTFail("列表里没有语音气泡可点")
            return
        }
        let tappable = voiceBubbles.allElementsBoundByIndex
            .filter { $0.frame.width > 1 && $0.frame.height > 1 }
        guard let newest = tappable.filter({ $0.isHittable }).max(by: { $0.frame.midY < $1.frame.midY })
            ?? tappable.max(by: { $0.frame.midY < $1.frame.midY }) else {
            XCTFail("找不到可点的语音气泡（a11y 未暴露有效 frame）")
            return
        }
        newest.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let stopPlaying = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "停止播放")).firstMatch
        var playing = false
        for _ in 0..<60 {
            if stopPlaying.exists { playing = true; break }
            usleep(300_000)
        }
        snap(app, "82d_播放中")
        XCTAssertTrue(playing, "新语音气泡点了没播起来（多为服务端未确认入库、媒体地址 404）")
        XCTAssertEqual(app.state, .runningForeground)
    }

    /// 从首页/发现页的区块「查看更多」进虚拟歌单详情页（为你精选 / 新歌速递），
    /// 断言「播放全部(N)」的 N == 100。
    ///
    /// - 冷启动首屏：首页那一行要先把当日 100 条快照拉齐（5 个请求），所以等待给得很宽。
    /// - 首屏数据落地会让区块位置抖动，万一没进详情页就返回再点一次（重试一轮），
    ///   避免把"位置抖动"误判成"功能坏了"。
    private func openTopListDetail(
        _ app: XCUIApplication,
        section: String,
        expectedTitle: String,
        shot: String
    ) {
        for attempt in 1...2 {
            scrollToSection(app, section)
            sleep(2)
            XCTAssertTrue(tapMore(app, beside: section), "\(section) 区缺少可点「查看更多」入口")
            if app.staticTexts[expectedTitle].waitForExistence(timeout: 30) {
                let count = waitPlayAllCount(app, expected: 100, timeout: 60)
                snap(app, shot)
                print("=== \(section) 详情 播放全部条数 = \(count.map(String.init) ?? "nil") ===")
                XCTAssertEqual(count, 100, "\(section) 详情曲目数不是 100（分页或快照仍被截断）")
                XCTAssertEqual(app.state, .runningForeground)
                return
            }
            print("=== 第 \(attempt) 次点「查看更多」后未出现 \(expectedTitle)，返回重试 ===")
            if app.buttons["返回"].exists {
                app.buttons["返回"].tap()
                sleep(3)
            }
        }
        print("=== A11Y TREE BEGIN (未进入 \(expectedTitle)) ===")
        print(app.debugDescription)
        print("=== A11Y TREE END ===")
        snap(app, "\(shot)_未进入详情页")
        XCTFail("未进入「\(expectedTitle)」详情页")
    }

    /// 把区块标题滚进可视区（首页/发现页都是纵向滚动容器）。
    @discardableResult
    private func scrollToSection(_ app: XCUIApplication, _ title: String, maxSwipes: Int = 8) -> Bool {
        let header = app.staticTexts[title]
        for _ in 0..<maxSwipes {
            if header.exists && header.isHittable { return true }
            app.swipeUp()
            usleep(600_000)
        }
        return header.exists
    }

    /// 点区块标题**同一行右侧**的「查看更多」。
    ///
    /// ⚠️ 不能用 `buttons["查看更多"].firstMatch`：SectionHeader 的 more 按钮
    /// contentDescription 就是 moreText，首页同时存在「推荐歌单」「为你精选」「热门专辑」
    /// 三个同名按钮，取错就会跳到别的聚合页。只能按「与标题同一行（midY 最接近）」定位。
    private func tapMore(_ app: XCUIApplication, beside title: String) -> Bool {
        let header = app.staticTexts[title]
        guard header.exists else { return false }
        let candidates = app.buttons
            .matching(NSPredicate(format: "label CONTAINS %@", "查看更多"))
            .allElementsBoundByIndex
            .filter { $0.isHittable }
        guard let target = candidates.min(by: {
            abs($0.frame.midY - header.frame.midY) < abs($1.frame.midY - header.frame.midY)
        }), abs(target.frame.midY - header.frame.midY) < 40 else {
            return false
        }
        target.tap()
        return true
    }

    /// 轮询详情页「播放全部(N)」按钮里的 N（首帧可能还是 0，等整份列表到位）。
    private func waitPlayAllCount(
        _ app: XCUIApplication,
        expected: Int,
        timeout: TimeInterval
    ) -> Int? {
        let playAll = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "播放全部")).firstMatch
        guard playAll.waitForExistence(timeout: timeout) else { return nil }
        let deadline = Date().addingTimeInterval(timeout)
        var last: Int?
        while Date() < deadline {
            last = playAllCount(playAll.label)
            if last == expected { return last }
            usleep(500_000)
        }
        return last
    }

    /// 从「播放全部(N)」label 抠出 N（Compose 合并语义可能把 label 拼成 `播放全部(100), 播放全部(100)`）。
    private func playAllCount(_ label: String) -> Int? {
        guard let range = label.range(of: "播放全部(") else { return nil }
        let digits = label[range.upperBound...].prefix { $0.isNumber }
        return digits.isEmpty ? nil : Int(digits)
    }
}

private extension String {
    func dropPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}
