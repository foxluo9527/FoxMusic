package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.music.Artist
import javax.inject.Inject

class GetAllArtistsUseCase @Inject constructor(
    private val artistRepository: ArtistRepository,
) {
    suspend operator fun invoke(limit: Int = 500): Result<List<Artist>> {
        val allArtists = mutableListOf<Artist>()
        var page = 1

        while (true) {
            when (val result = artistRepository.getArtistList(page = page, limit = limit)) {
                is Result.Success -> {
                    allArtists += result.data.list
                    if (!result.data.hasMore) break
                    page++
                }
                is Result.Error -> return result
                is Result.Loading -> return Result.error(
                    IllegalStateException("Unexpected loading state"),
                )
            }
        }

        return Result.success(allArtists.distinctBy { it.id })
    }
}
