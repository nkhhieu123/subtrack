package com.nhom4.subtrack

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nhom4.subtrack.ui.SubTrackRoot
import com.nhom4.subtrack.ui.VietnameseLocale
import com.nhom4.subtrack.ui.theme.SubTrackTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Luôn hiển thị tiếng Việt, kể cả khi điện thoại đặt ngôn ngữ khác (DatePicker, định dạng ngày…). */
    override fun attachBaseContext(newBase: Context) {
        Locale.setDefault(VietnameseLocale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(VietnameseLocale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private var openSubscriptionId by mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenExtra(intent)
    }

    private fun readOpenExtra(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_SUBSCRIPTION, -1L) ?: -1L
        if (id > 0) openSubscriptionId = id
        // Chỉ dùng một lần: xoay màn hình / tạo lại activity không điều hướng lại.
        intent?.removeExtra(EXTRA_OPEN_SUBSCRIPTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readOpenExtra(intent)
        enableEdgeToEdge()
        setContent {
            SubTrackTheme {
                SubTrackRoot(openSubscriptionId, onOpenedSubscription = { openSubscriptionId = null })
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SUBSCRIPTION = "open_subscription_id"
    }
}
