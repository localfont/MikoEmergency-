package com.miko.emergency.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miko.emergency.R
import com.miko.emergency.mesh.MeshNetworkManager
import com.miko.emergency.model.EmergencyMessage
import com.miko.emergency.model.MessageType
import com.miko.emergency.ui.MainActivity

class MeshForegroundService : Service() {

    private val TAG = "MeshForegroundService"
    private val CHANNEL_ID = "miko_mesh_channel"
    private val NOTIFICATION_ID = 1001

    inner class LocalBinder : Binder() {
        fun getService(): MeshForegroundService = this@MeshForegroundService
    }

    private val binder = LocalBinder()
    lateinit var meshManager: MeshNetworkManager
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        meshManager = MeshNetworkManager(applicationContext)
        meshManager.onMessageReceived = { message -> notifyIncomingMessage(message) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Mesh ağı aktif", "Acil durum ağına bağlanıldı"))
        meshManager.initialize()
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        meshManager.destroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Miko Emergency Mesh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Acil durum mesh ağı servisi"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun notifyIncomingMessage(message: EmergencyMessage) {
        if (message.type == MessageType.SOS || message.type == MessageType.LOCATION_SHARE) {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🆘 Acil Mesaj Alındı!")
                .setContentText("${message.senderAlias.ifEmpty { message.senderId }}: ${message.content.take(50)}")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notifManager.notify(message.hashCode(), notif)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshForegroundService::class.java))
        }
    }
}
