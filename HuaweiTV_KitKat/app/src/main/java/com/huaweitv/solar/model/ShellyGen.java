package com.huaweitv.solar.model;

/**
 * Shelly device generation persisted under SharedPreferences key `shelly_1_gen`.
 */
public enum ShellyGen {
    GEN1("GEN1", "GEN1 (Shelly 1/1PM/2.5/EM)", "GEN1"),
    GEN2("GEN2", "GEN2 (Shelly Plus/Pro)", "GEN2");

    public final String prefValue;
    public final String displayName;
    public final String shortLabel;

    ShellyGen(String prefValue, String displayName, String shortLabel) {
        this.prefValue = prefValue;
        this.displayName = displayName;
        this.shortLabel = shortLabel;
    }

    public static ShellyGen fromKey(String key) {
        if (key != null && "GEN1".equalsIgnoreCase(key.trim())) {
            return GEN1;
        }
        return GEN2;
    }
}
