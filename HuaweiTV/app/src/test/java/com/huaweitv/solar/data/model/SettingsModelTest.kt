package com.huaweitv.solar.data.model

import android.content.SharedPreferences
import com.huaweitv.solar.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsModelTest {

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
    fun testDefaultAppSettings() {
        val settings = AppSettings()
        assertEquals(InverterBrand.HUAWEI, settings.inverterBrand)
        assertEquals(502, settings.port)
        assertEquals(1, settings.unitId)
        assertEquals(1, settings.froniusDeviceId)
        assertEquals(10, settings.pollingIntervalSeconds)
        assertFalse(settings.mockMode)
        assertEquals("it", settings.languageCode)
        assertEquals(AppLanguage.ITALIAN, settings.appLanguage)

        assertFalse(settings.meterAlarmEnabled)
        assertEquals(3.0, settings.contractPowerKw, 0.001)
        assertTrue(settings.meterAlarmSoundEnabled)

        assertFalse(settings.shellySheddingEnabled)
        assertEquals("", settings.shelly1Name)
        assertEquals("", settings.shelly1Ip)
        assertEquals(0, settings.shelly1Channel)
        assertEquals(ShellyGen.GEN2, settings.shelly1Gen)
        assertEquals(15, settings.shellyTriggerDelaySec)
    }

    @Test
    fun testEffectivePortPerBrand() {
        val huaweiCustom = AppSettings(inverterBrand = InverterBrand.HUAWEI, port = 6607)
        assertEquals(6607, huaweiCustom.effectivePort())

        val fronius = AppSettings(inverterBrand = InverterBrand.FRONIUS, port = 6607)
        assertEquals(80, fronius.effectivePort())
    }

    @Test
    fun testShellyGenFromKey() {
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey(null))
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey(""))
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey("GEN2"))
        assertEquals(ShellyGen.GEN1, ShellyGen.fromKey("GEN1"))
        assertEquals(ShellyGen.GEN1, ShellyGen.fromKey("gen1"))
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey("UNKNOWN"))
    }

    @Test
    fun testAppLanguageResolution() {
        assertNull(AppLanguage.fromKey(null))
        assertNull(AppLanguage.fromKey(""))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromKey("it"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromKey("EN_US"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromKey("de-DE"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromKey("fr"))
        assertNull(AppLanguage.fromKey("es"))

        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.US))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLocale(Locale.GERMANY))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLocale(Locale.FRANCE))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromLocale(Locale("es", "ES")))

        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve("fr", Locale.US))
        assertEquals(AppLanguage.GERMAN, AppLanguage.resolve(null, Locale.GERMANY))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.resolve("unknown", Locale("es")))
    }

    @Test
    fun testSaveAndLoadLanguageCodeInPreferences() {
        val prefs = InMemorySharedPreferences()
        listOf("it", "en", "de", "fr").forEach { code ->
            SettingsRepository.saveToPreferences(prefs, AppSettings(languageCode = code))
            val loaded = SettingsRepository.loadFromPreferences(prefs)
            assertEquals(code, loaded.languageCode)
            assertEquals(code, loaded.appLanguage.code)
        }
    }
}
