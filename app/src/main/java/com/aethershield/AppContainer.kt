package com.aethershield

import android.app.Application
import android.content.Context
import com.aethershield.core.AetherProcessEngine
import com.aethershield.core.HevForwarder
import com.aethershield.core.TunForwarder
import com.aethershield.data.SettingsRepository
import com.aethershield.sharing.SharingServer
import com.aethershield.vpn.ConnectionManager

/** Manual DI (no Hilt yet, to keep the first build simple). Swap to Hilt later. */
object AppContainer {
    lateinit var settings: SettingsRepository
        private set
    lateinit var connection: ConnectionManager
        private set
    lateinit var forwarder: TunForwarder
        private set
    lateinit var sharing: SharingServer
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        settings = SettingsRepository(app)
        forwarder = HevForwarder(app)
        connection = ConnectionManager(AetherProcessEngine(app))
        // The share server reads the tunnel's current SOCKS port on every connection, so it
        // survives a reconnect without being restarted.
        sharing = SharingServer { connection.activeEndpoint.value }
    }
}

class AetherShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // Android decides when this actually runs; it is a daily-ish check, not a guarantee.
        com.aethershield.update.UpdateScheduler.schedule(this)
    }
}
