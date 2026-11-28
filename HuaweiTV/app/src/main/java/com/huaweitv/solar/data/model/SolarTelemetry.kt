package com.huaweitv.solar.data.model

import java.util.Locale
import kotlin.math.abs

/**
 * Brand-agnostic unified telemetry snapshot passed to the SolarDeck UI.
 *
 * Sign conventions (uniform across all inverter brands):
 * - pvPowerW: >= 0 (W)
 * - loadPowerW: >= 0 (W, normalized house consumption)
 * - gridPowerW: + = Export to grid (Immissione), - = Import from grid (Prelievo)
 * - batteryPowerW: + = Charging (Carica), - = Discharging (Scarica)
 * - dailyYieldKWh: >= 0.0 (kWh)
 * - batterySoc: 0.0..100.0 (%)
 */
data class SolarTelemetry(
    val pvPowerW: Int = 0,
    val activePowerW: Int = 0,
    val dailyYieldKWh: Double = 0.0,
    val gridPowerW: Int = 0,
    val batterySoc: Double = 0.0,
    val batteryPowerW: Int = 0,
    val loadPowerW: Int = (activePowerW - gridPowerW).coerceAtLeast(0),
    val timestamp: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val hasBattery: Boolean = true,
    val hasMeter: Boolean = true,
    val errorMessage: String? = null
) {
    /** Alias preserved for UI components displaying house consumption in Watts. */
    val houseConsumptionW: Int
        get() = loadPowerW

    val isGridExporting: Boolean get() = gridPowerW > 10
    val isGridImporting: Boolean get() = gridPowerW < -10

    val isBatteryCharging: Boolean get() = batteryPowerW > 10
    val isBatteryDischarging: Boolean get() = batteryPowerW < -10

    fun formatWattsOrKw(watts: Int): String {
        val absWatts = abs(watts)
        return if (absWatts >= 1000) {
            String.format(Locale.US, "%.2f kW", absWatts / 1000.0)
        } else {
            "$absWatts W"
        }
    }

    val formattedPvPower: String get() = formatWattsOrKw(pvPowerW)
    val formattedHousePower: String get() = formatWattsOrKw(loadPowerW)
    val formattedGridPower: String get() = formatWattsOrKw(gridPowerW)
    val formattedBatteryPower: String get() = formatWattsOrKw(batteryPowerW)
    val formattedDailyYield: String get() = String.format(Locale.US, "%.2f kWh", dailyYieldKWh)
    val formattedBatterySoc: String get() = String.format(Locale.US, "%.1f%%", batterySoc)
}
