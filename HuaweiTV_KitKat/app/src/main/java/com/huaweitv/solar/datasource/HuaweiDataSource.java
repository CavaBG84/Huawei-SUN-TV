package com.huaweitv.solar.datasource;

import com.huaweitv.solar.modbus.HuaweiRegisters;
import com.huaweitv.solar.modbus.ModbusPacket;
import com.huaweitv.solar.modbus.ModbusTcpClient;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Huawei SUN2000 + LUNA2000 data source over Modbus TCP.
 * Encapsulates the existing Modbus connection, registers, delays (1500ms / 200ms),
 * and sign conventions inside {@link InverterDataSource}.
 */
public class HuaweiDataSource implements InverterDataSource {

    private final String ipAddress;
    private final int port;
    private final int unitId;
    private final ModbusTcpClient modbusClient;

    public HuaweiDataSource(String ipAddress, int port, int unitId) {
        this(ipAddress, port, unitId, new ModbusTcpClient());
    }

    public HuaweiDataSource(String ipAddress, int port, int unitId, ModbusTcpClient modbusClient) {
        this.ipAddress = ipAddress != null ? ipAddress.trim() : "";
        this.port = port;
        this.unitId = unitId;
        this.modbusClient = modbusClient;
    }

    @Override
    public InverterBrand getBrand() {
        return InverterBrand.HUAWEI;
    }

    @Override
    public void connect() throws Exception {
        modbusClient.ensureConnected(ipAddress, port);
    }

    @Override
    public SolarTelemetry fetchTelemetry() throws Exception {
        if (!isConnected()) {
            connect();
        }

        try {
            // 1. Read PV Input Power (Reg 32064, INT32, 2 reg)
            byte[] pvBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_PV_INPUT_POWER, 2);
            int pvPowerW = Math.max(0, ModbusPacket.parseInt32(pvBytes, 0));
            Thread.sleep(200);

            // 2. Read Inverter Active Power (Reg 32080, INT32, 2 reg)
            byte[] activeBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_ACTIVE_POWER, 2);
            int activePowerW = ModbusPacket.parseInt32(activeBytes, 0);
            Thread.sleep(200);

            // 3. Read Inverter Daily Yield (Reg 32114, UINT32, 2 reg, /100.0)
            byte[] yieldBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_DAILY_YIELD, 2);
            Double parsedInverterYield = parseEnergyKWh(yieldBytes, 0);
            double inverterDailyYieldKWh = parsedInverterYield != null ? parsedInverterYield : 0.0;
            Thread.sleep(200);

            // 4. Read Grid Power (Reg 37113, INT32, 2 reg)
            byte[] gridBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_GRID_POWER, 2);
            int gridPowerW = ModbusPacket.parseInt32(gridBytes, 0);
            Thread.sleep(200);

            // 5. Read Battery SoC (Reg 37760, UINT16, 1 reg, /10.0)
            byte[] socBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_BATTERY_SOC, 1);
            double batterySoc = Math.min(100.0, Math.max(0.0, ModbusPacket.parseUInt16(socBytes, 0) / 10.0));
            Thread.sleep(200);

            // 6. Read Battery Power (Reg 37765, INT32, 2 reg)
            byte[] batPowerBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_BATTERY_POWER, 2);
            int batteryPowerW = ModbusPacket.parseInt32(batPowerBytes, 0);

            // 7. Read Battery Current-Day Charge (37015) & Discharge (37017) as a single 4-register block (37015..37018).
            // Reg 32114 tracks inverter AC output (excluding DC PV->battery charge and including battery->AC discharge).
            Thread.sleep(200);
            double batDailyChargeKwh = 0.0;
            double batDailyDischargeKwh = 0.0;
            try {
                byte[] batDayEnergyBytes = modbusClient.readHoldingRegisters(
                    unitId,
                    HuaweiRegisters.REG_BATTERY_DAY_CHARGE,
                    HuaweiRegisters.REG_BATTERY_DAY_ENERGY_QTY
                );
                if (batDayEnergyBytes.length >= 8) {
                    Double parsedCharge = parseEnergyKWh(batDayEnergyBytes, 0);
                    Double parsedDischarge = parseEnergyKWh(batDayEnergyBytes, 4);
                    if (parsedCharge != null && parsedDischarge != null) {
                        batDailyChargeKwh = parsedCharge;
                        batDailyDischargeKwh = parsedDischarge;
                    }
                }
            } catch (Exception ignored) {
                // Fallback silently on plants without battery or unsupported registers (0x02 Illegal Data Address, etc.)
                batDailyChargeKwh = 0.0;
                batDailyDischargeKwh = 0.0;
            }

            double dailyYieldKWh = calculateDailyPvYieldKWh(
                inverterDailyYieldKWh,
                batDailyChargeKwh,
                batDailyDischargeKwh
            );

            int loadPowerW = Math.max(0, activePowerW - gridPowerW);
            boolean hasBattery = batterySoc > 0.0
                || batteryPowerW != 0
                || batDailyChargeKwh > 0.0
                || batDailyDischargeKwh > 0.0;

            return new SolarTelemetry(
                pvPowerW,
                activePowerW,
                loadPowerW,
                dailyYieldKWh,
                gridPowerW,
                batterySoc,
                batteryPowerW,
                System.currentTimeMillis(),
                true,
                hasBattery,
                true,
                null
            );
        } catch (SocketTimeoutException e) {
            if (unitId != 0) {
                throw new IOException("Timeout lettura registri (Verifica Unit ID: deve essere 0)", e);
            }
            throw e;
        }
    }

    @Override
    public void disconnect() {
        modbusClient.close();
    }

    @Override
    public boolean isConnected() {
        return modbusClient.isConnected();
    }

    /**
     * Parses a Huawei UINT32 energy register (gain 100 -> kWh), returning null for 0xFFFFFFFF sentinel.
     */
    public static Double parseEnergyKWh(byte[] bytes, int offset) {
        long raw = ModbusPacket.parseUInt32(bytes, offset);
        if (raw == HuaweiRegisters.UINT32_INVALID) {
            return null;
        }
        return raw / 100.0;
    }

    /**
     * Computes true Daily PV Yield on Huawei SUN2000 + LUNA2000 systems:
     * pvDailyYieldKwh = max(0, dailyInverterYieldKwh (32114) + batDailyChargeKwh (37015) - batDailyDischargeKwh (37017)).
     * Falls back to dailyInverterYieldKwh when battery daily energy counters are unavailable.
     */
    public static double calculateDailyPvYieldKWh(
        double dailyInverterYieldKwh,
        Double batDailyChargeKwh,
        Double batDailyDischargeKwh
    ) {
        double safeInverterYield = Math.max(0.0, dailyInverterYieldKwh);
        if (batDailyChargeKwh == null || batDailyDischargeKwh == null) {
            return safeInverterYield;
        }
        return Math.max(0.0, safeInverterYield + batDailyChargeKwh - batDailyDischargeKwh);
    }
}
