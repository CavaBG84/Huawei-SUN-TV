package com.huaweitv.solar.data.modbus

/**
 * Modbus TCP Register mapping for Huawei SUN2000 inverters and LUNA2000 ESS.
 */
object HuaweiRegisters {
    /** Function Code 03: Read Holding Registers */
    const val FC_READ_HOLDING_REGISTERS = 0x03

    /** PV Input Power in Watts (INT32, 2 registers) */
    const val REG_PV_INPUT_POWER = 32064

    /** Inverter Active Power AC in Watts (INT32, 2 registers) */
    const val REG_ACTIVE_POWER = 32080

    /** Daily Yield in kWh (UINT32, 2 registers, scale factor: divide by 100.0) */
    const val REG_DAILY_YIELD = 32114

    /**
     * Power Meter Active Power in Watts (INT32, 2 registers)
     * Positive (+) = Export to grid, Negative (-) = Import from grid
     */
    const val REG_GRID_POWER = 37113

    /** Battery State of Charge in % (UINT16, 1 register, scale factor: divide by 10.0) */
    const val REG_BATTERY_SOC = 37760

    /**
     * Battery Charge/Discharge Power in Watts (INT32, 2 registers)
     * Positive (+) = Charge, Negative (-) = Discharge
     */
    const val REG_BATTERY_POWER = 37765

    /** Battery Current-Day Charge Capacity in kWh (UINT32, 2 registers, scale factor: divide by 100.0) */
    const val REG_BATTERY_DAY_CHARGE = 37015

    /** Battery Current-Day Discharge Capacity in kWh (UINT32, 2 registers, scale factor: divide by 100.0) */
    const val REG_BATTERY_DAY_DISCHARGE = 37017

    /** Quantity of contiguous registers for battery daily charge + discharge (37015..37018) */
    const val REG_BATTERY_DAY_ENERGY_QTY = 4

    /** Huawei Modbus sentinel for unavailable/uninitialized UINT32 registers */
    const val UINT32_INVALID = 0xFFFFFFFFL
}
