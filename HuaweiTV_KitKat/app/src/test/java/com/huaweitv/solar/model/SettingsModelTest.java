package com.huaweitv.solar.model;

import android.content.SharedPreferences;
import com.huaweitv.solar.repository.SettingsRepository;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class SettingsModelTest {

    private static class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(data);
        }

        @Override
        public String getString(String key, String defValue) {
            Object val = data.get(key);
            return val instanceof String ? (String) val : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object val = data.get(key);
            return val instanceof Integer ? (Integer) val : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object val = data.get(key);
            return val instanceof Long ? (Long) val : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object val = data.get(key);
            return val instanceof Float ? (Float) val : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object val = data.get(key);
            return val instanceof Boolean ? (Boolean) val : defValue;
        }

        @Override
        public boolean contains(String key) {
            return data.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();
                private boolean clearAll = false;

                @Override
                public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    pending.put(key, null);
                    return this;
                }

                @Override
                public Editor clear() {
                    clearAll = true;
                    return this;
                }

                @Override
                public boolean commit() {
                    apply();
                    return true;
                }

                @Override
                public void apply() {
                    if (clearAll) data.clear();
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) {
                            data.remove(entry.getKey());
                        } else {
                            data.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }

    @Test
    public void testDefaultAppSettings() {
        AppSettings settings = new AppSettings();
        assertEquals(InverterBrand.HUAWEI, settings.inverterBrand);
        assertEquals(502, settings.port);
        assertEquals(1, settings.unitId);
        assertEquals(1, settings.froniusDeviceId);
        assertEquals(10, settings.pollingIntervalSeconds);
        assertFalse(settings.mockMode);
        assertEquals("it", settings.languageCode);
        assertEquals(AppLanguage.ITALIAN, settings.getLanguage());

        assertFalse(settings.meterAlarmEnabled);
        assertEquals(3.0, settings.contractPowerKw, 0.001);
        assertTrue(settings.meterAlarmSoundEnabled);

        assertFalse(settings.shellySheddingEnabled);
        assertEquals("", settings.shelly1Name);
        assertEquals("", settings.shelly1Ip);
        assertEquals(0, settings.shelly1Channel);
        assertEquals(ShellyGen.GEN2, settings.shelly1Gen);
        assertEquals(15, settings.shellyTriggerDelaySec);
    }

    @Test
    public void testEffectivePortPerBrand() {
        AppSettings huaweiCustom = new AppSettings("192.168.1.53", 6607, 0, 10, false);
        assertEquals(6607, huaweiCustom.getEffectivePort());
        assertEquals(0, huaweiCustom.unitId);

        AppSettings fronius = new AppSettings(InverterBrand.FRONIUS, "192.168.1.60", 6607, 0, 10, false);
        assertEquals(80, fronius.getEffectivePort());
    }

    @Test
    public void testShellyGenFromKey() {
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey(null));
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey(""));
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey("GEN2"));
        assertEquals(ShellyGen.GEN1, ShellyGen.fromKey("GEN1"));
        assertEquals(ShellyGen.GEN1, ShellyGen.fromKey("gen1"));
        assertEquals(ShellyGen.GEN2, ShellyGen.fromKey("UNKNOWN"));
    }

    @Test
    public void testAppLanguageResolution() {
        assertNull(AppLanguage.fromKey(null));
        assertNull(AppLanguage.fromKey(""));
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromKey("it"));
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromKey("EN_US"));
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromKey("de-DE"));
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromKey("fr"));
        assertNull(AppLanguage.fromKey("es"));

        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.US));
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLocale(Locale.GERMANY));
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLocale(Locale.FRANCE));
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromLocale(new Locale("es", "ES")));

        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve("fr", Locale.US));
        assertEquals(AppLanguage.GERMAN, AppLanguage.resolve(null, Locale.GERMANY));
        assertEquals(AppLanguage.ITALIAN, AppLanguage.resolve("unknown", new Locale("es")));
    }

    @Test
    public void testSaveAndLoadLanguageCodeInPreferences() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        String[] codes = new String[]{"it", "en", "de", "fr"};
        for (String code : codes) {
            AppSettings updated = new AppSettings(
                InverterBrand.HUAWEI, "", 502, 1, 1, 10, false,
                code,
                false, 3.0, true, false, "", "", 0, ShellyGen.GEN2, 15
            );
            SettingsRepository.saveToPreferences(prefs, updated);
            AppSettings loaded = SettingsRepository.loadFromPreferences(prefs);
            assertEquals(code, loaded.languageCode);
            assertEquals(code, loaded.getLanguage().code);
        }
    }
}
