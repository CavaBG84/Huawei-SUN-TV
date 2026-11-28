package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.SolarTelemetry
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulated [InverterDataSource] for UI testing and demo mode without a physical inverter.
 */
class MockDataSource(
    override val brand: InverterBrand = InverterBrand.HUAWEI
) : InverterDataSource {

    @Volatile
    private var connected = false
    private var step = 0.0
    private var baseDailyYield = 14.85

    override suspend fun connect() {
        connected = true
    }

    override suspend fun fetchTelemetry(): SolarTelemetry {
        if (!connected) connect()
        step += 0.1
        baseDailyYield += 0.01

        val pv = (3500 + sin(step) * 600 + Random.nextInt(-50, 50)).toInt().coerceAtLeast(0)
        val house = (1600 + sin(step * 0.7) * 400 + Random.nextInt(-30, 30)).toInt().coerceAtLeast(100)
        val batterySoc = (78.5 + sin(step * 0.2) * 4.0).coerceIn(10.0, 100.0)
        val batteryPower = if (pv > house + 800) {
            (1200 + Random.nextInt(-40, 40)).coerceAtLeast(0)
        } else {
            -(600 + Random.nextInt(-20, 20))
        }

        val gridPower = pv - batteryPower - house
        val activePower = house + gridPower

        return SolarTelemetry(
            pvPowerW = pv,
            activePowerW = activePower,
            dailyYieldKWh = baseDailyYield,
            gridPowerW = gridPower,
            batterySoc = batterySoc,
            batteryPowerW = batteryPower,
            loadPowerW = house,
            timestamp = System.currentTimeMillis(),
            isConnected = true,
            hasBattery = true,
            hasMeter = true,
            errorMessage = null
        )
    }

    override fun disconnect() {
        connected = false
    }

    override fun isConnected(): Boolean = connected
}
