package com.huaweitv.solar.datasource;

import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;

/**
 * Factory creating the appropriate {@link InverterDataSource} based on `inverter_brand` settings.
 */
public final class InverterDataSourceFactory {

    private InverterDataSourceFactory() {}

    public static InverterDataSource create(AppSettings settings) {
        InverterBrand brand = (settings != null && settings.inverterBrand != null)
            ? settings.inverterBrand
            : InverterBrand.HUAWEI;

        if (settings != null && settings.mockMode) {
            return new MockDataSource(brand);
        }
        if (brand == InverterBrand.FRONIUS) {
            String ip = settings != null ? settings.ipAddress : "";
            int port = settings != null ? settings.getEffectivePort() : InverterBrand.FRONIUS.defaultPort;
            int deviceId = settings != null ? settings.froniusDeviceId : 1;
            return new FroniusDataSource(ip, port, deviceId);
        }
        String ip = settings != null ? settings.ipAddress : "";
        int port = settings != null ? settings.getEffectivePort() : InverterBrand.HUAWEI.defaultPort;
        int unitId = settings != null ? settings.unitId : 0;
        return new HuaweiDataSource(ip, port, unitId);
    }
}
