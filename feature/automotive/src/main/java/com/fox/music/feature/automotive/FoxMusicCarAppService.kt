package com.fox.music.feature.automotive

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class FoxMusicCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = FoxMusicCarSession()
}

class FoxMusicCarSession : Session() {
    override fun onCreateScreen(intent: Intent) = CarModeRootScreen(carContext)
}
