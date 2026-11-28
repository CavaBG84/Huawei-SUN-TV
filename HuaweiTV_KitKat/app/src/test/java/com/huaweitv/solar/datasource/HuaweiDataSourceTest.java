package com.huaweitv.solar.datasource;

import com.huaweitv.solar.modbus.HuaweiRegisters;
import com.huaweitv.solar.modbus.ModbusPacket;
import com.huaweitv.solar.modbus.ModbusTcpClient;
import com.huaweitv.solar.model.SolarTelemetry;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HuaweiDataSourceTest {

    private static class FakeModbusTcpClient extends ModbusTcpClient {
        private final Map<String, byte[]> registers;
        private final Map<Integer, IOException> errors;
        private boolean connected = false;
        final List<String> queries = new ArrayList<>();

        FakeModbusTcpClient(Map<String, byte[]> registers, Map<Integer, IOException> errors) {
            this.registers = registers;
            this.errors = errors;
        }

        @Override
        public synchronized boolean isConnected() {
            return connected;
        }

        @Override
        public synchronized void ensureConnected(String host, int port) {
            connected = true;
        }

        @Override
        public synchronized byte[] readHoldingRegisters(int unitId, int startAddress, int quantity) throws IOException {
            String key = startAddress + ":" + quantity;
            queries.add(key);
            IOException err = errors.get(startAddress);
            if (err != null) {
                throw err;
            }
            byte[] data = registers.get(key);
            if (data == null) {
                throw new AssertionError("Unexpected register read: " + key);
            }
            return data;
        }

        @Override
        public synchronized void close() {
            connected = false;
        }
    }

    private static byte[] int32Bytes(int value) {
        return new byte[]{
            (byte) ((value >>> 24) & 0xFF),
            (byte) ((value >>> 16) & 0xFF),
            (byte) ((value >>> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static byte[] uint32Bytes(long value) {
        return new byte[]{
            (byte) ((value >>> 24) & 0xFF),
            (byte) ((value >>> 16) & 0xFF),
            (byte) ((value >>> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static byte[] uint16Bytes(int value) {
        return new byte[]{
            (byte) ((value >>> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Test
    public void testCalculateDailyPvYieldWithLunaBattery() {
        double yield = HuaweiDataSource.calculateDailyPvYieldKWh(3.92, 4.20, 1.31);
        assertEquals(6.81, yield, 0.001);
    }

    @Test
    public void testCalculateDailyPvYieldClampsNegativeNightDischargeToZero() {
        double yield = HuaweiDataSource.calculateDailyPvYieldKWh(1.42, 0.0, 1.50);
        assertEquals(0.0, yield, 0.001);
    }

    @Test
    public void testCalculateDailyPvYieldFallsBackWhenBatteryEnergyUnavailable() {
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, null, 1.0), 0.001);
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, 2.0, null), 0.001);
        assertEquals(8.75, HuaweiDataSource.calculateDailyPvYieldKWh(8.75, null, null), 0.001);
    }

    @Test
    public void testParseEnergyKWhHandlesValidAndInvalidSentinel() {
        Double parsed = HuaweiDataSource.parseEnergyKWh(uint32Bytes(1542L), 0);
        assertNotNull(parsed);
        assertEquals(15.42, parsed, 0.001);

        Double zero = HuaweiDataSource.parseEnergyKWh(uint32Bytes(0L), 0);
        assertNotNull(zero);
        assertEquals(0.0, zero, 0.001);

        assertNull(HuaweiDataSource.parseEnergyKWh(uint32Bytes(HuaweiRegisters.UINT32_INVALID), 0));
    }

    @Test
    public void testFetchTelemetryWithLuna2000ReadsContiguousBlock37015AndComputesTrueDailyPvYield() throws Exception {
        Map<String, byte[]> registers = new HashMap<>();
        registers.put(HuaweiRegisters.REG_PV_INPUT_POWER + ":2", int32Bytes(3200));
        registers.put(HuaweiRegisters.REG_ACTIVE_POWER + ":2", int32Bytes(2000));
        registers.put(HuaweiRegisters.REG_DAILY_YIELD + ":2", uint32Bytes(392L));
        registers.put(HuaweiRegisters.REG_GRID_POWER + ":2", int32Bytes(500));
        registers.put(HuaweiRegisters.REG_BATTERY_SOC + ":1", uint16Bytes(650));
        registers.put(HuaweiRegisters.REG_BATTERY_POWER + ":2", int32Bytes(1150));
        registers.put(
            HuaweiRegisters.REG_BATTERY_DAY_CHARGE + ":4",
            concat(uint32Bytes(420L), uint32Bytes(131L))
        );

        FakeModbusTcpClient fakeClient = new FakeModbusTcpClient(registers, new HashMap<>());
        HuaweiDataSource dataSource = new HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient);
        SolarTelemetry telemetry = dataSource.fetchTelemetry();

        assertEquals(6.81, telemetry.dailyYieldKWh, 0.001);
        assertTrue(telemetry.hasBattery);
        assertEquals(3200, telemetry.pvPowerW);
        assertEquals(1500, telemetry.loadPowerW);
        assertTrue(fakeClient.queries.contains(HuaweiRegisters.REG_BATTERY_DAY_CHARGE + ":4"));
    }

    @Test
    public void testFetchTelemetryDetectsBatteryAtZeroSocFromDailyCounters() throws Exception {
        Map<String, byte[]> registers = new HashMap<>();
        registers.put(HuaweiRegisters.REG_PV_INPUT_POWER + ":2", int32Bytes(0));
        registers.put(HuaweiRegisters.REG_ACTIVE_POWER + ":2", int32Bytes(0));
        registers.put(HuaweiRegisters.REG_DAILY_YIELD + ":2", uint32Bytes(500L));
        registers.put(HuaweiRegisters.REG_GRID_POWER + ":2", int32Bytes(-400));
        registers.put(HuaweiRegisters.REG_BATTERY_SOC + ":1", uint16Bytes(0));
        registers.put(HuaweiRegisters.REG_BATTERY_POWER + ":2", int32Bytes(0));
        registers.put(
            HuaweiRegisters.REG_BATTERY_DAY_CHARGE + ":4",
            concat(uint32Bytes(200L), uint32Bytes(210L))
        );

        FakeModbusTcpClient fakeClient = new FakeModbusTcpClient(registers, new HashMap<>());
        HuaweiDataSource dataSource = new HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient);
        SolarTelemetry telemetry = dataSource.fetchTelemetry();

        assertEquals(4.90, telemetry.dailyYieldKWh, 0.001);
        assertTrue(telemetry.hasBattery);
    }

    @Test
    public void testFetchTelemetryFallsBackSilentlyWhenBlock37015ThrowsModbusException() throws Exception {
        Map<String, byte[]> registers = new HashMap<>();
        registers.put(HuaweiRegisters.REG_PV_INPUT_POWER + ":2", int32Bytes(2500));
        registers.put(HuaweiRegisters.REG_ACTIVE_POWER + ":2", int32Bytes(2450));
        registers.put(HuaweiRegisters.REG_DAILY_YIELD + ":2", uint32Bytes(930L));
        registers.put(HuaweiRegisters.REG_GRID_POWER + ":2", int32Bytes(1000));
        registers.put(HuaweiRegisters.REG_BATTERY_SOC + ":1", uint16Bytes(0));
        registers.put(HuaweiRegisters.REG_BATTERY_POWER + ":2", int32Bytes(0));

        Map<Integer, IOException> errors = new HashMap<>();
        errors.put(HuaweiRegisters.REG_BATTERY_DAY_CHARGE, new ModbusPacket.ModbusException("Illegal Data Address", 0x02));

        FakeModbusTcpClient fakeClient = new FakeModbusTcpClient(registers, errors);
        HuaweiDataSource dataSource = new HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient);
        SolarTelemetry telemetry = dataSource.fetchTelemetry();

        assertEquals(9.30, telemetry.dailyYieldKWh, 0.001);
        assertFalse(telemetry.hasBattery);
    }

    @Test
    public void testFetchTelemetryFallsBackSilentlyWhenBlock37015ThrowsIoException() throws Exception {
        Map<String, byte[]> registers = new HashMap<>();
        registers.put(HuaweiRegisters.REG_PV_INPUT_POWER + ":2", int32Bytes(2500));
        registers.put(HuaweiRegisters.REG_ACTIVE_POWER + ":2", int32Bytes(2450));
        registers.put(HuaweiRegisters.REG_DAILY_YIELD + ":2", uint32Bytes(765L));
        registers.put(HuaweiRegisters.REG_GRID_POWER + ":2", int32Bytes(500));
        registers.put(HuaweiRegisters.REG_BATTERY_SOC + ":1", uint16Bytes(0));
        registers.put(HuaweiRegisters.REG_BATTERY_POWER + ":2", int32Bytes(0));

        Map<Integer, IOException> errors = new HashMap<>();
        errors.put(HuaweiRegisters.REG_BATTERY_DAY_CHARGE, new IOException("Read failed on 37015..37018"));

        FakeModbusTcpClient fakeClient = new FakeModbusTcpClient(registers, errors);
        HuaweiDataSource dataSource = new HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient);
        SolarTelemetry telemetry = dataSource.fetchTelemetry();

        assertEquals(7.65, telemetry.dailyYieldKWh, 0.001);
        assertFalse(telemetry.hasBattery);
    }

    @Test
    public void testFetchTelemetryIgnoresSentinelBatteryEnergyValues() throws Exception {
        Map<String, byte[]> registers = new HashMap<>();
        registers.put(HuaweiRegisters.REG_PV_INPUT_POWER + ":2", int32Bytes(2500));
        registers.put(HuaweiRegisters.REG_ACTIVE_POWER + ":2", int32Bytes(2450));
        registers.put(HuaweiRegisters.REG_DAILY_YIELD + ":2", uint32Bytes(765L));
        registers.put(HuaweiRegisters.REG_GRID_POWER + ":2", int32Bytes(500));
        registers.put(HuaweiRegisters.REG_BATTERY_SOC + ":1", uint16Bytes(0));
        registers.put(HuaweiRegisters.REG_BATTERY_POWER + ":2", int32Bytes(0));
        registers.put(
            HuaweiRegisters.REG_BATTERY_DAY_CHARGE + ":4",
            concat(uint32Bytes(HuaweiRegisters.UINT32_INVALID), uint32Bytes(HuaweiRegisters.UINT32_INVALID))
        );

        FakeModbusTcpClient fakeClient = new FakeModbusTcpClient(registers, new HashMap<>());
        HuaweiDataSource dataSource = new HuaweiDataSource("192.168.1.100", 6607, 0, fakeClient);
        SolarTelemetry telemetry = dataSource.fetchTelemetry();

        assertEquals(7.65, telemetry.dailyYieldKWh, 0.001);
        assertFalse(telemetry.hasBattery);
    }
}
