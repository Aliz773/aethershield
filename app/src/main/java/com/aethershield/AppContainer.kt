package com.aethershield

import com.aethershield.core.AetherProcessEngine
import com.aethershield.core.HevForwarder
import android.app.Application
import android.content.Context
import com.aethershield.core.TunForwarder
import com.aethershield.data.SettingsRepository
import com.aethershield.vpn.ConnectionManager

/** Manual DI (no Hilt yet, to keep the first build simple). Swap to Hilt later. */
object AppContainer {
    lateinit var settings: SettingsRepository
        private set
    lateinit var connection: ConnectionManager
        private set
    lateinit var forwarder: TunForwarder
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        settings = SettingsRepository(app)
        forwarder = HevForwarder(app)
        connection = ConnectionManager(AetherProcessEngine(app))
    }
}

class AetherShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
