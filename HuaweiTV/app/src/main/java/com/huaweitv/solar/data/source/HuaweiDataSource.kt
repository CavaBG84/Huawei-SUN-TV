package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.modbus.HuaweiRegisters
import com.huaweitv.solar.data.modbus.ModbusPacket
import com.huaweitv.solar.data.modbus.ModbusTcpClient
import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.SolarTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Huawei SUN2000 + LUNA2000 data source over Modbus TCP.
 * Encapsulates the existing Modbus connection, registers, delays (1500ms / 200ms),
 * and sign conventions inside [InverterDataSource].
 */
class HuaweiDataSource(
    private val ipAddress: String,
    private val port: Int,
    private val unitId: Int,
    private val modbusClient: ModbusTcpClient = ModbusTcpClient()
) : InverterDataSource {

    override val brand: InverterBrand = InverterBrand.HUAWEI

    override suspend fun connect() {
        modbusClient.ensureConnected(ipAddress.trim(), port)
    }

    override suspend fun fetchTelemetry(): SolarTelemetry {
        if (!isConnected()) {
            connect()
        }

        try {
            // 1. Read PV Input Power (Reg 32064, INT32, 2 registers)
            val pvBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_PV_INPUT_POWER, 2)
            val pvPowerW = ModbusPacket.parseInt32(pvBytes).coerceAtLeast(0)
            delay(200) // Mandatory 200ms delay between register queries

            // 2. Read Inverter Active Power AC (Reg 32080, INT32, 2 registers)
            val activePowerBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_ACTIVE_POWER, 2)
            val activePowerW = ModbusPacket.parseInt32(activePowerBytes)
            delay(200)

            // 3. Read Inverter Daily Yield (Reg 32114, UINT32, 2 registers, scale factor: divide by 100.0)
            val dailyYieldBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_DAILY_YIELD, 2)
            val inverterDailyYieldKWh = parseEnergyKWh(dailyYieldBytes) ?: 0.0
            delay(200)

            // 4. Read Grid Power (Reg 37113, INT32, 2 registers)
            // Positive = export, Negative = import
            val gridBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_GRID_POWER, 2)
            val gridPowerW = ModbusPacket.parseInt32(gridBytes)
            delay(200)

            // 5. Read Battery SoC (Reg 37760, UINT16, 1 register, scale factor: divide by 10.0)
            val socBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_BATTERY_SOC, 1)
            val batterySoc = (ModbusPacket.parseUInt16(socBytes) / 10.0).coerceIn(0.0, 100.0)
            delay(200)

            // 6. Read Battery Power (Reg 37765, INT32, 2 registers)
            // Positive = charge, Negative = discharge
            val batPowerBytes = modbusClient.readHoldingRegisters(unitId, HuaweiRegisters.REG_BATTERY_POWER, 2)
            val batteryPowerW = ModbusPacket.parseInt32(batPowerBytes)

            // 7. Read Battery Current-Day Charge (37015) & Discharge (37017) as a single 4-register block (37015..37018).
            // Reg 32114 tracks inverter AC output (excluding DC PV->battery charge and including battery->AC discharge).
            delay(200)
            var batDailyChargeKwh = 0.0
            var batDailyDischargeKwh = 0.0
            try {
                val batDayEnergyBytes = modbusClient.readHoldingRegisters(
                    unitId,
                    HuaweiRegisters.REG_BATTERY_DAY_CHARGE,
                    HuaweiRegisters.REG_BATTERY_DAY_ENERGY_QTY
                )
                if (batDayEnergyBytes.size >= 8) {
                    val parsedCharge = parseEnergyKWh(batDayEnergyBytes, 0)
                    val parsedDischarge = parseEnergyKWh(batDayEnergyBytes, 4)
                    if (parsedCharge != null && parsedDischarge != null) {
                        batDailyChargeKwh = parsedCharge
                        batDailyDischargeKwh = parsedDischarge
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fallback silently on plants without battery or unsupported registers (0x02 Illegal Data Address, etc.)
                batDailyChargeKwh = 0.0
                batDailyDischargeKwh = 0.0
            }

            val dailyYieldKWh = calculateDailyPvYieldKWh(
                dailyInverterYieldKwh = inverterDailyYieldKWh,
                batDailyChargeKwh = batDailyChargeKwh,
                batDailyDischargeKwh = batDailyDischargeKwh
            )

            val loadPowerW = (activePowerW - gridPowerW).coerceAtLeast(0)
            val hasBattery = batterySoc > 0.0 ||
                batteryPowerW != 0 ||
                batDailyChargeKwh > 0.0 ||
                batDailyDischargeKwh > 0.0

            return SolarTelemetry(
                pvPowerW = pvPowerW,
                activePowerW = activePowerW,
                dailyYieldKWh = dailyYieldKWh,
                gridPowerW = gridPowerW,
                batterySoc = batterySoc,
                batteryPowerW = batteryPowerW,
                loadPowerW = loadPowerW,
                timestamp = System.currentTimeMillis(),
                isConnected = true,
                hasBattery = hasBattery,
                hasMeter = true,
                errorMessage = null
            )
        } catch (e: SocketTimeoutException) {
            if (unitId != 0) {
                throw IOException("Timeout lettura registri (Verifica Unit ID: su porta 6607 deve essere 0)", e)
            }
            throw e
        }
    }

    override fun disconnect() {
        modbusClient.close()
    }

    override fun isConnected(): Boolean = modbusClient.isConnected

    companion object {
        /**
         * Parses a Huawei UINT32 energy register (gain 100 -> kWh), returning null for 0xFFFFFFFF sentinel.
         */
        fun parseEnergyKWh(bytes: ByteArray, offset: Int = 0): Double? {
            val raw = ModbusPacket.parseUInt32(bytes, offset)
            return if (raw == HuaweiRegisters.UINT32_INVALID) null else raw / 100.0
        }

        /**
         * Computes true Daily PV Yield on Huawei SUN2000 + LUNA2000 systems:
         * pvDailyYieldKwh = max(0, dailyInverterYieldKwh (32114) + batDailyChargeKwh (37015) - batDailyDischargeKwh (37017)).
         * Falls back to dailyInverterYieldKwh when battery daily energy counters are unavailable.
         */
        fun calculateDailyPvYieldKWh(
            dailyInverterYieldKwh: Double,
            batDailyChargeKwh: Double?,
            batDailyDischargeKwh: Double?
        ): Double {
            val safeInverterYield = dailyInverterYieldKwh.coerceAtLeast(0.0)
            if (batDailyChargeKwh == null || batDailyDischargeKwh == null) {
                return safeInverterYield
            }
            return (safeInverterYield + batDailyChargeKwh - batDailyDischargeKwh).coerceAtLeast(0.0)
        }
    }
}
