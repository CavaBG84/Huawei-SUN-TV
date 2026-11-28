package com.huaweitv.solar.data.model

/**
 * Shelly device generation persisted under SharedPreferences key `shelly_1_gen`.
 */
enum class ShellyGen(
    val prefValue: String,
    val displayName: String,
    val shortLabel: String
) {
    GEN1(
        prefValue = "GEN1",
        displayName = "GEN1 (Shelly 1/1PM/2.5/EM)",
        shortLabel = "GEN1"
    ),
    GEN2(
        prefValue = "GEN2",
        displayName = "GEN2 (Shelly Plus/Pro)",
        shortLabel = "GEN2"
    );

    companion object {
        fun fromKey(key: String?): ShellyGen {
            if (key.isNullOrBlank()) return GEN2
            return if (key.trim().equals("GEN1", ignoreCase = true)) GEN1 else GEN2
        }
    }
}
