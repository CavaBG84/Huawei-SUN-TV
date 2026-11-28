package com.huaweitv.solar.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class SolarTelemetryTest {

    @Test
    public void testHouseConsumptionCalculationWithExport() {
        SolarTelemetry data = new SolarTelemetry(3500, 3000, 12.5, 1000, 80.0, 500);
        assertEquals(2000, data.getHouseConsumptionW());
        assertEquals(2000, data.loadPowerW);
        assertTrue(data.isGridExporting());
        assertFalse(data.isGridImporting());
    }

    @Test
    public void testHouseConsumptionCalculationWithImport() {
        SolarTelemetry data = new SolarTelemetry(500, 500, 3.0, -1500, 20.0, 0);
        assertEquals(2000, data.getHouseConsumptionW());
        assertEquals(2000, data.loadPowerW);
        assertFalse(data.isGridExporting());
        assertTrue(data.isGridImporting());
    }

    @Test
    public void testBatteryStateDetection() {
        SolarTelemetry charging = new SolarTelemetry(3000, 1800, 5.0, 0, 50.0, 1200);
        assertTrue(charging.isBatteryCharging());
        assertFalse(charging.isBatteryDischarging());

        SolarTelemetry discharging = new SolarTelemetry(0, 800, 5.0, 0, 45.0, -800);
        assertFalse(discharging.isBatteryCharging());
        assertTrue(discharging.isBatteryDischarging());
    }
}
