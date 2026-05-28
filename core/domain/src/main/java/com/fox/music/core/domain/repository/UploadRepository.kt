package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result

interface UploadRepository {

    suspend fun uploadImage(uri: Uri): Result<String>
}
