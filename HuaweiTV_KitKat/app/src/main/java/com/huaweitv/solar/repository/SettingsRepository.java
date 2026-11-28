package com.huaweitv.solar.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.huaweitv.solar.model.AppLanguage;
import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.ShellyGen;

import java.util.Locale;

/**
 * Settings persistence with Android SharedPreferences. Pure Java, zero external dependencies.
 * Preserves existing v1.x keys/values in-place while supporting Phase 2 & Phase 3 fields.
 */
public class SettingsRepository {

    public static final String PREF_NAME = "huawei_solar_kitkat_prefs";
    public static final String KEY_INVERTER_BRAND = "inverter_brand";
    public static final String KEY_IP = "inverter_ip";
    public static final String KEY_PORT = "modbus_port";
    public static final String KEY_UNIT_ID = "unit_id";
    public static final String KEY_FRONIUS_DEVICE_ID = "fronius_device_id";
    public static final String KEY_POLL_INTERVAL = "poll_interval";
    public static final String KEY_MOCK_MODE = "mock_mode";
    public static final String KEY_LANGUAGE_CODE = "language_code";

    public static final String KEY_METER_ALARM_ENABLED = "meter_alarm_enabled";
    public static final String KEY_CONTRACT_POWER_KW = "contract_power_kw";
    public static final String KEY_METER_ALARM_SOUND_ENABLED = "meter_alarm_sound_enabled";

    public static final String KEY_SHELLY_SHEDDING_ENABLED = "shelly_shedding_enabled";
    public static final String KEY_SHELLY_1_NAME = "shelly_1_name";
    public static final String KEY_SHELLY_1_IP = "shelly_1_ip";
    public static final String KEY_SHELLY_1_CHANNEL = "shelly_1_channel";
    public static final String KEY_SHELLY_1_GEN = "shelly_1_gen";
    public static final String KEY_SHELLY_TRIGGER_DELAY_SEC = "shelly_trigger_delay_sec";

    private final Context appContext;
    private final SharedPreferences prefs;

    public SettingsRepository(Context context) {
        Context app = context.getApplicationContext();
        this.appContext = app != null ? app : context;
        this.prefs = this.appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public Context getAppContext() {
        return appContext;
    }

    public AppSettings loadSettings() {
        return loadFromPreferences(prefs);
    }

    public void updateSettings(AppSettings settings) {
        saveToPreferences(prefs, settings);
    }

    public static AppSettings loadFromPreferences(SharedPreferences prefs) {
        InverterBrand brand = InverterBrand.fromKey(prefs.getString(KEY_INVERTER_BRAND, InverterBrand.HUAWEI.prefValue));
        int port = prefs.contains(KEY_PORT) ? prefs.getInt(KEY_PORT, 502) : 502;
        int unitId = prefs.contains(KEY_UNIT_ID) ? prefs.getInt(KEY_UNIT_ID, 1) : 1;
        double contractKw = prefs.contains(KEY_CONTRACT_POWER_KW)
            ? (double) prefs.getFloat(KEY_CONTRACT_POWER_KW, 3.0f)
            : 3.0;

        String languageCode = AppLanguage.resolve(prefs.getString(KEY_LANGUAGE_CODE, null), Locale.getDefault()).code;

        return new AppSettings(
            brand,
            prefs.getString(KEY_IP, ""),
            port,
            unitId,
            Math.max(0, prefs.getInt(KEY_FRONIUS_DEVICE_ID, 1)),
            prefs.getInt(KEY_POLL_INTERVAL, 10),
            prefs.getBoolean(KEY_MOCK_MODE, false),
            languageCode,
            prefs.getBoolean(KEY_METER_ALARM_ENABLED, false),
            contractKw,
            prefs.getBoolean(KEY_METER_ALARM_SOUND_ENABLED, true),
            prefs.getBoolean(KEY_SHELLY_SHEDDING_ENABLED, false),
            prefs.getString(KEY_SHELLY_1_NAME, ""),
            prefs.getString(KEY_SHELLY_1_IP, ""),
            Math.max(0, prefs.getInt(KEY_SHELLY_1_CHANNEL, 0)),
            ShellyGen.fromKey(prefs.getString(KEY_SHELLY_1_GEN, ShellyGen.GEN2.prefValue)),
            Math.max(1, prefs.getInt(KEY_SHELLY_TRIGGER_DELAY_SEC, 15))
        );
    }

    public static void saveToPreferences(SharedPreferences prefs, AppSettings settings) {
        prefs.edit()
            .putString(KEY_INVERTER_BRAND, settings.inverterBrand.prefValue)
            .putString(KEY_IP, settings.ipAddress)
            .putInt(KEY_PORT, settings.port)
            .putInt(KEY_UNIT_ID, settings.unitId)
            .putInt(KEY_FRONIUS_DEVICE_ID, settings.froniusDeviceId)
            .putInt(KEY_POLL_INTERVAL, settings.pollingIntervalSeconds)
            .putBoolean(KEY_MOCK_MODE, settings.mockMode)
            .putString(KEY_LANGUAGE_CODE, settings.getLanguage().code)
            .putBoolean(KEY_METER_ALARM_ENABLED, settings.meterAlarmEnabled)
            .putFloat(KEY_CONTRACT_POWER_KW, (float) settings.contractPowerKw)
            .putBoolean(KEY_METER_ALARM_SOUND_ENABLED, settings.meterAlarmSoundEnabled)
            .putBoolean(KEY_SHELLY_SHEDDING_ENABLED, settings.shellySheddingEnabled)
            .putString(KEY_SHELLY_1_NAME, settings.shelly1Name)
            .putString(KEY_SHELLY_1_IP, settings.shelly1Ip)
            .putInt(KEY_SHELLY_1_CHANNEL, settings.shelly1Channel)
            .putString(KEY_SHELLY_1_GEN, settings.shelly1Gen.prefValue)
            .putInt(KEY_SHELLY_TRIGGER_DELAY_SEC, settings.shellyTriggerDelaySec)
            .apply();
    }
}
