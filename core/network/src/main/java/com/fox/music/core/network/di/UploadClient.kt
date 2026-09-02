package com.fox.music.core.network.di

import javax.inject.Qualifier

/** OkHttp client with extended timeouts for large media uploads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadClient
