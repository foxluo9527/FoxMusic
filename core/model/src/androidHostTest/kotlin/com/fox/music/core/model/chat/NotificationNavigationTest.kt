package com.fox.music.core.model.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationTest {

    @Test
    fun `post comment notification resolves post id`() {
        val notification = Notification(
            id = 1,
            type = NotificationType.COMMENT,
            title = "新评论提醒",
            content = "用户评论了你的帖子",
            targetId = 789L,
            targetType = "comment",
        )

        assertEquals(789L, notification.resolvePostId())
        assertNull(notification.resolveMusicCommentId())
    }

    @Test
    fun `music comment notification resolves music comment id only`() {
        val notification = Notification(
            id = 2,
            type = NotificationType.COMMENT,
            title = "新评论提醒",
            content = "foxluo1 回复了你的评论：哈哈哈",
            targetId = 98L,
            targetType = "music_comment",
        )

        assertNull(notification.resolvePostId())
        assertEquals(98L, notification.resolveMusicCommentId())
    }
}
