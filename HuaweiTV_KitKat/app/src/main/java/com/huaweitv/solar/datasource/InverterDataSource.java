package com.huaweitv.solar.datasource;

import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;

/**
 * Brand-agnostic inverter data source abstraction (API 19+ compatible).
 */
public interface InverterDataSource {
    InverterBrand getBrand();
    void connect() throws Exception;
    SolarTelemetry fetchTelemetry() throws Exception;
    void disconnect();
    boolean isConnected();
}
