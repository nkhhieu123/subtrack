package com.nhom4.subtrack

import android.app.Application
import com.nhom4.subtrack.data.RatesRepository
import com.nhom4.subtrack.data.SettingsRepository
import com.nhom4.subtrack.data.SubTrackDatabase
import com.nhom4.subtrack.data.SubscriptionRepository
import com.nhom4.subtrack.reminder.Reminders

/** Giữ các đối tượng dùng chung cho toàn ứng dụng. */
class SubTrackApp : Application() {
    lateinit var repository: SubscriptionRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var rates: RatesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SubscriptionRepository(SubTrackDatabase(this))
        settings = SettingsRepository(this)
        rates = RatesRepository(this)
        Reminders.createChannel(this)
        if (settings.settings.value.remindersEnabled) Reminders.schedule(this)
    }
}
