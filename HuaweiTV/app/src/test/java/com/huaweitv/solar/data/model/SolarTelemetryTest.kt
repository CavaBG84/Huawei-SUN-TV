package com.huaweitv.solar.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTelemetryTest {

    @Test
    fun testHouseConsumptionCalculationWithExport() {
        val data = SolarTelemetry(
            pvPowerW = 3500,
            activePowerW = 3000,
            gridPowerW = 1000
        )
        assertEquals(2000, data.houseConsumptionW)
        assertEquals(2000, data.loadPowerW)
        assertTrue(data.isGridExporting)
        assertFalse(data.isGridImporting)
    }

    @Test
    fun testHouseConsumptionCalculationWithImport() {
        val data = SolarTelemetry(
            pvPowerW = 500,
            activePowerW = 500,
            gridPowerW = -1500
        )
        assertEquals(2000, data.houseConsumptionW)
        assertEquals(2000, data.loadPowerW)
        assertFalse(data.isGridExporting)
        assertTrue(data.isGridImporting)
    }

    @Test
    fun testHouseConsumptionClampZero() {
        val data = SolarTelemetry(
            activePowerW = 0,
            gridPowerW = 10
        )
        assertEquals(0, data.houseConsumptionW)
    }

    @Test
    fun testBatteryStateDetection() {
        val chargingData = SolarTelemetry(batteryPowerW = 1200)
        assertTrue(chargingData.isBatteryCharging)
        assertFalse(chargingData.isBatteryDischarging)

        val dischargingData = SolarTelemetry(batteryPowerW = -800)
        assertFalse(dischargingData.isBatteryCharging)
        assertTrue(dischargingData.isBatteryDischarging)

        val standbyData = SolarTelemetry(batteryPowerW = 0)
        assertFalse(standbyData.isBatteryCharging)
        assertFalse(standbyData.isBatteryDischarging)
    }

    @Test
    fun testFormatWattsOrKw() {
        val data = SolarTelemetry()
        assertEquals("500 W", data.formatWattsOrKw(500))
        assertEquals("3.50 kW", data.formatWattsOrKw(3500))
        assertEquals("1.25 kW", data.formatWattsOrKw(-1250))
    }
}
