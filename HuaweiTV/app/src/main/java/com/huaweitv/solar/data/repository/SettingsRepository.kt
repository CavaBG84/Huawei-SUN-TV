package com.huaweitv.solar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.huaweitv.solar.data.model.AppLanguage
import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.ShellyGen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings repository using native Android SharedPreferences.
 * Preserves existing v1.x keys/values in-place while supporting Phase 2 & Phase 3 fields.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_NAME = "huawei_solar_tv_prefs"
        const val KEY_INVERTER_BRAND = "inverter_brand"
        const val KEY_IP = "inverter_ip"
        const val KEY_PORT = "modbus_port"
        const val KEY_UNIT_ID = "unit_id"
        const val KEY_FRONIUS_DEVICE_ID = "fronius_device_id"
        const val KEY_POLL_INTERVAL = "poll_interval"
        const val KEY_MOCK_MODE = "mock_mode"
        const val KEY_LANGUAGE_CODE = "language_code"

        const val KEY_METER_ALARM_ENABLED = "meter_alarm_enabled"
        const val KEY_CONTRACT_POWER_KW = "contract_power_kw"
        const val KEY_METER_ALARM_SOUND_ENABLED = "meter_alarm_sound_enabled"

        const val KEY_SHELLY_SHEDDING_ENABLED = "shelly_shedding_enabled"
        const val KEY_SHELLY_1_NAME = "shelly_1_name"
        const val KEY_SHELLY_1_IP = "shelly_1_ip"
        const val KEY_SHELLY_1_CHANNEL = "shelly_1_channel"
        const val KEY_SHELLY_1_GEN = "shelly_1_gen"
        const val KEY_SHELLY_TRIGGER_DELAY_SEC = "shelly_trigger_delay_sec"

        internal fun loadFromPreferences(prefs: SharedPreferences): AppSettings {
            val brand = InverterBrand.fromKey(prefs.getString(KEY_INVERTER_BRAND, InverterBrand.HUAWEI.prefValue))
            val port = if (prefs.contains(KEY_PORT)) prefs.getInt(KEY_PORT, 502) else 502
            val unitId = if (prefs.contains(KEY_UNIT_ID)) prefs.getInt(KEY_UNIT_ID, 1) else 1
            val contractKw = if (prefs.contains(KEY_CONTRACT_POWER_KW)) {
                prefs.getFloat(KEY_CONTRACT_POWER_KW, 3.0f).toDouble()
            } else {
                3.0
            }
            val languageCode = AppLanguage.resolve(prefs.getString(KEY_LANGUAGE_CODE, null)).code
            return AppSettings(
                inverterBrand = brand,
                ipAddress = prefs.getString(KEY_IP, "") ?: "",
                port = port,
                unitId = unitId,
                froniusDeviceId = prefs.getInt(KEY_FRONIUS_DEVICE_ID, 1).coerceAtLeast(0),
                pollingIntervalSeconds = prefs.getInt(KEY_POLL_INTERVAL, 10),
                mockMode = prefs.getBoolean(KEY_MOCK_MODE, false),
                languageCode = languageCode,
                meterAlarmEnabled = prefs.getBoolean(KEY_METER_ALARM_ENABLED, false),
                contractPowerKw = contractKw,
                meterAlarmSoundEnabled = prefs.getBoolean(KEY_METER_ALARM_SOUND_ENABLED, true),
                shellySheddingEnabled = prefs.getBoolean(KEY_SHELLY_SHEDDING_ENABLED, false),
                shelly1Name = prefs.getString(KEY_SHELLY_1_NAME, "") ?: "",
                shelly1Ip = prefs.getString(KEY_SHELLY_1_IP, "") ?: "",
                shelly1Channel = prefs.getInt(KEY_SHELLY_1_CHANNEL, 0).coerceAtLeast(0),
                shelly1Gen = ShellyGen.fromKey(prefs.getString(KEY_SHELLY_1_GEN, ShellyGen.GEN2.prefValue)),
                shellyTriggerDelaySec = prefs.getInt(KEY_SHELLY_TRIGGER_DELAY_SEC, 15).coerceAtLeast(1)
            )
        }

        internal fun saveToPreferences(prefs: SharedPreferences, settings: AppSettings) {
            prefs.edit()
                .putString(KEY_INVERTER_BRAND, settings.inverterBrand.prefValue)
                .putString(KEY_IP, settings.ipAddress)
                .putInt(KEY_PORT, settings.port)
                .putInt(KEY_UNIT_ID, settings.unitId)
                .putInt(KEY_FRONIUS_DEVICE_ID, settings.froniusDeviceId)
                .putInt(KEY_POLL_INTERVAL, settings.pollingIntervalSeconds)
                .putBoolean(KEY_MOCK_MODE, settings.mockMode)
                .putString(KEY_LANGUAGE_CODE, settings.appLanguage.code)
                .putBoolean(KEY_METER_ALARM_ENABLED, settings.meterAlarmEnabled)
                .putFloat(KEY_CONTRACT_POWER_KW, settings.contractPowerKw.toFloat())
                .putBoolean(KEY_METER_ALARM_SOUND_ENABLED, settings.meterAlarmSoundEnabled)
                .putBoolean(KEY_SHELLY_SHEDDING_ENABLED, settings.shellySheddingEnabled)
                .putString(KEY_SHELLY_1_NAME, settings.shelly1Name)
                .putString(KEY_SHELLY_1_IP, settings.shelly1Ip)
                .putInt(KEY_SHELLY_1_CHANNEL, settings.shelly1Channel)
                .putString(KEY_SHELLY_1_GEN, settings.shelly1Gen.prefValue)
                .putInt(KEY_SHELLY_TRIGGER_DELAY_SEC, settings.shellyTriggerDelaySec)
                .apply()
        }
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun loadSettings(): AppSettings = loadFromPreferences(prefs)

    fun updateSettings(settings: AppSettings) {
        saveToPreferences(prefs, settings)
        _settingsFlow.value = settings
    }

    fun setMockMode(enabled: Boolean) {
        val current = _settingsFlow.value
        updateSettings(current.copy(mockMode = enabled))
    }
}
