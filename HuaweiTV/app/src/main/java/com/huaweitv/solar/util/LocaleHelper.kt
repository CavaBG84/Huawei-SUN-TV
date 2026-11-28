package com.huaweitv.solar.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.huaweitv.solar.data.model.AppLanguage
import com.huaweitv.solar.data.repository.SettingsRepository
import java.util.Locale

/**
 * Applies persisted manual [AppLanguage] to Activity and Application contexts across API 21..35.
 */
object LocaleHelper {

    fun wrapContext(base: Context): Context {
        val prefs = base.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(SettingsRepository.KEY_LANGUAGE_CODE, null)
        val language = AppLanguage.resolve(savedKey)
        return applyLanguage(base, language.code)
    }

    fun applyLanguage(context: Context, languageCode: String): Context {
        val language = AppLanguage.resolve(languageCode)
        return applyLanguage(context, language)
    }

    @Suppress("DEPRECATION")
    fun applyLanguage(context: Context, language: AppLanguage): Context {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Locale.forLanguageTag(language.code)
        } else {
            Locale(language.code)
        }
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.updateConfiguration(config, resources.displayMetrics)
            context.createConfigurationContext(config)
        } else {
            resources.updateConfiguration(config, resources.displayMetrics)
            context
        }
    }
}
