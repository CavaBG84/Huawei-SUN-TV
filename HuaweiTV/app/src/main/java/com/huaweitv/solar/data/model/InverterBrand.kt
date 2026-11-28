package com.huaweitv.solar.data.model

/**
 * Supported inverter brands persisted under SharedPreferences key `inverter_brand`.
 */
enum class InverterBrand(
    val prefValue: String,
    val displayName: String,
    val shortLabel: String,
    val subtitle: String,
    val defaultPort: Int,
    val requiresUnitId: Boolean
) {
    HUAWEI(
        prefValue = "HUAWEI",
        displayName = "Huawei SUN2000 (Modbus TCP)",
        shortLabel = "Huawei SUN2000 (Modbus TCP)",
        subtitle = "SUN2000 + LUNA2000",
        defaultPort = 502,
        requiresUnitId = true
    ),
    FRONIUS(
        prefValue = "FRONIUS",
        displayName = "Fronius (Solar API locale)",
        shortLabel = "Fronius (Solar API locale)",
        subtitle = "FRONIUS SOLAR API v1",
        defaultPort = 80,
        requiresUnitId = false
    );

    companion object {
        fun fromKey(key: String?): InverterBrand {
            if (key.isNullOrBlank()) return HUAWEI
            val normalized = key.trim().uppercase()
            return if (normalized == "FRONIUS" || normalized == "FRONIUS_SOLAR_API") {
                FRONIUS
            } else {
                HUAWEI
            }
        }
    }
}
