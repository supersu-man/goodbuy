package dev.sumanth.goodbuy.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.sumanth.goodbuy.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "price_drops_channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Price Drop Alerts"
            val descriptionText = "Notifications when tracked product prices drop"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPriceDropNotification(
        context: Context,
        productTitle: String,
        oldPrice: Double,
        newPrice: Double,
        productId: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            productId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val savings = oldPrice - newPrice
        val percent = if (oldPrice > 0) ((savings / oldPrice) * 100).toInt() else 0
        val contentText = if (percent > 0) {
            "Price dropped by $percent%! Now ₹$newPrice (was ₹$oldPrice)"
        } else {
            "Price dropped to ₹$newPrice (was ₹$oldPrice)"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("📉 Price Drop Alert!")
            .setContentText(productTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$productTitle\n$contentText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(productId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
