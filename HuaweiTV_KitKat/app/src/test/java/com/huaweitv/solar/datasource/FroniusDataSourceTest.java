package com.huaweitv.solar.datasource;

import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;
import org.junit.Test;

import static org.junit.Assert.*;

public class FroniusDataSourceTest {

    @Test
    public void testMapValuesHybridExportAndBatteryCharging() {
        SolarTelemetry telemetry = FroniusDataSource.mapValues(
            4200.4,
            -1250.2,
            -1850.2,
            -1100.0,
            15430.0,
            82.5,
            3100.4,
            123456L
        );

        assertEquals(4200, telemetry.pvPowerW);
        assertEquals(1250, telemetry.gridPowerW);
        assertTrue(telemetry.isGridExporting());
        assertFalse(telemetry.isGridImporting());
        assertEquals(1100, telemetry.batteryPowerW);
        assertTrue(telemetry.isBatteryCharging());
        assertEquals(1850, telemetry.loadPowerW);
        assertEquals(1850, telemetry.getHouseConsumptionW());
        assertEquals(3100, telemetry.activePowerW);
        assertEquals(15.43, telemetry.dailyYieldKWh, 0.001);
        assertEquals(82.5, telemetry.batterySoc, 0.01);
        assertTrue(telemetry.isConnected);
        assertTrue(telemetry.hasBattery);
        assertTrue(telemetry.hasMeter);
    }

    @Test
    public void testMapValuesGridImportAndBatteryDischarging() {
        SolarTelemetry telemetry = FroniusDataSource.mapValues(
            300.0,
            950.0,
            -1950.0,
            700.0,
            4200.0,
            34.0,
            1000.0,
            System.currentTimeMillis()
        );

        assertEquals(300, telemetry.pvPowerW);
        assertEquals(-950, telemetry.gridPowerW);
        assertTrue(telemetry.isGridImporting());
        assertEquals(-700, telemetry.batteryPowerW);
        assertTrue(telemetry.isBatteryDischarging());
        assertEquals(1950, telemetry.loadPowerW);
        assertEquals(4.2, telemetry.dailyYieldKWh, 0.001);
        assertEquals(34.0, telemetry.batterySoc, 0.01);
    }

    @Test
    public void testMapValuesNighttimeNullsWithoutBattery() {
        SolarTelemetry telemetry = FroniusDataSource.mapValues(
            null,
            410.6,
            -410.6,
            null,
            9850.0,
            null,
            0.0,
            System.currentTimeMillis()
        );

        assertEquals(0, telemetry.pvPowerW);
        assertEquals(-411, telemetry.gridPowerW);
        assertEquals(0, telemetry.batteryPowerW);
        assertEquals(411, telemetry.loadPowerW);
        assertEquals(9.85, telemetry.dailyYieldKWh, 0.001);
        assertEquals(0.0, telemetry.batterySoc, 0.01);
        assertFalse(telemetry.hasBattery);
        assertTrue(telemetry.hasMeter);
    }

    @Test
    public void testInverterBrandKeyAndFactory() {
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey(null));
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey("HUAWEI"));
        assertEquals(InverterBrand.FRONIUS, InverterBrand.fromKey("FRONIUS"));
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey("UNKNOWN"));

        InverterDataSource huawei = InverterDataSourceFactory.create(
            new AppSettings(InverterBrand.HUAWEI, "192.168.1.50", 6607, 0, 10, false)
        );
        assertTrue(huawei instanceof HuaweiDataSource);

        InverterDataSource fronius = InverterDataSourceFactory.create(
            new AppSettings(InverterBrand.FRONIUS, "192.168.1.60", 80, 0, 10, false)
        );
        assertTrue(fronius instanceof FroniusDataSource);

        InverterDataSource mock = InverterDataSourceFactory.create(
            new AppSettings(InverterBrand.HUAWEI, "", 6607, 0, 10, true)
        );
        assertTrue(mock instanceof MockDataSource);
    }
}
