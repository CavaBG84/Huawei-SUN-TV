package com.huaweitv.solar.data.coffee

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeePromptManagerTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            if (key != null && data.containsKey(key)) data[key] as? String else defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = key != null && data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
        }
    }

    @Test
    fun testThreeDayScheduleSnoozeAndPermanentDismissal() {
        val prefs = InMemorySharedPreferences()
        val day0 = 1_700_000_000_000L
        val threeDays = CoffeePromptManager.THREE_DAYS_MILLIS

        // First launch: initializes coffee_first_seen_ts and returns false
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0))
        assertEquals(day0, prefs.getLong(CoffeePromptManager.KEY_FIRST_SEEN_TS, 0L))
        assertFalse(prefs.getBoolean(CoffeePromptManager.KEY_DISMISSED_FOREVER, false))

        // Before 3 days: false
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 + threeDays - 1_000L))

        // At 3 days: true
        assertTrue(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 + threeDays))

        // Close with X / BACK -> snooze sets coffee_last_shown_ts
        val snoozedAt = day0 + threeDays + 5_000L
        CoffeePromptManager.recordSnoozed(prefs, snoozedAt)
        assertEquals(snoozedAt, prefs.getLong(CoffeePromptManager.KEY_LAST_SHOWN_TS, 0L))
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays - 1L))
        assertTrue(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays))

        // Confirmed forever -> never shows again
        CoffeePromptManager.recordDismissedForever(prefs)
        assertTrue(prefs.getBoolean(CoffeePromptManager.KEY_DISMISSED_FOREVER, false))
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays * 20L))
    }

    @Test
    fun testClockRollbackDoesNotShowPrompt() {
        val prefs = InMemorySharedPreferences()
        val day0 = 1_700_000_000_000L
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0))
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 - 10_000L))
    }

    @Test
    fun testQrCodeEncoderForPayPalUrlProducesValidVersion3Matrix() {
        val matrix = QrCodeEncoder.encode(CoffeePromptManager.PAYPAL_URL)
        assertEquals(29, matrix.size)
        matrix.forEach { row -> assertEquals(29, row.size) }

        // Top-left 7x7 finder outer ring and center 3x3
        for (i in 0 until 7) {
            assertTrue(matrix[0][i])
            assertTrue(matrix[6][i])
            assertTrue(matrix[i][0])
            assertTrue(matrix[i][6])
        }
        assertTrue(matrix[3][3])
        assertFalse(matrix[1][1])

        // Separators around top-left finder
        for (i in 0 until 8) {
            assertFalse(matrix[7][i])
            assertFalse(matrix[i][7])
        }

        // Top-right & bottom-left finder centers
        assertTrue(matrix[3][25])
        assertTrue(matrix[25][3])

        // Timing pattern & dark module (21, 8) & alignment center (22, 22)
        assertTrue(matrix[6][8])
        assertFalse(matrix[6][9])
        assertTrue(matrix[21][8])
        assertTrue(matrix[22][22])
        assertFalse(matrix[21][22])
    }
}
