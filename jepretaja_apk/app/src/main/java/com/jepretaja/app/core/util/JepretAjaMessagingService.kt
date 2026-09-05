package com.jepretaja.app.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jepretaja.app.MainActivity
import com.jepretaja.app.R

/**
 * Didaftarkan di AndroidManifest tapi sebelumnya belum ada implementasinya —
 * itu membuat proses crash (ClassNotFoundException) setiap kali FCM mencoba
 * bind ke service ini, biasanya tak lama setelah app dibuka, memutus apa pun
 * yang sedang dikerjakan user (termasuk upload creator yang sedang berjalan).
 *
 * Terima push notification dan simpan token FCM terbaru ke Firestore supaya
 * Cloud Functions backend bisa mengirim notifikasi ke user ini.
 */
class JepretAjaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "JepretAja"
        val body = message.notification?.body ?: message.data["body"] ?: return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Notifikasi JepretAja", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "jepretaja_default"
    }
}
