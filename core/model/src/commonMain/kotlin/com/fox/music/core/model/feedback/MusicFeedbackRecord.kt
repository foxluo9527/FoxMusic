package com.fox.music.core.model.feedback

/**
 * 我提交的「音乐不匹配」反馈记录（客户端「音源反馈记录」页）。
 *
 * 状态语义：
 *  - [status]          人工处理状态：pending 待处理 / resolved 已处理 / rejected 已忽略
 *  - [autoRepairStatus] 自动匹配状态：running 匹配中 / succeeded 已自动换源 / failed 未匹配 / skipped 已跳过
 *  - [autoRepairVia]   自动匹配通道：ai 智能识别 / legacy 旧竞速链
 */
data class MusicFeedbackRecord(
    val id: Long,
    val musicId: Long,
    val musicTitle: String,
    val musicArtists: String,
    val musicCover: String?,
    val musicDuration: Long,
    val content: String?,
    val status: String,
    val createdAt: String?,
    val handledAt: String?,
    val handleNote: String?,
    val autoRepairStatus: String?,
    val autoRepairVia: String?,
    val autoRepairDetail: String?,
    val autoRepairAt: String?,
) {
    /** 自动换成新音源（用户最关心的结果） */
    val autoReplaced: Boolean get() = autoRepairStatus == "succeeded"

    val isPending: Boolean get() = status == "pending"
}
