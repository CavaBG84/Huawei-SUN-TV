package com.huaweitv.solar.data.model

/**
 * Application configuration persisted in SharedPreferences.
 */
data class AppSettings(
    val inverterBrand: InverterBrand = InverterBrand.HUAWEI,
    val ipAddress: String = "",
    val port: Int = 502,
    val unitId: Int = 1,
    val froniusDeviceId: Int = 1,
    val pollingIntervalSeconds: Int = 10,
    val mockMode: Boolean = false,
    val languageCode: String = AppLanguage.ITALIAN.code,
    // Meter / Contract power alarm
    val meterAlarmEnabled: Boolean = false,
    val contractPowerKw: Double = 3.0,
    val meterAlarmSoundEnabled: Boolean = true,
    // Shelly local load shedding
    val shellySheddingEnabled: Boolean = false,
    val shelly1Name: String = "",
    val shelly1Ip: String = "",
    val shelly1Channel: Int = 0,
    val shelly1Gen: ShellyGen = ShellyGen.GEN2,
    val shellyTriggerDelaySec: Int = 15
) {
    val appLanguage: AppLanguage
        get() = AppLanguage.fromKey(languageCode) ?: AppLanguage.ITALIAN

    fun effectivePort(): Int = if (inverterBrand == InverterBrand.FRONIUS) {
        InverterBrand.FRONIUS.defaultPort
    } else if (port in 1..65535) {
        port
    } else {
        inverterBrand.defaultPort
    }
}
