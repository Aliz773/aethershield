package com.aethershield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ConnectionState
import com.aethershield.core.EngineEndpoint
import com.aethershield.core.EngineUnavailableException
import com.aethershield.core.TunCounters
import com.aethershield.core.TunnelHost
import com.aethershield.core.labelRes
import com.aethershield.ui.MainActivity
import com.aethershield.widget.VpnWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object VpnController {
    /** Non-null => the system consent dialog must be shown first. */
    fun consentIntent(ctx: Context): Intent? = VpnService.prepare(ctx)

    fun start(ctx: Context) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, AetherShieldVpnService::class.java))
    }

    fun stop(ctx: Context) {
        ctx.startService(
            Intent(ctx, AetherShieldVpnService::class.java).setAction(AetherShieldVpnService.ACTION_DISCONNECT)
        )
    }
}

class AetherShieldVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var tun: ParcelFileDescriptor? = null
    private var alive = true

    /**
     * The TUN is opened once and kept across reconnects. hev-socks5-tunnel keeps a foreign
     * fd instead of closing it, and Builder.establish() is the expensive part that rewrites
     * the system's routing -- there is no reason to redo it for a new SOCKS port.
     */
    private inner class Host : TunnelHost {
        override suspend fun attach(endpoint: EngineEndpoint) = withContext(Dispatchers.IO) {
            val pfd = tun ?: openTun().also { tun = it }
            AppContainer.forwarder.start(pfd, endpoint)
        }

        override fun detach() {
            runCatching { AppContainer.forwarder.stop() }
            runCatching { tun?.close() }
            tun = null
        }

        override fun traffic(): TunCounters? = AppContainer.forwarder.traffic()

        private fun openTun(): ParcelFileDescriptor = Builder()
            .setSession("AetherShield")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("1.0.0.1")
            // Keeps the core's own sockets (and our probes) out of the tunnel: no loop.
            .addDisallowedApplication(packageName)
            .establish() ?: throw EngineUnavailableException(
            "VPN permission is missing or was revoked"
        )
    }

    private val host = Host()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            AppContainer.connection.state.collect { s ->
                if (alive) runCatching {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, buildNotification(s))
                    VpnWidgetProvider.refresh(this@AetherShieldVpnService)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(AppContainer.connection.state.value))
        val manager = AppContainer.connection
        if (intent?.action == ACTION_DISCONNECT) {
            val job = manager.disconnect(host)
            scope.launch { job.join(); shutdown() }
        } else {
            scope.launch {
                val settings = AppContainer.settings.settings.first()
                // Returns only when the session is over: Connected (kept), or Error.
                manager.connect(settings, host).join()
                if (manager.state.value is ConnectionState.Error) shutdown()
            }
        }
        return START_NOT_STICKY
    }

    private fun shutdown() {
        alive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        AppContainer.connection.disconnect(host)
        super.onRevoke()
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        // The scope is gone, so the suspending teardown cannot run: take the core down here.
        runCatching { AppContainer.connection.killNow() }
        host.detach()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, AetherShieldVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val accent = when (state) {
            is ConnectionState.Connected -> 0xFF00C8F0.toInt()
            is ConnectionState.Error -> 0xFFFF453A.toInt()
            else -> 0xFFF5F7FA.toInt()
        }
        val card = RemoteViews(packageName, R.layout.notification_vpn).apply {
            setTextViewText(R.id.notif_status, getString(state.labelRes()))
            setTextColor(R.id.notif_status, accent)
            val since = (state as? ConnectionState.Connected)?.sinceMillis
            if (since != null) {
                setChronometer(R.id.notif_timer, since, null, true)
                setViewVisibility(R.id.notif_timer, View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_timer, View.GONE)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.notif_large))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(card)
            .setCustomBigContentView(card)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(state.labelRes()))
            .setContentIntent(open)
            .addAction(0, getString(R.string.btn_disconnect), disconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.aethershield.DISCONNECT"
        private const val CHANNEL = "vpn_status"
        private const val NOTIF_ID = 1
        private const val MTU = 1500
        private const val TUN_ADDRESS = "10.8.0.2"
    }
}
