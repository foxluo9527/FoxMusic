package com.fox.music.core.assistant

/**
 * 助手动作类型白名单（与服务端 services/aiAssistant.js 的 ACTION_TOOLS 对齐）。
 * 端侧只 dispatch 白名单内的动作；服务端护栏已保证不会出现名单外类型，
 * 这里再兜一层（纵深防御）。
 */
object AssistantActionTypes {
    const val PLAY_MUSIC = "play_music"
    const val PLAY_PLAYLIST = "play_playlist"
    const val PLAY_ALBUM = "play_album"
    const val OPEN_SCREEN = "open_screen"
    const val CREATE_PLAYLIST = "create_playlist"
    const val ADD_TO_PLAYLIST = "add_to_playlist"
    const val FAVORITE_MUSIC = "favorite_music"
    const val FAVORITE_PLAYLIST = "favorite_playlist"
    const val FAVORITE_ALBUM = "favorite_album"
    const val SEARCH_IN_APP = "search_in_app"
    const val SEND_MESSAGE = "send_message"
    const val ADD_FRIEND = "add_friend"

    val ALL: Set<String> = setOf(
        PLAY_MUSIC, PLAY_PLAYLIST, PLAY_ALBUM, OPEN_SCREEN, CREATE_PLAYLIST,
        ADD_TO_PLAYLIST, FAVORITE_MUSIC, FAVORITE_PLAYLIST, FAVORITE_ALBUM,
        SEARCH_IN_APP, SEND_MESSAGE, ADD_FRIEND,
    )

    /** open_screen 的合法页面标识（与服务端 SCREEN_WHITELIST 对齐） */
    val SCREENS: Set<String> = setOf(
        "home", "discover", "community", "messages", "profile", "search",
        "playlist", "music", "album", "artist", "rank", "favorites", "history",
    )
}

/** 单个动作的执行结果。requiresConfirm 的动作由 UI 层确认后再调 dispatch。 */
sealed interface DispatchResult {
    /** 执行成功（message 为可 Toast 的简短说明） */
    data class Success(val message: String) : DispatchResult

    /** 执行失败（参数非法 / 数据不存在 / 端侧接口报错） */
    data class Failed(val reason: String) : DispatchResult

    /** 当前端侧版本尚未实现该动作（UI 应提示暂不支持，不阻塞其它动作） */
    data class Unsupported(val actionType: String) : DispatchResult
}

/**
 * 动作意图执行器。
 *
 * Android 实现（core:assistant-android）把动作映射到 MusicController /
 * domain UseCase——复用既有 MVI 通道，账号维度与鉴权天然满足（方案 4.1 铁律）。
 * iOS 实现放 iosShared。
 */
interface AssistantActionDispatcher {
    suspend fun dispatch(action: AssistantAction): DispatchResult
}
