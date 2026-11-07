package com.aethershield.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ConnectionState
import com.aethershield.core.labelRes
import com.aethershield.ui.MainActivity
import com.aethershield.vpn.VpnController

/**
 * A one-tap switch for the tunnel. It runs exactly the same code path as the app, so it always
 * uses whatever the user last configured -- it is not a separate profile.
 */
class VpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            toggle(context)
            refresh(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.aethershield.widget.TOGGLE"

        private const val ACCENT = 0xFF00C8F0.toInt()
        private const val PLAIN = 0xFFF5F7FA.toInt()

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, VpnWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val state = AppContainer.connection.state.value
            val views = RemoteViews(context.packageName, R.layout.widget_vpn)
            views.setTextViewText(R.id.widget_status, context.getString(state.labelRes()))
            views.setTextColor(
                R.id.widget_status,
                if (state is ConnectionState.Connected) ACCENT else PLAIN,
            )
            views.setOnClickPendingIntent(R.id.widget_root, toggleIntent(context))
            manager.updateAppWidget(id, views)
        }

        private fun toggleIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, VpnWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun toggle(context: Context) {
            when (AppContainer.connection.state.value) {
                is ConnectionState.Connected,
                is ConnectionState.Connecting,
                is ConnectionState.Reconnecting,
                is ConnectionState.Preparing,
                -> VpnController.stop(context)

                else -> start(context)
            }
        }

        private fun start(context: Context) {
            // The first run needs the system VPN consent dialog, which only an Activity can show.
            if (VpnController.consentIntent(context) != null) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                VpnController.start(context)
            }
        }
    }
}
