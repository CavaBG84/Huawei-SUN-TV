package com.huaweitv.solar.modbus;

/**
 * Huawei Modbus Register mappings for SUN2000 and LUNA2000.
 */
public final class HuaweiRegisters {
    private HuaweiRegisters() {}

    public static final int FC_READ_HOLDING_REGISTERS = 0x03;

    /** PV Input Power (W, INT32, 2 reg) */
    public static final int REG_PV_INPUT_POWER = 32064;

    /** Inverter Active Power AC (W, INT32, 2 reg) */
    public static final int REG_ACTIVE_POWER = 32080;

    /** Daily Yield (kWh, UINT32, 2 reg, divide by 100.0) */
    public static final int REG_DAILY_YIELD = 32114;

    /** Grid Power from Power Meter (W, INT32, 2 reg, positive = export, negative = import) */
    public static final int REG_GRID_POWER = 37113;

    /** Battery State of Charge (%, UINT16, 1 reg, divide by 10.0) */
    public static final int REG_BATTERY_SOC = 37760;

    /** Battery Power (W, INT32, 2 reg, positive = charge, negative = discharge) */
    public static final int REG_BATTERY_POWER = 37765;

    /** Battery Current-Day Charge Capacity (kWh, UINT32, 2 reg, divide by 100.0) */
    public static final int REG_BATTERY_DAY_CHARGE = 37015;

    /** Battery Current-Day Discharge Capacity (kWh, UINT32, 2 reg, divide by 100.0) */
    public static final int REG_BATTERY_DAY_DISCHARGE = 37017;

    /** Quantity of contiguous registers for battery daily charge + discharge (37015..37018) */
    public static final int REG_BATTERY_DAY_ENERGY_QTY = 4;

    /** Huawei Modbus sentinel for unavailable/uninitialized UINT32 registers */
    public static final long UINT32_INVALID = 0xFFFFFFFFL;
}
