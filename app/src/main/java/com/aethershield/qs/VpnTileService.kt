package com.aethershield.qs

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ConnectionState
import com.aethershield.core.labelRes
import com.aethershield.ui.MainActivity
import com.aethershield.vpn.VpnController

/**
 * Quick Settings tile: the same toggle as the app, with the settings the user already saved.
 * The tile only reflects state while it is being listened to, which is all Android guarantees.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        when (AppContainer.connection.state.value) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            is ConnectionState.Preparing,
            -> VpnController.stop(this)

            else -> {
                if (VpnController.consentIntent(this) != null) {
                    // Consent can only be granted from an Activity.
                    unlockAndRun { openApp() }
                } else {
                    VpnController.start(this)
                }
            }
        }
        render()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val state = AppContainer.connection.state.value
        tile.state = if (state is ConnectionState.Connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(state.labelRes())
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_vpn)
        tile.updateTile()
    }
}
