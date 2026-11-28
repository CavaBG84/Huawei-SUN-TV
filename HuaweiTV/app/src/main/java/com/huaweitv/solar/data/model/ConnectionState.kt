package com.huaweitv.solar.data.model

/**
 * Connection states for inverter communication.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object NeedsConfig : ConnectionState
    data class Connecting(
        val message: String = "",
        val ip: String = "",
        val port: Int = 0,
        val isFronius: Boolean = false
    ) : ConnectionState
    data class Connected(val ip: String, val port: Int) : ConnectionState
    data class Error(val message: String, val retryInSeconds: Int = 0) : ConnectionState
    data object Mock : ConnectionState
}
