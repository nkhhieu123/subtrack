package com.nhom4.subtrack

import android.app.Application
import com.nhom4.subtrack.data.SubTrackDatabase
import com.nhom4.subtrack.data.SubscriptionRepository

/**
 * Lớp Application: chạy đầu tiên khi mở app, sống suốt vòng đời app.
 * Tạo DUY NHẤT một Database và một Repository để mọi màn hình dùng chung một nguồn dữ liệu.
 */
class SubTrackApp : Application() {
    lateinit var repository: SubscriptionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SubscriptionRepository(SubTrackDatabase(this))
    }
}
