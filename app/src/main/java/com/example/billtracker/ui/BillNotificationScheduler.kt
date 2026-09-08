package com.example.billtracker.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.billtracker.MainActivity
import com.example.billtracker.R
import com.example.billtracker.data.Biller
import com.example.billtracker.data.Recurrence
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "bill_due"
private const val ACTION_NOTIFY = "com.example.billtracker.NOTIFY_BILL"
private const val EXTRA_ID = "bill_id"
private const val EXTRA_NAME = "bill_name"
private const val EXTRA_AMOUNT = "bill_amount"
private const val EXTRA_DUE = "bill_due"

class BillNotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(bills: List<Biller>) {
        bills.forEach { schedule(it) }
    }

    fun schedule(bill: Biller) {
        cancel(bill.id)
        if (bill.isPaid) return
        val date = runCatching { LocalDate.parse(bill.dueDate) }.getOrNull() ?: return
        val notifyDate = date.minusDays(1)
        val trigger = notifyDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return

        val intent = Intent(context, BillNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra(EXTRA_ID, bill.id)
            putExtra(EXTRA_NAME, bill.name)
            putExtra(EXTRA_AMOUNT, bill.amount ?: Double.NaN)
            putExtra(EXTRA_DUE, date.toString())
        }
        val pending = PendingIntent.getBroadcast(
            context, bill.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    fun cancel(id: Long) {
        val intent = Intent(context, BillNotificationReceiver::class.java).apply { action = ACTION_NOTIFY }
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }
}

class BillNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOTIFY) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val name = intent.getStringExtra(EXTRA_NAME) ?: "Bill"
        val due = intent.getStringExtra(EXTRA_DUE) ?: ""
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, Double.NaN)
        val amountText = if (amount.isNaN()) "" else " ${String.format("$%,.2f", amount)}"

        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("$name is due tomorrow")
            .setContentText("$name$amountText is due $due. Tap to open Bill Tracker.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name$amountText is due tomorrow ($due)."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        NotificationManagerCompat.from(context).notify(intent.getLongExtra(EXTRA_ID, 0).toInt(), notification)
    }

    companion object {
        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Bill reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Reminders for bills due tomorrow" }
            )
        }
    }
}


class BillBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = com.example.billtracker.data.BillerDatabase.getInstance(context)
                BillNotificationScheduler(context).scheduleAll(db.billerDao().getAllOnce())
            } finally {
                pending.finish()
            }
        }
    }
}
