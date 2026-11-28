package com.huaweitv.solar.coffee;

import android.content.SharedPreferences;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class CoffeePromptManagerTest {

    private static class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public Map<String, ?> getAll() { return new HashMap<>(data); }
        @Override
        public String getString(String key, String defValue) {
            Object val = data.get(key);
            return val instanceof String ? (String) val : defValue;
        }
        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
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
        public boolean contains(String key) { return data.containsKey(key); }
        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();
                private boolean clearAll = false;

                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> values) { return this; }
                @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                @Override public Editor remove(String key) { pending.put(key, null); return this; }
                @Override public Editor clear() { clearAll = true; return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override
                public void apply() {
                    if (clearAll) data.clear();
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) data.remove(entry.getKey());
                        else data.put(entry.getKey(), entry.getValue());
                    }
                }
            };
        }
    }

    @Test
    public void testThreeDayScheduleSnoozeAndPermanentDismissal() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        long day0 = 1_700_000_000_000L;
        long threeDays = CoffeePromptManager.THREE_DAYS_MILLIS;

        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0));
        assertEquals(day0, prefs.getLong(CoffeePromptManager.KEY_FIRST_SEEN_TS, 0L));
        assertFalse(prefs.getBoolean(CoffeePromptManager.KEY_DISMISSED_FOREVER, false));

        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 + threeDays - 1_000L));
        assertTrue(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 + threeDays));

        long snoozedAt = day0 + threeDays + 5_000L;
        CoffeePromptManager.recordSnoozed(prefs, snoozedAt);
        assertEquals(snoozedAt, prefs.getLong(CoffeePromptManager.KEY_LAST_SHOWN_TS, 0L));
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays - 1L));
        assertTrue(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays));

        CoffeePromptManager.recordDismissedForever(prefs);
        assertTrue(prefs.getBoolean(CoffeePromptManager.KEY_DISMISSED_FOREVER, false));
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, snoozedAt + threeDays * 20L));
    }

    @Test
    public void testClockRollbackDoesNotShowPrompt() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        long day0 = 1_700_000_000_000L;
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0));
        assertFalse(CoffeePromptManager.shouldShowCoffeePrompt(prefs, day0 - 10_000L));
    }

    @Test
    public void testQrCodeEncoderForPayPalUrlProducesValidVersion3Matrix() {
        boolean[][] matrix = QrCodeEncoder.encode(CoffeePromptManager.PAYPAL_URL);
        assertEquals(29, matrix.length);
        for (boolean[] row : matrix) {
            assertEquals(29, row.length);
        }

        for (int i = 0; i < 7; i++) {
            assertTrue(matrix[0][i]);
            assertTrue(matrix[6][i]);
            assertTrue(matrix[i][0]);
            assertTrue(matrix[i][6]);
        }
        assertTrue(matrix[3][3]);
        assertFalse(matrix[1][1]);

        for (int i = 0; i < 8; i++) {
            assertFalse(matrix[7][i]);
            assertFalse(matrix[i][7]);
        }

        assertTrue(matrix[3][25]);
        assertTrue(matrix[25][3]);
        assertTrue(matrix[6][8]);
        assertFalse(matrix[6][9]);
        assertTrue(matrix[21][8]);
        assertTrue(matrix[22][22]);
        assertFalse(matrix[21][22]);
    }
}
