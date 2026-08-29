package com.twasol.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TwasolFirebaseMessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "twasol_messages_channel"
    private val CHANNEL_NAME = "Twasol Messages"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // أرسل الـ token إلى الخادم بعد تسجيل الدخول من الـ UI
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            channel.description = "إخطارات الرسائل الواردة"
            channel.enableLights(true)
            channel.lightColor = Color.GREEN
            nm.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            remoteMessage.data.let { data ->
                putExtra("senderId", data["senderId"])
                putExtra("messageId", data["messageId"])
            }
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val title = remoteMessage.notification?.title ?: "رسالة جديدة"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["content"] ?: ""

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
