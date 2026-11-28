package com.huaweitv.solar.coffee;

import android.content.Context;
import android.content.SharedPreferences;
import com.huaweitv.solar.repository.SettingsRepository;

/**
 * Manages the 3-day periodic voluntary "Offrimi un caffè" (PayPal) prompt state.
 */
public class CoffeePromptManager {

    public static final long THREE_DAYS_MILLIS = 3L * 24L * 60L * 60L * 1000L;
    public static final String PAYPAL_URL = "https://paypal.me/cavalleridaniele";

    public static final String KEY_FIRST_SEEN_TS = "coffee_first_seen_ts";
    public static final String KEY_LAST_SHOWN_TS = "coffee_last_shown_ts";
    public static final String KEY_DISMISSED_FOREVER = "coffee_dismissed_forever";

    private final SharedPreferences prefs;

    public CoffeePromptManager(Context context) {
        Context app = context.getApplicationContext();
        Context target = app != null ? app : context;
        this.prefs = target.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean shouldShowCoffeePrompt() {
        return shouldShowCoffeePrompt(System.currentTimeMillis());
    }

    public boolean shouldShowCoffeePrompt(long nowMillis) {
        return shouldShowCoffeePrompt(prefs, nowMillis);
    }

    public void recordSnoozed() {
        recordSnoozed(System.currentTimeMillis());
    }

    public void recordSnoozed(long nowMillis) {
        recordSnoozed(prefs, nowMillis);
    }

    public void recordDismissedForever() {
        recordDismissedForever(prefs);
    }

    public static boolean shouldShowCoffeePrompt(SharedPreferences prefs, long nowMillis) {
        if (prefs.getBoolean(KEY_DISMISSED_FOREVER, false)) {
            return false;
        }

        long firstSeenTs = prefs.getLong(KEY_FIRST_SEEN_TS, 0L);
        if (firstSeenTs <= 0L) {
            prefs.edit().putLong(KEY_FIRST_SEEN_TS, nowMillis).apply();
            return false;
        }

        long lastShownTs = prefs.getLong(KEY_LAST_SHOWN_TS, 0L);
        long refTs = lastShownTs > 0L ? lastShownTs : firstSeenTs;
        if (nowMillis < refTs) {
            return false;
        }

        return (nowMillis - refTs) >= THREE_DAYS_MILLIS;
    }

    public static void recordSnoozed(SharedPreferences prefs, long nowMillis) {
        prefs.edit().putLong(KEY_LAST_SHOWN_TS, nowMillis).apply();
    }

    public static void recordDismissedForever(SharedPreferences prefs) {
        prefs.edit().putBoolean(KEY_DISMISSED_FOREVER, true).apply();
    }
}
