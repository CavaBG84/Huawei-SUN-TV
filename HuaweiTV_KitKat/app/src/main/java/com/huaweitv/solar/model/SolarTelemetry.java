package com.huaweitv.solar.model;

import java.util.Locale;

/**
 * Brand-agnostic unified telemetry snapshot passed to the SolarDeck UI.
 * Pure Java with zero external dependencies (Android 4.4.4 KitKat compatible).
 *
 * Sign conventions (uniform across all inverter brands):
 * - pvPowerW: >= 0 (W)
 * - loadPowerW: >= 0 (W, normalized house consumption)
 * - gridPowerW: + = Export to grid (Immissione), - = Import from grid (Prelievo)
 * - batteryPowerW: + = Charging (Carica), - = Discharging (Scarica)
 * - dailyYieldKWh: >= 0.0 (kWh)
 * - batterySoc: 0.0..100.0 (%)
 */
public class SolarTelemetry {
    public int pvPowerW = 0;
    public int activePowerW = 0;
    public int loadPowerW = 0;
    public double dailyYieldKWh = 0.0;
    public int gridPowerW = 0;
    public double batterySoc = 0.0;
    public int batteryPowerW = 0;
    public long timestamp = System.currentTimeMillis();

    public boolean isConnected = false;
    public boolean hasBattery = true;
    public boolean hasMeter = true;
    public String errorMessage = null;

    public SolarTelemetry() {}

    public SolarTelemetry(int pvPowerW, int activePowerW, double dailyYieldKWh, int gridPowerW, double batterySoc, int batteryPowerW) {
        this(
            pvPowerW,
            activePowerW,
            Math.max(0, activePowerW - gridPowerW),
            dailyYieldKWh,
            gridPowerW,
            batterySoc,
            batteryPowerW,
            System.currentTimeMillis(),
            true,
            true,
            true,
            null
        );
    }

    public SolarTelemetry(
        int pvPowerW,
        int activePowerW,
        int loadPowerW,
        double dailyYieldKWh,
        int gridPowerW,
        double batterySoc,
        int batteryPowerW,
        long timestamp,
        boolean isConnected,
        boolean hasBattery,
        boolean hasMeter,
        String errorMessage
    ) {
        this.pvPowerW = pvPowerW;
        this.activePowerW = activePowerW;
        this.loadPowerW = Math.max(0, loadPowerW);
        this.dailyYieldKWh = dailyYieldKWh;
        this.gridPowerW = gridPowerW;
        this.batterySoc = batterySoc;
        this.batteryPowerW = batteryPowerW;
        this.timestamp = timestamp;
        this.isConnected = isConnected;
        this.hasBattery = hasBattery;
        this.hasMeter = hasMeter;
        this.errorMessage = errorMessage;
    }

    public int getHouseConsumptionW() {
        return loadPowerW;
    }

    public boolean isGridExporting() {
        return gridPowerW > 10;
    }

    public boolean isGridImporting() {
        return gridPowerW < -10;
    }

    public boolean isBatteryCharging() {
        return batteryPowerW > 10;
    }

    public boolean isBatteryDischarging() {
        return batteryPowerW < -10;
    }

    public static String formatWattsOrKw(int watts) {
        int absWatts = Math.abs(watts);
        if (absWatts >= 1000) {
            return String.format(Locale.US, "%.2f kW", absWatts / 1000.0);
        } else {
            return absWatts + " W";
        }
    }

    public String getFormattedPvPower() {
        return formatWattsOrKw(pvPowerW);
    }

    public String getFormattedHousePower() {
        return formatWattsOrKw(loadPowerW);
    }

    public String getFormattedGridPower() {
        return formatWattsOrKw(gridPowerW);
    }

    public String getFormattedBatteryPower() {
        return formatWattsOrKw(batteryPowerW);
    }

    public String getFormattedDailyYield() {
        return String.format(Locale.US, "%.2f kWh", dailyYieldKWh);
    }

    public String getFormattedBatterySoc() {
        return String.format(Locale.US, "%.1f%%", batterySoc);
    }
}
