package com.huaweitv.solar.data.repository

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.ConnectionState
import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.SolarTelemetry
import com.huaweitv.solar.data.source.InverterDataSource
import com.huaweitv.solar.data.source.InverterDataSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Multi-inverter repository orchestrating [InverterDataSource] polling and simulated Mock mode.
 */
class SolarRepository(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val dataSourceFactory: (AppSettings) -> InverterDataSource = InverterDataSourceFactory::create
) {
    private var activeDataSource: InverterDataSource? = null

    private val _solarData = MutableStateFlow(SolarTelemetry())
    val solarData: StateFlow<SolarTelemetry> = _solarData.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var workerJob: Job? = null

    init {
        scope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                restartWorker(settings)
            }
        }
    }

    private fun restartWorker(settings: AppSettings) {
        workerJob?.cancel()
        disconnectActiveSource()
        workerJob = scope.launch(Dispatchers.IO) {
            if (settings.mockMode) {
                runMockWorker(settings)
            } else {
                runLiveWorker(settings)
            }
        }
    }

    private suspend fun runLiveWorker(settings: AppSettings) {
        val ip = settings.ipAddress.trim()
        if (ip.isEmpty()) {
            _connectionState.value = ConnectionState.NeedsConfig
            return
        }

        val port = settings.effectivePort()
        val dataSource = dataSourceFactory(settings).also { activeDataSource = it }
        var backoffMs = 2000L
        val maxBackoffMs = 30000L

        while (scope.isActive) {
            try {
                if (!dataSource.isConnected()) {
                    val isFronius = settings.inverterBrand == InverterBrand.FRONIUS
                    val actionLabel = if (isFronius) {
                        "Lettura Fronius $ip:$port..."
                    } else {
                        "Connessione a $ip:$port..."
                    }
                    _connectionState.value = ConnectionState.Connecting(
                        message = actionLabel,
                        ip = ip,
                        port = port,
                        isFronius = isFronius
                    )
                    dataSource.connect()
                }

                val telemetry = dataSource.fetchTelemetry()
                backoffMs = 2000L
                _connectionState.value = ConnectionState.Connected(ip, port)
                _solarData.value = telemetry

                delay(settings.pollingIntervalSeconds.coerceAtLeast(1) * 1000L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dataSource.disconnect()
                val retrySec = (backoffMs / 1000).toInt()
                val errorMsg = e.localizedMessage ?: "Errore I/O sconosciuto"
                _connectionState.value = ConnectionState.Error("Errore connessione: $errorMsg", retrySec)
                _solarData.value = _solarData.value.copy(
                    isConnected = false,
                    errorMessage = errorMsg
                )

                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            }
        }
    }

    private suspend fun runMockWorker(settings: AppSettings) {
        _connectionState.value = ConnectionState.Mock
        val mockSource = dataSourceFactory(settings).also { activeDataSource = it }
        mockSource.connect()

        while (scope.isActive) {
            _solarData.value = mockSource.fetchTelemetry()
            delay(2000L)
        }
    }

    private fun disconnectActiveSource() {
        activeDataSource?.disconnect()
        activeDataSource = null
    }

    fun retryConnection() {
        restartWorker(settingsRepository.settingsFlow.value)
    }

    fun resume() {
        restartWorker(settingsRepository.settingsFlow.value)
    }

    fun disconnect() {
        workerJob?.cancel()
        disconnectActiveSource()
        _connectionState.value = ConnectionState.Disconnected
    }
}
