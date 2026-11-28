package com.huaweitv.solar.model;

/**
 * App settings model.
 */
public class AppSettings {
    public InverterBrand inverterBrand = InverterBrand.HUAWEI;
    public String ipAddress = "";
    public int port = 502;
    public int unitId = 1;
    public int froniusDeviceId = 1;
    public int pollingIntervalSeconds = 10;
    public boolean mockMode = false;
    public String languageCode = AppLanguage.ITALIAN.code;

    // Meter / Contract power alarm
    public boolean meterAlarmEnabled = false;
    public double contractPowerKw = 3.0;
    public boolean meterAlarmSoundEnabled = true;

    // Shelly local load shedding
    public boolean shellySheddingEnabled = false;
    public String shelly1Name = "";
    public String shelly1Ip = "";
    public int shelly1Channel = 0;
    public ShellyGen shelly1Gen = ShellyGen.GEN2;
    public int shellyTriggerDelaySec = 15;

    public AppSettings() {}

    public AppSettings(String ipAddress, int port, int unitId, int pollingIntervalSeconds, boolean mockMode) {
        this(InverterBrand.HUAWEI, ipAddress, port, unitId, pollingIntervalSeconds, mockMode);
    }

    public AppSettings(InverterBrand inverterBrand, String ipAddress, int port, int unitId, int pollingIntervalSeconds, boolean mockMode) {
        this(
            inverterBrand, ipAddress, port, unitId, 1, pollingIntervalSeconds, mockMode,
            false, 3.0, true,
            false, "", "", 0, ShellyGen.GEN2, 15
        );
    }

    public AppSettings(
        InverterBrand inverterBrand,
        String ipAddress,
        int port,
        int unitId,
        int froniusDeviceId,
        int pollingIntervalSeconds,
        boolean mockMode,
        boolean meterAlarmEnabled,
        double contractPowerKw,
        boolean meterAlarmSoundEnabled,
        boolean shellySheddingEnabled,
        String shelly1Name,
        String shelly1Ip,
        int shelly1Channel,
        ShellyGen shelly1Gen,
        int shellyTriggerDelaySec
    ) {
        this(
            inverterBrand, ipAddress, port, unitId, froniusDeviceId, pollingIntervalSeconds, mockMode,
            AppLanguage.ITALIAN.code,
            meterAlarmEnabled, contractPowerKw, meterAlarmSoundEnabled,
            shellySheddingEnabled, shelly1Name, shelly1Ip, shelly1Channel, shelly1Gen, shellyTriggerDelaySec
        );
    }

    public AppSettings(
        InverterBrand inverterBrand,
        String ipAddress,
        int port,
        int unitId,
        int froniusDeviceId,
        int pollingIntervalSeconds,
        boolean mockMode,
        String languageCode,
        boolean meterAlarmEnabled,
        double contractPowerKw,
        boolean meterAlarmSoundEnabled,
        boolean shellySheddingEnabled,
        String shelly1Name,
        String shelly1Ip,
        int shelly1Channel,
        ShellyGen shelly1Gen,
        int shellyTriggerDelaySec
    ) {
        this.inverterBrand = inverterBrand != null ? inverterBrand : InverterBrand.HUAWEI;
        this.ipAddress = ipAddress != null ? ipAddress : "";
        this.port = port;
        this.unitId = unitId;
        this.froniusDeviceId = Math.max(0, froniusDeviceId);
        this.pollingIntervalSeconds = pollingIntervalSeconds;
        this.mockMode = mockMode;
        AppLanguage resolvedLang = AppLanguage.fromKey(languageCode);
        this.languageCode = resolvedLang != null ? resolvedLang.code : AppLanguage.ITALIAN.code;
        this.meterAlarmEnabled = meterAlarmEnabled;
        this.contractPowerKw = contractPowerKw;
        this.meterAlarmSoundEnabled = meterAlarmSoundEnabled;
        this.shellySheddingEnabled = shellySheddingEnabled;
        this.shelly1Name = shelly1Name != null ? shelly1Name : "";
        this.shelly1Ip = shelly1Ip != null ? shelly1Ip : "";
        this.shelly1Channel = Math.max(0, shelly1Channel);
        this.shelly1Gen = shelly1Gen != null ? shelly1Gen : ShellyGen.GEN2;
        this.shellyTriggerDelaySec = Math.max(1, shellyTriggerDelaySec);
    }

    public AppLanguage getLanguage() {
        AppLanguage parsed = AppLanguage.fromKey(languageCode);
        return parsed != null ? parsed : AppLanguage.ITALIAN;
    }

    public int getEffectivePort() {
        if (inverterBrand == InverterBrand.FRONIUS) {
            return InverterBrand.FRONIUS.defaultPort;
        }
        return (port > 0 && port <= 65535) ? port : inverterBrand.defaultPort;
    }
}
