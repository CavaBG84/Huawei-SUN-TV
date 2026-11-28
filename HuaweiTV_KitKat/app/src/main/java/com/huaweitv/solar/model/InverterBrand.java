package com.huaweitv.solar.model;

import java.util.Locale;

/**
 * Supported inverter brands persisted under SharedPreferences key `inverter_brand`.
 */
public enum InverterBrand {
    HUAWEI(
        "HUAWEI",
        "Huawei SUN2000 (Modbus TCP)",
        "Huawei SUN2000 (Modbus TCP)",
        "SUN2000 + LUNA2000",
        502,
        true
    ),
    FRONIUS(
        "FRONIUS",
        "Fronius (Solar API locale)",
        "Fronius (Solar API locale)",
        "FRONIUS SOLAR API v1",
        80,
        false
    );

    public final String prefValue;
    public final String displayName;
    public final String shortLabel;
    public final String subtitle;
    public final int defaultPort;
    public final boolean requiresUnitId;

    InverterBrand(
        String prefValue,
        String displayName,
        String shortLabel,
        String subtitle,
        int defaultPort,
        boolean requiresUnitId
    ) {
        this.prefValue = prefValue;
        this.displayName = displayName;
        this.shortLabel = shortLabel;
        this.subtitle = subtitle;
        this.defaultPort = defaultPort;
        this.requiresUnitId = requiresUnitId;
    }

    public static InverterBrand fromKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return HUAWEI;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if ("FRONIUS".equals(normalized) || "FRONIUS_SOLAR_API".equals(normalized)) {
            return FRONIUS;
        }
        return HUAWEI;
    }
}
