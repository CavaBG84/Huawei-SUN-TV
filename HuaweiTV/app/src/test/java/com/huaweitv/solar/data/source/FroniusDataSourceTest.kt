package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.InverterBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FroniusDataSourceTest {

    @Test
    fun testMapValuesHybridExportAndBatteryCharging() {
        // Fronius: P_Grid = -1250.2 (export), P_Akku = -1100.0 (charging), P_Load = -1850.2
        val telemetry = FroniusDataSource.mapValues(
            pvW = 4200.4,
            gridW = -1250.2,
            loadW = -1850.2,
            akkuW = -1100.0,
            eDayWh = 15430.0,
            socPct = 82.5,
            inverterActivePowerW = 3100.4,
            timestamp = 123456L
        )

        assertEquals(4200, telemetry.pvPowerW)
        assertEquals(1250, telemetry.gridPowerW) // + = export
        assertTrue(telemetry.isGridExporting)
        assertFalse(telemetry.isGridImporting)
        assertEquals(1100, telemetry.batteryPowerW) // + = charging
        assertTrue(telemetry.isBatteryCharging)
        assertEquals(1850, telemetry.loadPowerW)
        assertEquals(1850, telemetry.houseConsumptionW)
        assertEquals(3100, telemetry.activePowerW)
        assertEquals(15.43, telemetry.dailyYieldKWh, 0.001)
        assertEquals(82.5, telemetry.batterySoc, 0.01)
        assertTrue(telemetry.isConnected)
        assertTrue(telemetry.hasBattery)
        assertTrue(telemetry.hasMeter)
    }

    @Test
    fun testMapValuesGridImportAndBatteryDischarging() {
        // Fronius: P_Grid = +950 (import), P_Akku = +700 (discharging), P_Load = -1950
        val telemetry = FroniusDataSource.mapValues(
            pvW = 300.0,
            gridW = 950.0,
            loadW = -1950.0,
            akkuW = 700.0,
            eDayWh = 4200.0,
            socPct = 34.0,
            inverterActivePowerW = 1000.0
        )

        assertEquals(300, telemetry.pvPowerW)
        assertEquals(-950, telemetry.gridPowerW) // - = import
        assertTrue(telemetry.isGridImporting)
        assertEquals(-700, telemetry.batteryPowerW) // - = discharging
        assertTrue(telemetry.isBatteryDischarging)
        assertEquals(1950, telemetry.loadPowerW)
        assertEquals(4.2, telemetry.dailyYieldKWh, 0.001)
        assertEquals(34.0, telemetry.batterySoc, 0.01)
    }

    @Test
    fun testMapValuesNighttimeNullsWithoutBattery() {
        val telemetry = FroniusDataSource.mapValues(
            pvW = null,
            gridW = 410.6,
            loadW = -410.6,
            akkuW = null,
            eDayWh = 9850.0,
            socPct = null,
            inverterActivePowerW = 0.0
        )

        assertEquals(0, telemetry.pvPowerW)
        assertEquals(-411, telemetry.gridPowerW)
        assertEquals(0, telemetry.batteryPowerW)
        assertEquals(411, telemetry.loadPowerW)
        assertEquals(9.85, telemetry.dailyYieldKWh, 0.001)
        assertEquals(0.0, telemetry.batterySoc, 0.01)
        assertFalse(telemetry.hasBattery)
        assertTrue(telemetry.hasMeter)
    }

    @Test
    fun testMapValuesWithoutMeter() {
        val telemetry = FroniusDataSource.mapValues(
            pvW = 2500.0,
            gridW = null,
            loadW = null,
            akkuW = null,
            eDayWh = 6500.0,
            socPct = null,
            inverterActivePowerW = 2450.0
        )

        assertEquals(2500, telemetry.pvPowerW)
        assertEquals(2450, telemetry.activePowerW)
        assertEquals(2450, telemetry.loadPowerW)
        assertFalse(telemetry.hasBattery)
        assertFalse(telemetry.hasMeter)
    }

    @Test
    fun testInverterBrandKeyAndFactory() {
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey(null))
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey("HUAWEI"))
        assertEquals(InverterBrand.FRONIUS, InverterBrand.fromKey("FRONIUS"))
        assertEquals(InverterBrand.HUAWEI, InverterBrand.fromKey("UNKNOWN"))

        val huaweiSource = InverterDataSourceFactory.create(AppSettings(inverterBrand = InverterBrand.HUAWEI, ipAddress = "192.168.1.50"))
        assertTrue(huaweiSource is HuaweiDataSource)

        val froniusSource = InverterDataSourceFactory.create(AppSettings(inverterBrand = InverterBrand.FRONIUS, ipAddress = "192.168.1.60", port = 80))
        assertTrue(froniusSource is FroniusDataSource)

        val mockSource = InverterDataSourceFactory.create(AppSettings(mockMode = true))
        assertTrue(mockSource is MockDataSource)
    }
}
