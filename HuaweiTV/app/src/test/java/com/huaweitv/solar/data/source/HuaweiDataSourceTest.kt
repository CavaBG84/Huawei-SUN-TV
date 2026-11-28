package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.modbus.HuaweiRegisters
import com.huaweitv.solar.data.modbus.ModbusPacket
import com.huaweitv.solar.data.modbus.ModbusTcpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HuaweiDataSourceTest {

    private class FakeModbusTcpClient(
        private val registers: Map<Pair<Int, Int>, ByteArray>,
        private val errors: Map<Int, Exception> = emptyMap()
    ) : ModbusTcpClient() {
        private var connected = false
        val queries = mutableListOf<Pair<Int, Int>>()

        override val isConnected: Boolean
            get() = connected

        override suspend fun ensureConnected(host: String, port: Int) {
            connected = true
        }

        override suspend fun readHoldingRegisters(
            unitId: Int,
            startAddress: Int,
            quantity: Int
        ): ByteArray {
            queries.add(startAddress to quantity)
            errors[startAddress]?.let { throw it }
            return registers[startAddress to quantity]
                ?: throw AssertionError("Unexpected register read: $startAddress qty=$quantity")
        }

        override fun close() {
            connected = false
        }
    }

    private fun int32Bytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun uint32Bytes(value: Long): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun uint16Bytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    @Test
    fun testCalculateDailyPvYieldWithLunaBattery() {
        // Real FusionSolar example: inverter AC = 3.92 kWh, battery charge = 4.20 kWh, discharge = 1.31 kWh -> 6.81 kWh
        val yield = HuaweiDataSource.calculateDailyPvYieldKWh(
            dailyInverterYieldKwh = 3.92,
            batDailyChargeKwh = 4.20,
            batDailyDischargeKwh = 1.31
        )
        assertEquals(6.81, yield, 0.001)
    }

    @Test
    fun testCalculateDailyPvYieldClampsNegativeNightDischargeToZero() {
        // Midnight / nighttime discharge lag: inverter AC output = 1.42 kWh, discharge = 1.50 kWh
        val yield = HuaweiDataSource.calculateDailyPvYieldKWh(
            dailyInverterYieldKwh = 1.42,
            batDailyChargeKwh = 0.0,
            batDailyDischargeKwh = 1.50
        )
        assertEquals(0.0, yield, 0.001)
    }

    @Test
    fun testCalculateDailyPvYieldFallsBackWhenBatteryEnergyUnavailable() {
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, null, 1.0), 0.001)
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, 2.0, null), 0.001)
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, null, null), 0.001)
    }

    @Test
    fun testParseEnergyKWhHandlesValidAndInvalidSentinel() {
        assertEquals(15.42, HuaweiDataSource.parseEnergyKWh(uint32Bytes(1542L))!!, 0.001)
        assertEquals(0.0, HuaweiDataSource.parseEnergyKWh(uint32Bytes(0L))!!, 0.001)
        assertNull(HuaweiDataSource.parseEnergyKWh(uint32Bytes(HuaweiRegisters.UINT32_INVALID)))
    }

    @Test
    fun testFetchTelemetryWithLuna2000ReadsContiguousBlock37015AndComputesTrueDailyPvYield() = runBlocking {
        val block37015 = uint32Bytes(420L) + uint32Bytes(131L) // 4.20 kWh charge, 1.31 kWh discharge
        val fakeClient = FakeModbusTcpClient(
            registers = mapOf(
                (HuaweiRegisters.REG_PV_INPUT_POWER to 2) to int32Bytes(3200),
                (HuaweiRegisters.REG_ACTIVE_POWER to 2) to int32Bytes(2000),
                (HuaweiRegisters.REG_DAILY_YIELD to 2) to uint32Bytes(392L),   // 3.92 kWh inverter AC
                (HuaweiRegisters.REG_GRID_POWER to 2) to int32Bytes(500),
                (HuaweiRegisters.REG_BATTERY_SOC to 1) to uint16Bytes(650),     // 65.0%
                (HuaweiRegisters.REG_BATTERY_POWER to 2) to int32Bytes(1150),
                (HuaweiRegisters.REG_BATTERY_DAY_CHARGE to 4) to block37015
            )
        )

        val dataSource = HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient)
        val telemetry = dataSource.fetchTelemetry()

        // 3.92 + 4.20 - 1.31 = 6.81 kWh
        assertEquals(6.81, telemetry.dailyYieldKWh, 0.001)
        assertTrue(telemetry.hasBattery)
        assertEquals(3200, telemetry.pvPowerW)
        assertEquals(1500, telemetry.loadPowerW)
        assertTrue(fakeClient.queries.contains(HuaweiRegisters.REG_BATTERY_DAY_CHARGE to 4))
    }

    @Test
    fun testFetchTelemetryDetectsBatteryAtZeroSocFromDailyCounters() = runBlocking {
        val block37015 = uint32Bytes(200L) + uint32Bytes(210L) // 2.00 kWh charge, 2.10 kWh discharge
        val fakeClient = FakeModbusTcpClient(
            registers = mapOf(
                (HuaweiRegisters.REG_PV_INPUT_POWER to 2) to int32Bytes(0),
                (HuaweiRegisters.REG_ACTIVE_POWER to 2) to int32Bytes(0),
                (HuaweiRegisters.REG_DAILY_YIELD to 2) to uint32Bytes(500L),   // 5.00 kWh
                (HuaweiRegisters.REG_GRID_POWER to 2) to int32Bytes(-400),
                (HuaweiRegisters.REG_BATTERY_SOC to 1) to uint16Bytes(0),       // 0.0%
                (HuaweiRegisters.REG_BATTERY_POWER to 2) to int32Bytes(0),      // 0 W idle
                (HuaweiRegisters.REG_BATTERY_DAY_CHARGE to 4) to block37015
            )
        )

        val dataSource = HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient)
        val telemetry = dataSource.fetchTelemetry()

        // 5.00 + 2.00 - 2.10 = 4.90 kWh
        assertEquals(4.90, telemetry.dailyYieldKWh, 0.001)
        assertTrue(telemetry.hasBattery)
    }

    @Test
    fun testFetchTelemetryFallsBackSilentlyWhenBlock37015ThrowsModbusException() = runBlocking {
        val fakeClient = FakeModbusTcpClient(
            registers = mapOf(
                (HuaweiRegisters.REG_PV_INPUT_POWER to 2) to int32Bytes(2500),
                (HuaweiRegisters.REG_ACTIVE_POWER to 2) to int32Bytes(2450),
                (HuaweiRegisters.REG_DAILY_YIELD to 2) to uint32Bytes(930L),   // 9.30 kWh
                (HuaweiRegisters.REG_GRID_POWER to 2) to int32Bytes(1000),
                (HuaweiRegisters.REG_BATTERY_SOC to 1) to uint16Bytes(0),
                (HuaweiRegisters.REG_BATTERY_POWER to 2) to int32Bytes(0)
            ),
            errors = mapOf(
                HuaweiRegisters.REG_BATTERY_DAY_CHARGE to ModbusPacket.ModbusException("Illegal Data Address", 0x02)
            )
        )

        val dataSource = HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient)
        val telemetry = dataSource.fetchTelemetry()

        assertEquals(9.30, telemetry.dailyYieldKWh, 0.001)
        assertFalse(telemetry.hasBattery)
    }

    @Test
    fun testFetchTelemetryFallsBackSilentlyWhenBlock37015ThrowsIoException() = runBlocking {
        val fakeClient = FakeModbusTcpClient(
            registers = mapOf(
                (HuaweiRegisters.REG_PV_INPUT_POWER to 2) to int32Bytes(2500),
                (HuaweiRegisters.REG_ACTIVE_POWER to 2) to int32Bytes(2450),
                (HuaweiRegisters.REG_DAILY_YIELD to 2) to uint32Bytes(765L),
                (HuaweiRegisters.REG_GRID_POWER to 2) to int32Bytes(500),
                (HuaweiRegisters.REG_BATTERY_SOC to 1) to uint16Bytes(0),
                (HuaweiRegisters.REG_BATTERY_POWER to 2) to int32Bytes(0)
            ),
            errors = mapOf(
                HuaweiRegisters.REG_BATTERY_DAY_CHARGE to IOException("Read failed on 37015..37018")
            )
        )

        val dataSource = HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient)
        val telemetry = dataSource.fetchTelemetry()

        assertEquals(7.65, telemetry.dailyYieldKWh, 0.001)
        assertFalse(telemetry.hasBattery)
    }

    @Test
    fun testFetchTelemetryIgnoresSentinelBatteryEnergyValues() = runBlocking {
        val sentinelBlock = uint32Bytes(HuaweiRegisters.UINT32_INVALID) + uint32Bytes(HuaweiRegisters.UINT32_INVALID)
        val fakeClient = FakeModbusTcpClient(
            registers = mapOf(
                (HuaweiRegisters.REG_PV_INPUT_POWER to 2) to int32Bytes(2500),
                (HuaweiRegisters.REG_ACTIVE_POWER to 2) to int32Bytes(2450),
                (HuaweiRegisters.REG_DAILY_YIELD to 2) to uint32Bytes(765L),
                (HuaweiRegisters.REG_GRID_POWER to 2) to int32Bytes(500),
                (HuaweiRegisters.REG_BATTERY_SOC to 1) to uint16Bytes(0),
                (HuaweiRegisters.REG_BATTERY_POWER to 2) to int32Bytes(0),
                (HuaweiRegisters.REG_BATTERY_DAY_CHARGE to 4) to sentinelBlock
            )
        )

        val dataSource = HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient)
        val telemetry = dataSource.fetchTelemetry()

        assertEquals(7.65, telemetry.dailyYieldKWh, 0.001)
        assertFalse(telemetry.hasBattery)
    }
}
