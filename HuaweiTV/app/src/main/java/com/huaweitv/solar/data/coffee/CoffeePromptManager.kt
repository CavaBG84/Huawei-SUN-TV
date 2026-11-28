package com.huaweitv.solar.data.coffee

import android.content.Context
import android.content.SharedPreferences
import com.huaweitv.solar.data.repository.SettingsRepository

/**
 * Manages the 3-day periodic voluntary "Offrimi un caffè" (PayPal) prompt state.
 */
class CoffeePromptManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)

    fun shouldShowCoffeePrompt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return shouldShowCoffeePrompt(prefs, nowMillis)
    }

    fun recordSnoozed(nowMillis: Long = System.currentTimeMillis()) {
        recordSnoozed(prefs, nowMillis)
    }

    fun recordDismissedForever() {
        recordDismissedForever(prefs)
    }

    companion object {
        const val THREE_DAYS_MILLIS: Long = 3L * 24L * 60L * 60L * 1000L
        const val PAYPAL_URL: String = "https://paypal.me/cavalleridaniele"

        const val KEY_FIRST_SEEN_TS = "coffee_first_seen_ts"
        const val KEY_LAST_SHOWN_TS = "coffee_last_shown_ts"
        const val KEY_DISMISSED_FOREVER = "coffee_dismissed_forever"

        internal fun shouldShowCoffeePrompt(prefs: SharedPreferences, nowMillis: Long): Boolean {
            if (prefs.getBoolean(KEY_DISMISSED_FOREVER, false)) {
                return false
            }

            val firstSeenTs = prefs.getLong(KEY_FIRST_SEEN_TS, 0L)
            if (firstSeenTs <= 0L) {
                prefs.edit().putLong(KEY_FIRST_SEEN_TS, nowMillis).apply()
                return false
            }

            val lastShownTs = prefs.getLong(KEY_LAST_SHOWN_TS, 0L)
            val refTs = if (lastShownTs > 0L) lastShownTs else firstSeenTs
            if (nowMillis < refTs) {
                return false
            }

            return (nowMillis - refTs) >= THREE_DAYS_MILLIS
        }

        internal fun recordSnoozed(prefs: SharedPreferences, nowMillis: Long) {
            prefs.edit().putLong(KEY_LAST_SHOWN_TS, nowMillis).apply()
        }

        internal fun recordDismissedForever(prefs: SharedPreferences) {
            prefs.edit().putBoolean(KEY_DISMISSED_FOREVER, true).apply()
        }
    }
}
