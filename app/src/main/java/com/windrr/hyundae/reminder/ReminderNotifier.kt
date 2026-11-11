package com.windrr.hyundae.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.windrr.hyundae.MainActivity
import com.windrr.hyundae.R
import com.windrr.hyundae.data.local.entity.PlanItemEntity

object ReminderNotifier {

    const val MONITORING_NOTIFICATION_ID = 1001
    private const val REMINDER_NOTIFICATION_ID = 1002

    private const val CHANNEL_MONITORING = "arrival_monitoring"
    private const val CHANNEL_REMINDER = "arrival_reminder"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_MONITORING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("도착 감지")
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_REMINDER, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("짐 챙기기 알림")
                    .build(),
            ),
        )
    }

    /** 포그라운드 서비스 유지용 알림 */
    fun monitoringNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("도착하면 짐 챙기기를 알려드릴게요")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    @SuppressLint("MissingPermission") // 아래에서 직접 확인
    fun notifyArrival(context: Context, items: List<PlanItemEntity>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val summary = items.joinToString { if (it.quantity > 1) "${it.name} ${it.quantity}개" else it.name }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("오늘 실었던 물건을 모두 챙기셨나요?")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }
}
