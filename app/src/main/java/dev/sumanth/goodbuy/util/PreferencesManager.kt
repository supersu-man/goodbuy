package dev.sumanth.goodbuy.util

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.sumanth.goodbuy.worker.PriceCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

object PreferencesManager {
    private const val PREFS_NAME = "goodbuy_preferences"
    private const val KEY_BACKGROUND_PRICE_CHECK = "background_price_check_enabled"

    private var sharedPreferences: SharedPreferences? = null

    private val _isBackgroundCheckEnabled = MutableStateFlow(true)
    val isBackgroundCheckEnabled: StateFlow<Boolean> = _isBackgroundCheckEnabled.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences = prefs
        val enabled = prefs.getBoolean(KEY_BACKGROUND_PRICE_CHECK, true)
        _isBackgroundCheckEnabled.value = enabled
    }

    fun setBackgroundCheckEnabled(context: Context, enabled: Boolean) {
        val prefs = sharedPreferences ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { sharedPreferences = it }
        prefs.edit().putBoolean(KEY_BACKGROUND_PRICE_CHECK, enabled).apply()
        _isBackgroundCheckEnabled.value = enabled

        val appContext = context.applicationContext
        if (enabled) {
            schedulePriceCheck(appContext)
        } else {
            cancelPriceCheck(appContext)
        }
    }

    fun schedulePriceCheckIfEnabled(context: Context) {
        init(context)
        val appContext = context.applicationContext
        if (_isBackgroundCheckEnabled.value) {
            schedulePriceCheck(appContext)
        } else {
            cancelPriceCheck(appContext)
        }
    }

    private fun schedulePriceCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "GoodBuyPriceCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    private fun cancelPriceCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("GoodBuyPriceCheckWork")
    }
}
