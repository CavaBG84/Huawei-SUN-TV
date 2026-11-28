package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.SolarTelemetry

/**
 * Brand-agnostic inverter data source abstraction.
 * Implementations handle local LAN transport (Modbus TCP, HTTP Solar API v1, etc.)
 * and normalize readings into [SolarTelemetry].
 */
interface InverterDataSource {
    val brand: InverterBrand

    suspend fun connect()

    suspend fun fetchTelemetry(): SolarTelemetry

    fun disconnect()

    fun isConnected(): Boolean
}
