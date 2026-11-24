package com.nhom4.subtrack.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nhom4.subtrack.MainActivity
import com.nhom4.subtrack.R
import com.nhom4.subtrack.SubTrackApp
import com.nhom4.subtrack.data.Subscription
import com.nhom4.subtrack.data.SubscriptionStatus
import com.nhom4.subtrack.domain.BillingMath
import com.nhom4.subtrack.ui.formatDate
import com.nhom4.subtrack.ui.formatMoney
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Giờ nhắc cố định trong ngày. */
object ReminderTime {
    /** Lần nhắc hằng ngày chạy lúc 8:00 (WorkManager có thể trễ vài phút, thường trong khoảng 8–9 giờ). */
    const val HOUR = 8

    /** Kiểm tra ngay (sau khi lưu gói, bật nhắc) chỉ gửi trước giờ này; muộn hơn thì để 8 giờ sáng mai. */
    const val QUIET_FROM_HOUR = 22

    /** Thời gian chờ tới [HOUR]:00 kế tiếp: hôm nay nếu chưa tới giờ, không thì sáng mai. */
    fun delayUntilNext(now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** Có được gửi thông báo ngay lúc [time] không (ngoài khung 8:00–21:59 thì không, tránh báo lúc nửa đêm). */
    fun canNotifyNow(time: LocalTime): Boolean = time.hour in HOUR until QUIET_FROM_HOUR
}

/**
 * Gửi thông báo cho gói đang trong khoảng nhắc trước hạn.
 * - Bản hằng ngày ([KEY_DAILY] = true) chạy lúc 8 giờ sáng và tự hẹn lần chạy sáng hôm sau.
 * - Bản kiểm tra ngay chỉ gửi trong khung giờ cho phép ([ReminderTime.canNotifyNow]).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SubTrackApp
        if (!app.settings.settings.value.remindersEnabled) return Result.success()
        val daily = inputData.getBoolean(KEY_DAILY, false)
        // Hẹn trước lần 8 giờ sáng mai (nối sau lần đang chạy) để lỗi ở dưới cũng không làm mất lịch
        if (daily) Reminders.scheduleNextDaily(applicationContext)
        else if (!ReminderTime.canNotifyNow(LocalTime.now())) return Result.success()
        return runCatching {
            app.repository.refresh()
            val today = LocalDate.now()
            app.repository.subscriptions.value.orEmpty()
                .filter { shouldRemind(it, today) }
                .forEach {
                    // Chỉ ghi nhận khi gửi được; thiếu quyền thì lần kiểm tra sau sẽ thử lại
                    if (Reminders.notifyDue(applicationContext, it, BillingMath.daysUntil(it.nextPaymentDate, today))) {
                        app.repository.markNotified(it.id, today)
                    }
                }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val KEY_DAILY = "daily"

        fun shouldRemind(sub: Subscription, today: LocalDate): Boolean {
            val before = sub.remindDaysBefore ?: return false
            if (sub.status != SubscriptionStatus.ACTIVE) return false
            val days = BillingMath.daysUntil(sub.nextPaymentDate, today)
            val last = sub.lastNotifiedOn
            val snooze = sub.snoozedUntil
            if (days > before || last == today) return false
            if (snooze != null && snooze > today) return false
            return last == null || days == 0L || snooze == today ||
                (days < 0 && ChronoUnit.DAYS.between(last, today) >= 3)
        }
    }
}

object Reminders {
    private const val CHANNEL_ID = "payment_reminders"
    /** Lịch cũ (PeriodicWork 24 giờ, giờ chạy trôi theo lúc cài app); chỉ còn dùng để hủy. */
    private const val LEGACY_PERIODIC_WORK = "daily_reminders"
    private const val DAILY_WORK = "daily_reminders_8am"
    private const val ONE_TIME_WORK = "check_reminders_now"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun dailyRequest() = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(ReminderTime.delayUntilNext(LocalDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
        .setInputData(workDataOf(ReminderWorker.KEY_DAILY to true))
        .build()

    /** Hẹn lần nhắc 8 giờ sáng kế tiếp; đã có lịch thì giữ nguyên. Gọi khi mở app và khi bật nhắc. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_PERIODIC_WORK)
        wm.enqueueUniqueWork(DAILY_WORK, ExistingWorkPolicy.KEEP, dailyRequest())
    }

    /** Gọi từ lần chạy hằng ngày: nối lần 8 giờ sáng hôm sau vào sau lần đang chạy. */
    fun scheduleNextDaily(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(DAILY_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, dailyRequest())
    }

    /** Kiểm tra ngay, ví dụ sau khi lưu một gói sắp đến hạn. */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(DAILY_WORK)
        wm.cancelUniqueWork(LEGACY_PERIODIC_WORK)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    enum class NotifText { TODAY, UPCOMING, OVERDUE }

    fun textKind(days: Long): NotifText = when {
        days == 0L -> NotifText.TODAY
        days < 0 -> NotifText.OVERDUE
        else -> NotifText.UPCOMING
    }

    /** Gỡ thông báo của gói khỏi khay sau khi người dùng đã xử lý trong ứng dụng. */
    fun cancelFor(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())

    /** Trả về true nếu thông báo đã được gửi cho hệ thống. */
    fun notifyDue(context: Context, sub: Subscription, days: Long): Boolean {
        val money = formatMoney(sub.amount, sub.currency)
        val date = formatDate(sub.nextPaymentDate)
        val (title, body) = when (textKind(days)) {
            NotifText.TODAY -> context.getString(R.string.notif_title_today, sub.name) to context.getString(R.string.notif_body_today, money)
            NotifText.OVERDUE -> context.getString(R.string.notif_title_overdue, sub.name) to context.getString(R.string.notif_body_overdue, money, date)
            NotifText.UPCOMING -> context.getString(R.string.notif_title, sub.name) to
                context.resources.getQuantityString(R.plurals.notif_body, days.toInt(), money, days.toInt(), date)
        }
        return post(context, sub.id.toInt(), title, body, sub)
    }

    fun notifyTest(context: Context): Boolean =
        post(context, 0, context.getString(R.string.notif_test_title), context.getString(R.string.notif_test_body))

    private const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun action(context: Context, sub: Subscription, action: String, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, sub.id.toInt() * 3 + slot,
            Intent(context, PaymentActionReceiver::class.java).setAction(action)
                .putExtra(PaymentActionReceiver.EXTRA_SUB_ID, sub.id)
                .putExtra(PaymentActionReceiver.EXTRA_PERIOD, sub.nextPaymentDate.toEpochDay()),
            PI_FLAGS,
        )

    @SuppressLint("MissingPermission") // đã kiểm tra bằng hasPermission ngay bên dưới
    private fun post(context: Context, id: Int, title: String, body: String, sub: Subscription? = null): Boolean {
        if (!hasPermission(context)) return false
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        sub?.let { open.putExtra(MainActivity.EXTRA_OPEN_SUBSCRIPTION, it.id) }
        val intent = PendingIntent.getActivity(context, (sub?.id?.toInt() ?: 0) * 3, open, PI_FLAGS)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
        if (sub != null) {
            builder.addAction(0, context.getString(R.string.notif_action_paid), action(context, sub, PaymentActionReceiver.ACTION_PAID, 1))
            builder.addAction(0, context.getString(R.string.notif_action_snooze), action(context, sub, PaymentActionReceiver.ACTION_SNOOZE, 2))
        }
        val notification = builder.build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.isSuccess
    }
}
