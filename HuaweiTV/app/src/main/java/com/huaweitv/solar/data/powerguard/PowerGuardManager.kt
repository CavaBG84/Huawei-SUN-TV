package com.huaweitv.solar.data.powerguard

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.SolarTelemetry
import kotlin.math.ceil

enum class PowerBand {
    NORMAL,
    WARNING,
    CRITICAL
}

enum class ShellyStatusKind {
    NONE,
    SHEDDING_IN_PROGRESS,
    SHED_SUCCESS,
    SHED_ERROR
}

data class PowerGuardState(
    val band: PowerBand = PowerBand.NORMAL,
    val importPowerKw: Double = 0.0,
    val contractPowerKw: Double = 3.0,
    val warningThresholdKw: Double = 3.30,
    val criticalThresholdKw: Double = 3.99,
    val remainingSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val shellyPendingDelaySec: Int = 0,
    val isShedded: Boolean = false,
    val shedLoadName: String = "",
    val shellyStatusMessage: String? = null,
    val shellyStatusKind: ShellyStatusKind = ShellyStatusKind.NONE,
    val shellyErrorDetail: String? = null,
    val shellyActionInProgress: Boolean = false,
    val isTestSimulation: Boolean = false
) {
    val shouldShowBanner: Boolean
        get() = band != PowerBand.NORMAL || isShedded || shellyStatusMessage != null || isTestSimulation
}

data class PowerGuardEvaluation(
    val state: PowerGuardState,
    val shouldTriggerWarningBeep: Boolean = false,
    val shouldTriggerCriticalBeep: Boolean = false,
    val shouldTriggerShellyShed: Boolean = false
)

/**
 * Stateful power-threshold monitor and Shelly anti-spunto load-shedding coordinator.
 *
 * Thresholds based on contracted power (Open Meter 2G rules):
 * - NORMAL:   importPowerKw <= contractPowerKw * 1.10
 * - WARNING:  contractPowerKw * 1.10 < importPowerKw <= contractPowerKw * 1.33 (180 min / 10,800s countdown)
 * - CRITICAL: importPowerKw > contractPowerKw * 1.33 (120s / 2 min emergency countdown)
 */
