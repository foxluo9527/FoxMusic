package com.fox.music.core.domain.di

import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.AppUpdateRepository
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.MusicFeedbackRepository
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.RecommendedMusicCacheManager
import com.fox.music.core.domain.repository.ReportRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.domain.usecase.AddTrackToPlaylistUseCase
import com.fox.music.core.domain.usecase.BlockUserUseCase
import com.fox.music.core.domain.usecase.CheckAppUpdateUseCase
import com.fox.music.core.domain.usecase.ClearSearchHistoryUseCase
import com.fox.music.core.domain.usecase.CreatePlaylistUseCase
import com.fox.music.core.domain.usecase.CreatePostUseCase
import com.fox.music.core.domain.usecase.DeletePlaylistUseCase
import com.fox.music.core.domain.usecase.DeletePostCommentUseCase
import com.fox.music.core.domain.usecase.DeletePostUseCase
import com.fox.music.core.domain.usecase.FetchMusicLyricsUseCase
import com.fox.music.core.domain.usecase.ForgotPasswordUseCase
import com.fox.music.core.domain.usecase.GetAdminMusicPagingUseCase
import com.fox.music.core.domain.usecase.GetAlbumDetailUseCase
import com.fox.music.core.domain.usecase.GetAlbumListUseCase
import com.fox.music.core.domain.usecase.GetAllArtistsUseCase
import com.fox.music.core.domain.usecase.GetArtistDetailUseCase
import com.fox.music.core.domain.usecase.GetBlacklistUseCase
import com.fox.music.core.domain.usecase.GetCategoryPlaylistsUseCase
import com.fox.music.core.domain.usecase.GetCommentRepliesUseCase
import com.fox.music.core.domain.usecase.GetFavoritesUseCase
import com.fox.music.core.domain.usecase.GetHotArtistPagingUseCase
import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetListeningStatsUseCase
import com.fox.music.core.domain.usecase.GetMusicCommentsPagingUseCase
import com.fox.music.core.domain.usecase.GetMusicCommentsUseCase
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlayHistoryUseCase
import com.fox.music.core.domain.usecase.GetPlaylistCategoriesUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistsPagingUseCase
import com.fox.music.core.domain.usecase.GetPostCommentPreviewUseCase
import com.fox.music.core.domain.usecase.GetPostCommentRepliesPagingUseCase
import com.fox.music.core.domain.usecase.GetPostCommentsPagingUseCase
import com.fox.music.core.domain.usecase.GetPostDetailUseCase
import com.fox.music.core.domain.usecase.GetPostLikesPagingUseCase
import com.fox.music.core.domain.usecase.GetPostTagsUseCase
import com.fox.music.core.domain.usecase.GetPostsPagingUseCase
import com.fox.music.core.domain.usecase.GetPrivacySettingsUseCase
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.domain.usecase.GetRankDetailUseCase
import com.fox.music.core.domain.usecase.GetRecentPostsUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPlaylistsUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPostTagsUseCase
import com.fox.music.core.domain.usecase.GetReportHistoryUseCase
import com.fox.music.core.domain.usecase.GetSearchAlbumPagingUseCase
import com.fox.music.core.domain.usecase.GetSearchArtistPagingUseCase
import com.fox.music.core.domain.usecase.GetSearchHistoryUseCase
import com.fox.music.core.domain.usecase.GetSearchMusicPagingUseCase
import com.fox.music.core.domain.usecase.GetUserHomeProfileUseCase
import com.fox.music.core.domain.usecase.GetUserPostsPagingUseCase
import com.fox.music.core.domain.usecase.LikeMusicCommentUseCase
import com.fox.music.core.domain.usecase.LoginUseCase
import com.fox.music.core.domain.usecase.LogoutUseCase
import com.fox.music.core.domain.usecase.PostCommentUseCase
import com.fox.music.core.domain.usecase.PostMusicCommentUseCase
import com.fox.music.core.domain.usecase.RecordPlayUseCase
import com.fox.music.core.domain.usecase.RegisterUseCase
import com.fox.music.core.domain.usecase.RemoveTrackFromPlaylistUseCase
import com.fox.music.core.domain.usecase.RemoveTracksFromPlaylistUseCase
import com.fox.music.core.domain.usecase.ResetPasswordUseCase
import com.fox.music.core.domain.usecase.ResolveMusicCommentNavigationUseCase
import com.fox.music.core.domain.usecase.ResolveThirdPartyAudioUseCase
import com.fox.music.core.domain.usecase.SaveSearchHistoryUseCase
import com.fox.music.core.domain.usecase.SearchMusicUseCase
import com.fox.music.core.domain.usecase.SearchProvidersByTrackUseCase
import com.fox.music.core.domain.usecase.SearchProviderCandidatesUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.domain.usecase.SubmitMusicFeedbackUseCase
import com.fox.music.core.domain.usecase.GetMyMusicFeedbacksUseCase
import com.fox.music.core.domain.usecase.ReplaceMusicSourceUseCase
import com.fox.music.core.domain.usecase.CheckMusicExistsUseCase
import com.fox.music.core.domain.usecase.ImportSingleTrackUseCase
import com.fox.music.core.domain.usecase.FetchScrapedLyricsUseCase
import com.fox.music.core.domain.usecase.ToggleAlbumFavoriteUseCase
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePlaylistFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePostFavoriteUseCase
import com.fox.music.core.domain.usecase.TogglePostLikeUseCase
import com.fox.music.core.domain.usecase.UnblockUserUseCase
import com.fox.music.core.domain.usecase.UpdateHomepageBackgroundUseCase
import com.fox.music.core.domain.usecase.UpdatePlaylistUseCase
import com.fox.music.core.domain.usecase.UpdatePostUseCase
import com.fox.music.core.domain.usecase.UpdatePrivacySettingsUseCase
import com.fox.music.core.domain.usecase.UpdateProfileUseCase
import com.fox.music.core.domain.usecase.UploadImageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * UseCase 显式装配模块。
 *
 * :core:domain 是 KMP 模块，没有 Java 编译任务，Dagger/Hilt 生成的 Java 存根
 * 会被静默丢弃，因此 UseCase 不能用 @Inject 构造函数注入，改由本模块逐个 @Provides。
 * 本文件由脚本生成（扫描各 UseCase 的构造函数签名），新增 UseCase 后需同步添加。
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideCheckAppUpdateUseCase(appUpdateRepository: AppUpdateRepository): CheckAppUpdateUseCase = CheckAppUpdateUseCase(appUpdateRepository)

    @Provides
    fun provideCreatePostUseCase(socialRepository: SocialRepository, uploadRepository: UploadRepository, postDraftRepository: PostDraftRepository): CreatePostUseCase = CreatePostUseCase(socialRepository, uploadRepository, postDraftRepository)

    @Provides
    fun provideGetAdminMusicPagingUseCase(adminMusicRepository: AdminMusicRepository): GetAdminMusicPagingUseCase = GetAdminMusicPagingUseCase(adminMusicRepository)

    @Provides
    fun provideGetAlbumListUseCase(albumRepository: AlbumRepository): GetAlbumListUseCase = GetAlbumListUseCase(albumRepository)

    @Provides
    fun provideGetHotArtistPagingUseCase(artistRepository: ArtistRepository): GetHotArtistPagingUseCase = GetHotArtistPagingUseCase(artistRepository)

    @Provides
    fun provideGetMusicCommentsPagingUseCase(musicRepository: MusicRepository): GetMusicCommentsPagingUseCase = GetMusicCommentsPagingUseCase(musicRepository)

    @Provides
    fun provideGetMusicListUseCase(musicRepository: MusicRepository, recommendedMusicCacheManager: RecommendedMusicCacheManager): GetMusicListUseCase = GetMusicListUseCase(musicRepository, recommendedMusicCacheManager)

    @Provides
    fun provideGetPlaylistsPagingUseCase(playlistRepository: PlaylistRepository): GetPlaylistsPagingUseCase = GetPlaylistsPagingUseCase(playlistRepository)

    @Provides
    fun provideGetPostCommentRepliesPagingUseCase(socialRepository: SocialRepository): GetPostCommentRepliesPagingUseCase = GetPostCommentRepliesPagingUseCase(socialRepository)

    @Provides
    fun provideGetPostCommentsPagingUseCase(socialRepository: SocialRepository): GetPostCommentsPagingUseCase = GetPostCommentsPagingUseCase(socialRepository)

    @Provides
    fun provideGetPostLikesPagingUseCase(socialRepository: SocialRepository): GetPostLikesPagingUseCase = GetPostLikesPagingUseCase(socialRepository)

    @Provides
    fun provideGetPostsPagingUseCase(socialRepository: SocialRepository): GetPostsPagingUseCase = GetPostsPagingUseCase(socialRepository)

    @Provides
    fun provideGetSearchAlbumPagingUseCase(getAlbumListUseCase: GetAlbumListUseCase): GetSearchAlbumPagingUseCase = GetSearchAlbumPagingUseCase(getAlbumListUseCase)

    @Provides
    fun provideGetSearchArtistPagingUseCase(artistRepository: ArtistRepository): GetSearchArtistPagingUseCase = GetSearchArtistPagingUseCase(artistRepository)

    @Provides
    fun provideGetSearchMusicPagingUseCase(searchRepository: SearchRepository): GetSearchMusicPagingUseCase = GetSearchMusicPagingUseCase(searchRepository)

    @Provides
    fun provideGetUserPostsPagingUseCase(socialRepository: SocialRepository): GetUserPostsPagingUseCase = GetUserPostsPagingUseCase(socialRepository)

    @Provides
    fun provideUpdateHomepageBackgroundUseCase(uploadRepository: UploadRepository, userRepository: UserRepository): UpdateHomepageBackgroundUseCase = UpdateHomepageBackgroundUseCase(uploadRepository, userRepository)

    @Provides
    fun provideUploadImageUseCase(uploadRepository: UploadRepository): UploadImageUseCase = UploadImageUseCase(uploadRepository)

    @Provides
    fun provideAddTrackToPlaylistUseCase(playlistRepository: PlaylistRepository): AddTrackToPlaylistUseCase = AddTrackToPlaylistUseCase(playlistRepository)

    @Provides
    fun provideBlockUserUseCase(userRepository: UserRepository): BlockUserUseCase = BlockUserUseCase(userRepository)

    @Provides
    fun provideClearSearchHistoryUseCase(searchRepository: SearchRepository): ClearSearchHistoryUseCase = ClearSearchHistoryUseCase(searchRepository)

    @Provides
    fun provideCreatePlaylistUseCase(playlistRepository: PlaylistRepository): CreatePlaylistUseCase = CreatePlaylistUseCase(playlistRepository)

    @Provides
    fun provideDeletePlaylistUseCase(playlistRepository: PlaylistRepository): DeletePlaylistUseCase = DeletePlaylistUseCase(playlistRepository)

    @Provides
    fun provideDeletePostCommentUseCase(socialRepository: SocialRepository): DeletePostCommentUseCase = DeletePostCommentUseCase(socialRepository)

    @Provides
    fun provideDeletePostUseCase(socialRepository: SocialRepository): DeletePostUseCase = DeletePostUseCase(socialRepository)

    @Provides
    fun provideFetchMusicLyricsUseCase(musicRepository: MusicRepository, importRepository: ImportRepository): FetchMusicLyricsUseCase = FetchMusicLyricsUseCase(musicRepository, importRepository)

    @Provides
    fun provideForgotPasswordUseCase(authRepository: AuthRepository): ForgotPasswordUseCase = ForgotPasswordUseCase(authRepository)

    @Provides
    fun provideGetAlbumDetailUseCase(albumRepository: AlbumRepository): GetAlbumDetailUseCase = GetAlbumDetailUseCase(albumRepository)

    @Provides
    fun provideGetAllArtistsUseCase(artistRepository: ArtistRepository): GetAllArtistsUseCase = GetAllArtistsUseCase(artistRepository)

    @Provides
    fun provideGetArtistDetailUseCase(artistRepository: ArtistRepository): GetArtistDetailUseCase = GetArtistDetailUseCase(artistRepository)

    @Provides
    fun provideGetBlacklistUseCase(userRepository: UserRepository): GetBlacklistUseCase = GetBlacklistUseCase(userRepository)

    @Provides
    fun provideGetCategoryPlaylistsUseCase(playlistRepository: PlaylistRepository): GetCategoryPlaylistsUseCase = GetCategoryPlaylistsUseCase(playlistRepository)

    @Provides
    fun provideGetCommentRepliesUseCase(musicRepository: MusicRepository): GetCommentRepliesUseCase = GetCommentRepliesUseCase(musicRepository)

    @Provides
    fun provideGetFavoritesUseCase(favoriteRepository: FavoriteRepository): GetFavoritesUseCase = GetFavoritesUseCase(favoriteRepository)

    @Provides
    fun provideGetHotKeywordsUseCase(searchRepository: SearchRepository): GetHotKeywordsUseCase = GetHotKeywordsUseCase(searchRepository)

    @Provides
    fun provideGetListeningStatsUseCase(musicRepository: MusicRepository): GetListeningStatsUseCase = GetListeningStatsUseCase(musicRepository)

    @Provides
    fun provideGetMusicCommentsUseCase(musicRepository: MusicRepository): GetMusicCommentsUseCase = GetMusicCommentsUseCase(musicRepository)

    @Provides
    fun provideGetMusicDetailUseCase(musicRepository: MusicRepository): GetMusicDetailUseCase = GetMusicDetailUseCase(musicRepository)

    @Provides
    fun provideGetPlayHistoryUseCase(musicRepository: MusicRepository): GetPlayHistoryUseCase = GetPlayHistoryUseCase(musicRepository)

    @Provides
    fun provideGetPlaylistCategoriesUseCase(playlistRepository: PlaylistRepository): GetPlaylistCategoriesUseCase = GetPlaylistCategoriesUseCase(playlistRepository)

    @Provides
    fun provideGetPlaylistDetailUseCase(playlistRepository: PlaylistRepository): GetPlaylistDetailUseCase = GetPlaylistDetailUseCase(playlistRepository)

    @Provides
    fun provideGetPlaylistListUseCase(playlistRepository: PlaylistRepository): GetPlaylistListUseCase = GetPlaylistListUseCase(playlistRepository)

    @Provides
    fun provideGetPostCommentPreviewUseCase(socialRepository: SocialRepository): GetPostCommentPreviewUseCase = GetPostCommentPreviewUseCase(socialRepository)

    @Provides
    fun provideGetPostDetailUseCase(socialRepository: SocialRepository): GetPostDetailUseCase = GetPostDetailUseCase(socialRepository)

    @Provides
    fun provideGetPostTagsUseCase(socialRepository: SocialRepository): GetPostTagsUseCase = GetPostTagsUseCase(socialRepository)

    @Provides
    fun provideGetPrivacySettingsUseCase(userRepository: UserRepository): GetPrivacySettingsUseCase = GetPrivacySettingsUseCase(userRepository)

    @Provides
    fun provideGetProfileUseCase(authRepository: AuthRepository): GetProfileUseCase = GetProfileUseCase(authRepository)

    @Provides
    fun provideGetRankDetailUseCase(playlistRepository: PlaylistRepository): GetRankDetailUseCase = GetRankDetailUseCase(playlistRepository)

    @Provides
    fun provideGetRecentPostsUseCase(socialRepository: SocialRepository): GetRecentPostsUseCase = GetRecentPostsUseCase(socialRepository)

    @Provides
    fun provideGetRecommendedPlaylistsUseCase(playlistRepository: PlaylistRepository): GetRecommendedPlaylistsUseCase = GetRecommendedPlaylistsUseCase(playlistRepository)

    @Provides
    fun provideGetRecommendedPostTagsUseCase(socialRepository: SocialRepository): GetRecommendedPostTagsUseCase = GetRecommendedPostTagsUseCase(socialRepository)

    @Provides
    fun provideGetReportHistoryUseCase(reportRepository: ReportRepository): GetReportHistoryUseCase = GetReportHistoryUseCase(reportRepository)

    @Provides
    fun provideGetSearchHistoryUseCase(searchRepository: SearchRepository): GetSearchHistoryUseCase = GetSearchHistoryUseCase(searchRepository)

    @Provides
    fun provideGetUserHomeProfileUseCase(userRepository: UserRepository): GetUserHomeProfileUseCase = GetUserHomeProfileUseCase(userRepository)

    @Provides
    fun provideLikeMusicCommentUseCase(musicRepository: MusicRepository): LikeMusicCommentUseCase = LikeMusicCommentUseCase(musicRepository)

    @Provides
    fun provideLoginUseCase(authRepository: AuthRepository): LoginUseCase = LoginUseCase(authRepository)

    @Provides
    fun provideLogoutUseCase(authRepository: AuthRepository): LogoutUseCase = LogoutUseCase(authRepository)

    @Provides
    fun providePostCommentUseCase(socialRepository: SocialRepository): PostCommentUseCase = PostCommentUseCase(socialRepository)

    @Provides
    fun providePostMusicCommentUseCase(musicRepository: MusicRepository): PostMusicCommentUseCase = PostMusicCommentUseCase(musicRepository)

    @Provides
    fun provideRecordPlayUseCase(musicRepository: MusicRepository): RecordPlayUseCase = RecordPlayUseCase(musicRepository)

    @Provides
    fun provideRegisterUseCase(authRepository: AuthRepository): RegisterUseCase = RegisterUseCase(authRepository)

    @Provides
    fun provideRemoveTrackFromPlaylistUseCase(playlistRepository: PlaylistRepository): RemoveTrackFromPlaylistUseCase = RemoveTrackFromPlaylistUseCase(playlistRepository)

    @Provides
    fun provideRemoveTracksFromPlaylistUseCase(playlistRepository: PlaylistRepository): RemoveTracksFromPlaylistUseCase = RemoveTracksFromPlaylistUseCase(playlistRepository)

    @Provides
    fun provideResetPasswordUseCase(authRepository: AuthRepository): ResetPasswordUseCase = ResetPasswordUseCase(authRepository)

    @Provides
    fun provideResolveMusicCommentNavigationUseCase(musicRepository: MusicRepository): ResolveMusicCommentNavigationUseCase = ResolveMusicCommentNavigationUseCase(musicRepository)

    @Provides
    fun provideSaveSearchHistoryUseCase(searchRepository: SearchRepository): SaveSearchHistoryUseCase = SaveSearchHistoryUseCase(searchRepository)

    @Provides
    fun provideSearchMusicUseCase(searchRepository: SearchRepository): SearchMusicUseCase = SearchMusicUseCase(searchRepository)

    @Provides
    fun provideSubmitReportUseCase(reportRepository: ReportRepository): SubmitReportUseCase = SubmitReportUseCase(reportRepository)

    @Provides
    fun provideSubmitMusicFeedbackUseCase(musicFeedbackRepository: MusicFeedbackRepository): SubmitMusicFeedbackUseCase =
        SubmitMusicFeedbackUseCase(musicFeedbackRepository)

    @Provides
    fun provideGetMyMusicFeedbacksUseCase(musicFeedbackRepository: MusicFeedbackRepository): GetMyMusicFeedbacksUseCase =
        GetMyMusicFeedbacksUseCase(musicFeedbackRepository)

    @Provides
    fun provideReplaceMusicSourceUseCase(musicFeedbackRepository: MusicFeedbackRepository): ReplaceMusicSourceUseCase =
        ReplaceMusicSourceUseCase(musicFeedbackRepository)

    @Provides
    fun provideToggleAlbumFavoriteUseCase(albumRepository: AlbumRepository): ToggleAlbumFavoriteUseCase = ToggleAlbumFavoriteUseCase(albumRepository)

    @Provides
    fun provideToggleMusicFavoriteUseCase(musicRepository: MusicRepository): ToggleMusicFavoriteUseCase = ToggleMusicFavoriteUseCase(musicRepository)

    @Provides
    fun provideTogglePlaylistFavoriteUseCase(playlistRepository: PlaylistRepository): TogglePlaylistFavoriteUseCase = TogglePlaylistFavoriteUseCase(playlistRepository)

    @Provides
    fun provideTogglePostFavoriteUseCase(socialRepository: SocialRepository): TogglePostFavoriteUseCase = TogglePostFavoriteUseCase(socialRepository)

    @Provides
    fun provideTogglePostLikeUseCase(socialRepository: SocialRepository): TogglePostLikeUseCase = TogglePostLikeUseCase(socialRepository)

    @Provides
    fun provideUnblockUserUseCase(userRepository: UserRepository): UnblockUserUseCase = UnblockUserUseCase(userRepository)

    @Provides
    fun provideUpdatePlaylistUseCase(playlistRepository: PlaylistRepository): UpdatePlaylistUseCase = UpdatePlaylistUseCase(playlistRepository)

    @Provides
    fun provideUpdatePostUseCase(socialRepository: SocialRepository): UpdatePostUseCase = UpdatePostUseCase(socialRepository)

    @Provides
    fun provideUpdatePrivacySettingsUseCase(userRepository: UserRepository): UpdatePrivacySettingsUseCase = UpdatePrivacySettingsUseCase(userRepository)

    @Provides
    fun provideUpdateProfileUseCase(authRepository: AuthRepository): UpdateProfileUseCase = UpdateProfileUseCase(authRepository)

    @Provides
    fun provideSearchProvidersByTrackUseCase(searchRepository: SearchRepository): SearchProvidersByTrackUseCase =
        SearchProvidersByTrackUseCase(searchRepository)

    @Provides
    fun provideResolveThirdPartyAudioUseCase(searchRepository: SearchRepository): ResolveThirdPartyAudioUseCase =
        ResolveThirdPartyAudioUseCase(searchRepository)

    @Provides
    fun provideCheckMusicExistsUseCase(searchRepository: SearchRepository): CheckMusicExistsUseCase =
        CheckMusicExistsUseCase(searchRepository)

    @Provides
    fun provideImportSingleTrackUseCase(searchRepository: SearchRepository): ImportSingleTrackUseCase =
        ImportSingleTrackUseCase(searchRepository)

    @Provides
    fun provideSearchProviderCandidatesUseCase(searchRepository: SearchRepository): SearchProviderCandidatesUseCase =
        SearchProviderCandidatesUseCase(searchRepository)

    @Provides
    fun provideFetchScrapedLyricsUseCase(searchRepository: SearchRepository): FetchScrapedLyricsUseCase =
        FetchScrapedLyricsUseCase(searchRepository)

}