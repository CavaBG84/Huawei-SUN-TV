package com.huaweitv.solar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaweitv.solar.data.coffee.CoffeePromptManager
import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.ConnectionState
import com.huaweitv.solar.data.model.ShellyGen
import com.huaweitv.solar.data.model.SolarTelemetry
import com.huaweitv.solar.data.powerguard.AlarmSoundController
import com.huaweitv.solar.data.powerguard.PowerBand
import com.huaweitv.solar.data.powerguard.PowerGuardEvaluation
import com.huaweitv.solar.data.powerguard.PowerGuardManager
import com.huaweitv.solar.data.powerguard.PowerGuardState
import com.huaweitv.solar.data.repository.SettingsRepository
import com.huaweitv.solar.data.repository.SolarRepository
import com.huaweitv.solar.data.shelly.ShellyController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SolarUiState(
    val solarData: SolarTelemetry = SolarTelemetry(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val powerGuardState: PowerGuardState = PowerGuardState(),
    val isSettingsOpen: Boolean = false,
    val isCoffeePromptOpen: Boolean = false
)

class SolarViewModel(
    private val solarRepository: SolarRepository,
    private val settingsRepository: SettingsRepository,
    private val coffeePromptManager: CoffeePromptManager? = null
) : ViewModel() {

    private val isSettingsOpenFlow = MutableStateFlow(false)
    private val powerGuardManager = PowerGuardManager()
    private val alarmSoundController = AlarmSoundController()
    private val shellyController = ShellyController()
    private val powerGuardStateFlow = MutableStateFlow(PowerGuardState())
    private val isCoffeePromptOpenFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                solarRepository.solarData,
                settingsRepository.settingsFlow
            ) { telemetry, settings ->
                telemetry to settings
            }.collect { (telemetry, settings) ->
                handleEvaluation(powerGuardManager.evaluate(telemetry, settings), settings)
            }
        }

        // 1-second ticker for smooth countdowns, critical beep intervals, and anti-spunto delay
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val state = powerGuardManager.currentState
                val settings = settingsRepository.settingsFlow.value
                val needsTick = state.band != PowerBand.NORMAL ||
                    state.isTestSimulation ||
                    state.shellyPendingDelaySec > 0 ||
                    (settings.shellySheddingEnabled && !state.isShedded)
                if (needsTick) {
                    handleEvaluation(powerGuardManager.tick(settings), settings)
                }
            }
        }
    }

    val uiState: StateFlow<SolarUiState> = combine(
        combine(
            solarRepository.solarData,
            solarRepository.connectionState,
            settingsRepository.settingsFlow
        ) { solarData, connectionState, settings ->
            Triple(solarData, connectionState, settings)
        },
        combine(
            powerGuardStateFlow,
            isSettingsOpenFlow,
            isCoffeePromptOpenFlow
        ) { powerGuardState, isSettingsOpen, isCoffeePromptOpen ->
            Triple(powerGuardState, isSettingsOpen, isCoffeePromptOpen)
        }
    ) { (solarData, connectionState, settings), (powerGuardState, isSettingsOpen, isCoffeePromptOpen) ->
        SolarUiState(
            solarData = solarData,
            connectionState = connectionState,
            settings = settings,
            powerGuardState = powerGuardState,
            isSettingsOpen = isSettingsOpen,
            isCoffeePromptOpen = isCoffeePromptOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SolarUiState()
    )

    private fun handleEvaluation(evaluation: PowerGuardEvaluation, settings: AppSettings) {
        powerGuardStateFlow.value = evaluation.state

        if (evaluation.shouldTriggerCriticalBeep) {
            alarmSoundController.playCriticalBeep()
        } else if (evaluation.shouldTriggerWarningBeep) {
            alarmSoundController.playWarningBeep()
        }

        if (evaluation.shouldTriggerShellyShed) {
            val loadName = settings.shelly1Name
            viewModelScope.launch {
                val result = shellyController.turnOff(settings)
                powerGuardManager.onShellyCommandResult(
                    success = result.isSuccess,
                    loadName = loadName,
                    errorDetail = result.exceptionOrNull()?.localizedMessage
                )
                powerGuardStateFlow.value = powerGuardManager.currentState
            }
        }
    }

    fun mutePowerGuardAlarm() {
        powerGuardManager.muteAlarmForSession()
        powerGuardStateFlow.value = powerGuardManager.currentState
    }

    fun resetShellyShed() {
        powerGuardManager.resetShellyShed()
        powerGuardStateFlow.value = powerGuardManager.currentState
    }

    fun startPowerAlarmTest(contractPowerKw: Double) {
        val settings = settingsRepository.settingsFlow.value
        val eval = powerGuardManager.startAlarmTest(contractPowerKw, settings)
        handleEvaluation(eval, settings)
    }

    suspend fun testShellyTurnOff(ip: String, channel: Int, gen: ShellyGen): Result<Unit> {
        return shellyController.turnOff(ip = ip, channel = channel, gen = gen)
    }

    fun openSettings() {
        isSettingsOpenFlow.value = true
    }

    fun closeSettings() {
        isSettingsOpenFlow.value = false
    }

    fun saveSettings(newSettings: AppSettings) {
        settingsRepository.updateSettings(newSettings)
        isSettingsOpenFlow.value = false
    }

    fun toggleMockMode() {
        val currentMock = uiState.value.settings.mockMode
        viewModelScope.launch {
            settingsRepository.setMockMode(!currentMock)
        }
    }

    fun retryConnection() {
        solarRepository.retryConnection()
    }

    fun checkCoffeePromptIfNeeded() {
        if (isSettingsOpenFlow.value || isCoffeePromptOpenFlow.value) return
        if (coffeePromptManager?.shouldShowCoffeePrompt() == true) {
            isCoffeePromptOpenFlow.value = true
        }
    }

    fun snoozeCoffeePrompt() {
        coffeePromptManager?.recordSnoozed()
        isCoffeePromptOpenFlow.value = false
    }

    fun dismissCoffeePromptForever() {
        coffeePromptManager?.recordDismissedForever()
    }

    fun closeCoffeePromptAfterThankYou() {
        isCoffeePromptOpenFlow.value = false
    }

    override fun onCleared() {
        super.onCleared()
        alarmSoundController.release()
        solarRepository.disconnect()
    }
}
