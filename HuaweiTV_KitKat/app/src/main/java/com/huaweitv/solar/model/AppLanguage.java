package com.huaweitv.solar.model;

import java.util.Locale;

/**
 * Supported application UI languages persisted under SharedPreferences key `app_language`.
 */
public enum AppLanguage {
    ITALIAN("it", "Italiano", "IT"),
    ENGLISH("en", "English", "EN"),
    GERMAN("de", "Deutsch", "DE"),
    FRENCH("fr", "Français", "FR");

    public final String code;
    public final String nativeLabel;
    public final String shortCode;

    AppLanguage(String code, String nativeLabel, String shortCode) {
        this.code = code;
        this.nativeLabel = nativeLabel;
        this.shortCode = shortCode;
    }

    public Locale toLocale() {
        return new Locale(code);
    }

    public static AppLanguage fromKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        int dashIdx = normalized.indexOf('-');
        if (dashIdx > 0) {
            normalized = normalized.substring(0, dashIdx);
        }
        int underscoreIdx = normalized.indexOf('_');
        if (underscoreIdx > 0) {
            normalized = normalized.substring(0, underscoreIdx);
        }
        for (AppLanguage lang : values()) {
            if (lang.code.equals(normalized)) {
                return lang;
            }
        }
        return null;
    }

    public static AppLanguage fromLocale(Locale locale) {
        if (locale == null || locale.getLanguage() == null) {
            return ITALIAN;
        }
        String lang = locale.getLanguage().trim().toLowerCase(Locale.ROOT);
        for (AppLanguage item : values()) {
            if (item.code.equals(lang)) {
                return item;
            }
        }
        return ITALIAN;
    }

    public static AppLanguage resolve(String savedKey, Locale systemLocale) {
        AppLanguage saved = fromKey(savedKey);
        return saved != null ? saved : fromLocale(systemLocale);
    }
}
