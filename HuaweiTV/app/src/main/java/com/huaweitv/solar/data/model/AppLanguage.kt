package com.huaweitv.solar.data.model

import java.util.Locale

/**
 * Supported application UI languages persisted under SharedPreferences key `app_language`.
 */
enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val shortCode: String
) {
    ITALIAN(code = "it", nativeLabel = "Italiano", shortCode = "IT"),
    ENGLISH(code = "en", nativeLabel = "English", shortCode = "EN"),
    GERMAN(code = "de", nativeLabel = "Deutsch", shortCode = "DE"),
    FRENCH(code = "fr", nativeLabel = "Français", shortCode = "FR");

    fun toLocale(): Locale = Locale(code)

    companion object {
        fun fromKey(key: String?): AppLanguage? {
            if (key.isNullOrBlank()) return null
            val normalized = key.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.code == normalized }
        }

        fun fromLocale(locale: Locale?): AppLanguage {
            val lang = locale?.language?.trim()?.lowercase(Locale.ROOT) ?: return ITALIAN
            return entries.firstOrNull { it.code == lang } ?: ITALIAN
        }

        fun resolve(savedKey: String?, systemLocale: Locale = Locale.getDefault()): AppLanguage {
            return fromKey(savedKey) ?: fromLocale(systemLocale)
        }
    }
}
