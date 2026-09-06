package com.watchrelay.app.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.watchrelay.app.MainActivity
import com.watchrelay.app.R
import com.watchrelay.app.WatchRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class ReceiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: RelayHttpServer? = null
    private var nsd: NsdManager? = null
    private var registered = false

    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            registered = true
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            registered = false
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, notification())
        startServer()
        listening.value = true
        listenUrl.value = LanAddress.listenUrl()
        return START_STICKY
    }

    override fun onDestroy() {
        listening.value = false
        runCatching { server?.stop() }
        if (registered) {
            runCatching { nsd?.unregisterService(registration) }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) return
        val app = application as WatchRelayApp
        val http = RelayHttpServer { fileName, bytes ->
            val imported = runBlocking { app.container.repository.importBytes(fileName, bytes) }
            val message = if (imported.isEmpty()) {
                "Saved $fileName but no workouts were recognized."
            } else {
                "Imported ${imported.size} workout${if (imported.size == 1) "" else "s"} from $fileName."
            }
            lastEvent.value = message
            message
        }
        http.start()
        server = http
        nsd = getSystemService(NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "WatchRelay"
            serviceType = SERVICE_TYPE
            port = LanAddress.PORT
        }
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi‑Fi receive", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.receive_notification_title))
            .setContentText(getString(R.string.receive_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.watchrelay.app.STOP_RECEIVE"
        const val SERVICE_TYPE = "_watchrelay._tcp."
        private const val CHANNEL = "watchrelay-receive"
        private const val NOTIF_ID = 17

        val listening = MutableStateFlow(false)
        val listenUrl = MutableStateFlow("http://0.0.0.0:${LanAddress.PORT}")
        val lastEvent = MutableStateFlow("Idle")

        fun start(context: Context) {
            val intent = Intent(context, ReceiveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReceiveService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
            context.stopService(Intent(context, ReceiveService::class.java))
        }
    }
}