class PowerGuardManager(
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val WARNING_TOLERANCE_FACTOR = 1.10
        const val CRITICAL_TOLERANCE_FACTOR = 1.33
        const val WARNING_DURATION_SEC = 10_800L // 180 minutes
        const val CRITICAL_DURATION_SEC = 120L   // 2 minutes
        const val WARNING_BEEP_COOLDOWN_MS = 60_000L
        const val CRITICAL_BEEP_INTERVAL_MS = 4_000L
        const val TEST_ALARM_DURATION_MS = 8_000L
        const val TEST_IMPORT_FACTOR = 1.40
        private const val EPSILON = 1e-6
    }

    private var warningStartMs: Long? = null
    private var criticalStartMs: Long? = null
    private var lastWarningBeepMs: Long = Long.MIN_VALUE
    private var lastCriticalBeepMs: Long = Long.MIN_VALUE
    private var shellyExceedStartMs: Long? = null

    private var testActive: Boolean = false
    private var testStartMs: Long = 0L
    private var testContractKw: Double = 3.0
    private var lastTestCriticalBeepMs: Long = Long.MIN_VALUE

    private var lastTelemetry: SolarTelemetry? = null
    private var lastSettings: AppSettings = AppSettings()

    var currentState: PowerGuardState = PowerGuardState()
        private set

    fun evaluate(telemetry: SolarTelemetry, settings: AppSettings): PowerGuardEvaluation {
        lastTelemetry = telemetry
        lastSettings = settings
        return evaluateInternal(telemetry, settings, nowMsProvider())
    }

    /**
     * Advances internal countdowns using the latest telemetry & settings (e.g. every 1s tick).
     */
    fun tick(settings: AppSettings): PowerGuardEvaluation {
        lastSettings = settings
        val telemetry = lastTelemetry ?: if (testActive) SolarTelemetry() else return PowerGuardEvaluation(currentState)
        return evaluateInternal(telemetry, settings, nowMsProvider())
    }

    /**
     * Starts an 8-second CRITICAL simulation (contractPowerKw * 1.40) for banner + acoustic test.
     * Temporarily ignores meter_alarm_enabled == false and never triggers Shelly shedding.
     */
    fun startAlarmTest(contractPowerKwOverride: Double, settings: AppSettings): PowerGuardEvaluation {
        lastSettings = settings
        val nowMs = nowMsProvider()
        testActive = true
        testStartMs = nowMs
        testContractKw = when {
            contractPowerKwOverride > 0.0 -> contractPowerKwOverride
            settings.contractPowerKw > 0.0 -> settings.contractPowerKw
            else -> 3.0
        }
        lastTestCriticalBeepMs = Long.MIN_VALUE
        val telemetry = lastTelemetry ?: SolarTelemetry()
        return evaluateInternal(telemetry, settings, nowMs)
    }

    fun muteAlarmForSession() {
        if (testActive) {
            testActive = false
            val telemetry = lastTelemetry ?: SolarTelemetry()
            evaluateInternal(telemetry, lastSettings, nowMsProvider())
            return
        }
        currentState = currentState.copy(isMuted = true)
    }

    fun resetShellyShed() {
        shellyExceedStartMs = null
        currentState = currentState.copy(
            isShedded = false,
            shellyStatusMessage = null,
            shellyStatusKind = ShellyStatusKind.NONE,
            shellyErrorDetail = null,
            shellyActionInProgress = false,
            shellyPendingDelaySec = 0
        )
    }

    fun onShellyCommandResult(success: Boolean, loadName: String, errorDetail: String?) {
        val displayName = loadName.ifBlank { "Shelly" }
        currentState = currentState.copy(
            isShedded = true,
            shedLoadName = displayName,
            shellyActionInProgress = false,
            shellyPendingDelaySec = 0,
            shellyStatusKind = if (success) ShellyStatusKind.SHED_SUCCESS else ShellyStatusKind.SHED_ERROR,
            shellyErrorDetail = if (success) null else errorDetail,
            shellyStatusMessage = if (success) {
                "Carico staccato: $displayName"
            } else {
                "Errore distacco $displayName: ${errorDetail ?: "rete non raggiungibile"}"
            }
        )
    }

    private fun evaluateInternal(
        telemetry: SolarTelemetry,
        settings: AppSettings,
        nowMs: Long
    ): PowerGuardEvaluation {
        if (testActive) {
            val testElapsedMs = (nowMs - testStartMs).coerceAtLeast(0L)
            if (testElapsedMs >= TEST_ALARM_DURATION_MS) {
                testActive = false
            } else {
                val warningKw = testContractKw * WARNING_TOLERANCE_FACTOR
                val criticalKw = testContractKw * CRITICAL_TOLERANCE_FACTOR
                val simulatedImportKw = testContractKw * TEST_IMPORT_FACTOR
                val elapsedSec = testElapsedMs / 1000L
                val remainingSec = (CRITICAL_DURATION_SEC - elapsedSec).coerceAtLeast(0L)

                var triggerTestBeep = false
                if (lastTestCriticalBeepMs == Long.MIN_VALUE ||
                    (nowMs - lastTestCriticalBeepMs) >= CRITICAL_BEEP_INTERVAL_MS
                ) {
                    triggerTestBeep = true
                    lastTestCriticalBeepMs = nowMs
                }

                val testState = PowerGuardState(
                    band = PowerBand.CRITICAL,
                    importPowerKw = simulatedImportKw,
                    contractPowerKw = testContractKw,
                    warningThresholdKw = warningKw,
                    criticalThresholdKw = criticalKw,
                    remainingSeconds = remainingSec,
                    isMuted = false,
                    shellyPendingDelaySec = 0,
                    isShedded = currentState.isShedded,
                    shedLoadName = currentState.shedLoadName,
                    shellyStatusMessage = currentState.shellyStatusMessage,
                    shellyStatusKind = currentState.shellyStatusKind,
                    shellyErrorDetail = currentState.shellyErrorDetail,
                    shellyActionInProgress = currentState.shellyActionInProgress,
                    isTestSimulation = true
                )
                currentState = testState
                return PowerGuardEvaluation(
                    state = testState,
                    shouldTriggerWarningBeep = false,
                    shouldTriggerCriticalBeep = triggerTestBeep,
                    shouldTriggerShellyShed = false
                )
            }
        }

        val contractKw = if (settings.contractPowerKw > 0.0) settings.contractPowerKw else 3.0
        val warningThresholdKw = contractKw * WARNING_TOLERANCE_FACTOR
        val criticalThresholdKw = contractKw * CRITICAL_TOLERANCE_FACTOR

        // Import power is positive only when meter is valid and gridPowerW < 0 (import)
        val importKw = if (telemetry.hasMeter && telemetry.isConnected && telemetry.gridPowerW < 0) {
            (-telemetry.gridPowerW) / 1000.0
        } else {
            0.0
        }

        val previousBand = currentState.band
        var newBand = PowerBand.NORMAL
        var remainingSec = 0L
        var isMuted = currentState.isMuted
        var triggerWarningBeep = false
        var triggerCriticalBeep = false

        if (!settings.meterAlarmEnabled) {
            warningStartMs = null
            criticalStartMs = null
            isMuted = false
        } else {
            newBand = classifyBand(importKw, warningThresholdKw, criticalThresholdKw)
            when (newBand) {
                PowerBand.NORMAL -> {
                    warningStartMs = null
                    criticalStartMs = null
                    isMuted = false
                    remainingSec = 0L
                }
                PowerBand.WARNING -> {
                    criticalStartMs = null
                    if (warningStartMs == null) {
                        warningStartMs = nowMs
                    }
                    val elapsedSec = ((nowMs - warningStartMs!!) / 1000L).coerceAtLeast(0L)
                    remainingSec = (WARNING_DURATION_SEC - elapsedSec).coerceAtLeast(0L)

                    val soundEnabled = settings.meterAlarmSoundEnabled && !isMuted
                    if (soundEnabled && previousBand != PowerBand.WARNING) {
                        if (lastWarningBeepMs == Long.MIN_VALUE || (nowMs - lastWarningBeepMs) >= WARNING_BEEP_COOLDOWN_MS) {
                            triggerWarningBeep = true
                            lastWarningBeepMs = nowMs
                        }
                    }
                }
                PowerBand.CRITICAL -> {
                    if (criticalStartMs == null) {
                        criticalStartMs = nowMs
                    }
                    val elapsedSec = ((nowMs - criticalStartMs!!) / 1000L).coerceAtLeast(0L)
                    remainingSec = (CRITICAL_DURATION_SEC - elapsedSec).coerceAtLeast(0L)

                    val soundEnabled = settings.meterAlarmSoundEnabled && !isMuted
                    if (soundEnabled) {
                        if (previousBand != PowerBand.CRITICAL ||
                            lastCriticalBeepMs == Long.MIN_VALUE ||
                            (nowMs - lastCriticalBeepMs) >= CRITICAL_BEEP_INTERVAL_MS
                        ) {
                            triggerCriticalBeep = true
                            lastCriticalBeepMs = nowMs
                        }
                    }
                }
            }
        }

        // Shelly anti-spunto & load-shedding evaluation
        var triggerShellyShed = false
        var shellyPendingDelaySec = 0
        var isShedded = currentState.isShedded
        var shedLoadName = currentState.shedLoadName
        var shellyStatusMessage = currentState.shellyStatusMessage
        var shellyStatusKind = currentState.shellyStatusKind
        var shellyErrorDetail = currentState.shellyErrorDetail
        var shellyActionInProgress = currentState.shellyActionInProgress

        val shellyConfigured = settings.shellySheddingEnabled && settings.shelly1Ip.trim().isNotEmpty()
        if (!shellyConfigured) {
            shellyExceedStartMs = null
            shellyPendingDelaySec = 0
            if (!settings.shellySheddingEnabled) {
                isShedded = false
                shellyStatusMessage = null
                shellyStatusKind = ShellyStatusKind.NONE
                shellyErrorDetail = null
                shellyActionInProgress = false
            }
        } else if (!isShedded && !shellyActionInProgress) {
            if (importKw > warningThresholdKw + EPSILON) {
                if (shellyExceedStartMs == null) {
                    shellyExceedStartMs = nowMs
                }
                val delaySec = settings.shellyTriggerDelaySec.coerceAtLeast(1)
                val elapsedMs = (nowMs - shellyExceedStartMs!!).coerceAtLeast(0L)
                val requiredMs = delaySec * 1000L
                if (elapsedMs >= requiredMs) {
                    triggerShellyShed = true
                    isShedded = true
                    shellyActionInProgress = true
                    shedLoadName = settings.shelly1Name.ifBlank { "Shelly" }
                    shellyStatusKind = ShellyStatusKind.SHEDDING_IN_PROGRESS
                    shellyErrorDetail = null
                    shellyStatusMessage = "Distacco in corso: $shedLoadName..."
                    shellyPendingDelaySec = 0
                } else {
                    val remainingMs = requiredMs - elapsedMs
                    shellyPendingDelaySec = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
                }
            } else {
                // Anti-spunto reset: power dropped back within normal threshold before delay elapsed
                shellyExceedStartMs = null
                shellyPendingDelaySec = 0
            }
        }

        val nextState = PowerGuardState(
            band = newBand,
            importPowerKw = importKw,
            contractPowerKw = contractKw,
            warningThresholdKw = warningThresholdKw,
            criticalThresholdKw = criticalThresholdKw,
            remainingSeconds = remainingSec,
            isMuted = isMuted,
            shellyPendingDelaySec = shellyPendingDelaySec,
            isShedded = isShedded,
            shedLoadName = shedLoadName,
            shellyStatusMessage = shellyStatusMessage,
            shellyStatusKind = shellyStatusKind,
            shellyErrorDetail = shellyErrorDetail,
            shellyActionInProgress = shellyActionInProgress
        )
        currentState = nextState

        return PowerGuardEvaluation(
            state = nextState,
            shouldTriggerWarningBeep = triggerWarningBeep,
            shouldTriggerCriticalBeep = triggerCriticalBeep,
            shouldTriggerShellyShed = triggerShellyShed
        )
    }

    private fun classifyBand(
        importKw: Double,
        warningThresholdKw: Double,
        criticalThresholdKw: Double
    ): PowerBand {
        return when {
            importKw > criticalThresholdKw + EPSILON -> PowerBand.CRITICAL
            importKw > warningThresholdKw + EPSILON -> PowerBand.WARNING
            else -> PowerBand.NORMAL
        }
    }
}
