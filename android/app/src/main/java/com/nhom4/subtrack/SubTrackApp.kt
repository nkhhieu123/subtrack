package com.nhom4.subtrack

import android.app.Application
import com.nhom4.subtrack.data.RatesRepository
import com.nhom4.subtrack.data.SettingsRepository
import com.nhom4.subtrack.data.DatasetRegistry
import com.nhom4.subtrack.data.SubscriptionRepository
import com.nhom4.subtrack.reminder.Reminders

/** Giữ các đối tượng dùng chung cho toàn ứng dụng. */
class SubTrackApp : Application() {
    lateinit var repository: SubscriptionRepository
        private set
    lateinit var datasets: DatasetRegistry
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var rates: RatesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        datasets = DatasetRegistry(this)
        repository = datasets.repository
        settings = SettingsRepository(this)
        rates = RatesRepository(this)
        Reminders.createChannel(this)
        if (settings.settings.value.remindersEnabled) Reminders.schedule(this)
    }

    /** Chuyển bộ dữ liệu rồi kiểm tra nhắc hạn ngay cho bộ mới. */
    suspend fun switchDataset(id: String) {
        datasets.switchTo(id)
        if (settings.settings.value.remindersEnabled) Reminders.checkNow(this)
    }
}
