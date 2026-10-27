package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.CreateAdminMusicInput
import com.fox.music.core.domain.repository.LibraryLyricsForbidden
import com.fox.music.core.domain.repository.LoginResult
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.user.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncLibraryLyricsUseCaseTest {

    @Test
    fun regularUserDoesNotWriteLibrary() = runBlocking<Unit> {
        val admin = RecordingAdmin()
        val useCase = useCase(profile = Result.success(user("user")), admin = admin)
        val outcome = useCase(musicId = 12, isThirdParty = false, lyrics = LRC, lyricsTrans = null)
        assertIs<LibraryLyricSyncOutcome.SkippedNotAdmin>(outcome)
        assertEquals(0, admin.calls)
    }

    @Test
    fun adminWritesLyricsAndTranslation() = runBlocking<Unit> {
        val admin = RecordingAdmin()
        val useCase = useCase(profile = Result.success(user("admin")), admin = admin)
        val outcome = useCase(musicId = 12, isThirdParty = false, lyrics = LRC, lyricsTrans = TRANS)
        assertIs<LibraryLyricSyncOutcome.Synced>(outcome)
        assertEquals(1, admin.calls)
        assertEquals(12L, admin.lastId)
        assertEquals(LRC, admin.lastLyrics)
        assertEquals(TRANS, admin.lastTrans)
    }

    @Test
    fun superAdminWritesLibrary() = runBlocking<Unit> {
        val admin = RecordingAdmin()
        val useCase = useCase(profile = Result.success(user("superadmin")), admin = admin)
        val outcome = useCase(musicId = 3, isThirdParty = false, lyrics = LRC, lyricsTrans = null)
        assertIs<LibraryLyricSyncOutcome.Synced>(outcome)
        assertEquals(1, admin.calls)
        assertEquals(null, admin.lastTrans)
    }

    @Test
    fun thirdPartyAndMissingIdAreSkipped() = runBlocking<Unit> {
        val admin = RecordingAdmin()
        val useCase = useCase(profile = Result.success(user("admin")), admin = admin)
        assertIs<LibraryLyricSyncOutcome.SkippedNotLibrary>(
            useCase(musicId = -5, isThirdParty = true, lyrics = LRC, lyricsTrans = null),
        )
        assertIs<LibraryLyricSyncOutcome.SkippedNotLibrary>(
            useCase(musicId = 0, isThirdParty = false, lyrics = LRC, lyricsTrans = null),
        )
        assertEquals(0, admin.calls)
    }

    @Test
    fun adminFailureIsReported() = runBlocking<Unit> {
        val admin = RecordingAdmin(updateResult = Result.error(Exception("数据库忙"), "数据库忙"))
        val useCase = useCase(profile = Result.success(user("admin")), admin = admin)
        val outcome = useCase(musicId = 8, isThirdParty = false, lyrics = LRC, lyricsTrans = null)
        val failed = assertIs<LibraryLyricSyncOutcome.Failed>(outcome)
        assertEquals("数据库忙", failed.message)
    }

    @Test
    fun unknownProfileStillSyncsUnlessForbidden() = runBlocking<Unit> {
        val forbidden = RecordingAdmin(updateResult = Result.error(LibraryLyricsForbidden(), "无权限"))
        val skipped = useCase(profile = Result.error(Exception("timeout")), admin = forbidden)
            .invoke(musicId = 8, isThirdParty = false, lyrics = LRC, lyricsTrans = null)
        assertIs<LibraryLyricSyncOutcome.SkippedNotAdmin>(skipped)

        val ok = RecordingAdmin()
        val synced = useCase(profile = Result.error(Exception("timeout")), admin = ok)
            .invoke(musicId = 8, isThirdParty = false, lyrics = LRC, lyricsTrans = null)
        assertIs<LibraryLyricSyncOutcome.Synced>(synced)
    }

    private fun useCase(profile: Result<User>, admin: AdminMusicRepository) = SyncLibraryLyricsUseCase(
        getProfileUseCase = GetProfileUseCase(FakeAuthRepository(profile)),
        adminMusicRepository = admin,
    )

    private fun user(role: String) = User(id = 1, username = "fox", role = role)

    private class FakeAuthRepository(
        private val profile: Result<User>,
    ) : AuthRepository {
        override suspend fun login(username: String, password: String): Result<LoginResult> = unused()
        override suspend fun register(username: String, password: String, email: String): Result<LoginResult> = unused()
        override suspend fun logout(): Result<Unit> = unused()
        override suspend fun getProfile(): Result<User> = profile
        override suspend fun updateProfile(
            nickname: String?,
            avatar: String?,
            signature: String?,
            email: String?,
        ): Result<User> = unused()
        override suspend fun forgotPassword(email: String): Result<Unit?> = unused()
        override suspend fun resetPassword(code: String, email: String, newPassword: String): Result<Unit?> = unused()
        private fun unused(): Nothing = error("unused")
    }

    private class RecordingAdmin(
        private val updateResult: Result<Unit> = Result.success(Unit),
    ) : AdminMusicRepository {
        var calls = 0
        var lastId: Long? = null
        var lastLyrics: String? = null
        var lastTrans: String? = null

        override suspend fun getAdminMusicList(page: Int, limit: Int, keyword: String?): Result<PagedData<Music>> = unused()
        override suspend fun deleteAdminMusic(id: Long): Result<Unit> = unused()
        override suspend fun createAdminMusic(input: CreateAdminMusicInput): Result<Music> = unused()
        override suspend fun updateMusicLyrics(id: Long, lyrics: String, lyricsTrans: String?): Result<Unit> {
            calls += 1
            lastId = id
            lastLyrics = lyrics
            lastTrans = lyricsTrans
            return updateResult
        }
        private fun unused(): Nothing = error("unused")
    }

    private companion object {
        const val LRC = "[00:01.00]你好"
        const val TRANS = "[00:01.00]hello"
    }
}
