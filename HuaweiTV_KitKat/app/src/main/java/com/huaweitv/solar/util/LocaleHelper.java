package com.huaweitv.solar.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import com.huaweitv.solar.model.AppLanguage;
import com.huaweitv.solar.repository.SettingsRepository;

import java.util.Locale;

/**
 * Applies persisted manual {@link AppLanguage} across Android 4.4.4 KitKat (API 19) through Android 15.
 */
public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context wrapContext(Context base) {
        if (base == null) return null;
        SharedPreferences prefs = base.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(SettingsRepository.KEY_LANGUAGE_CODE, null);
        AppLanguage language = AppLanguage.resolve(savedKey, Locale.getDefault());
        return applyLanguage(base, language);
    }

    public static Context applyLanguage(Context context, String languageCode) {
        AppLanguage language = AppLanguage.resolve(languageCode, Locale.getDefault());
        return applyLanguage(context, language);
    }

    @SuppressWarnings("deprecation")
    public static Context applyLanguage(Context context, AppLanguage language) {
        if (context == null || language == null) return context;
        Locale locale = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            ? Locale.forLanguageTag(language.code)
            : new Locale(language.code);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
        } else {
            config.locale = locale;
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createConfigurationContext(config);
        }
        return context;
    }
}
