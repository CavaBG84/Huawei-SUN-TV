package com.huaweitv.solar.powerguard;

import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.SolarTelemetry;

/**
 * Stateful power-threshold monitor and Shelly anti-spunto load-shedding coordinator.
 * Pure Java, zero external dependencies (Android 4.4.4 KitKat compatible).
 */
public class PowerGuardManager {

    public interface Clock {
        long nowMs();
    }

    public static final double WARNING_TOLERANCE_FACTOR = 1.10;
    public static final double CRITICAL_TOLERANCE_FACTOR = 1.33;
    public static final long WARNING_DURATION_SEC = 10_800L; // 180 minutes
    public static final long CRITICAL_DURATION_SEC = 120L;   // 2 minutes
    public static final long WARNING_BEEP_COOLDOWN_MS = 60_000L;
    public static final long CRITICAL_BEEP_INTERVAL_MS = 4_000L;
    public static final long TEST_ALARM_DURATION_MS = 8_000L;
    public static final double TEST_IMPORT_FACTOR = 1.40;
    private static final double EPSILON = 1e-6;

    private final Clock clock;

    private Long warningStartMs = null;
    private Long criticalStartMs = null;
    private long lastWarningBeepMs = Long.MIN_VALUE;
    private long lastCriticalBeepMs = Long.MIN_VALUE;
    private Long shellyExceedStartMs = null;

    private boolean testActive = false;
    private long testStartMs = 0L;
    private double testContractKw = 3.0;
    private long lastTestCriticalBeepMs = Long.MIN_VALUE;

    private SolarTelemetry lastTelemetry = null;
    private AppSettings lastSettings = null;
    private PowerGuardState currentState = new PowerGuardState();

    public PowerGuardManager() {
        this(System::currentTimeMillis);
    }

    public PowerGuardManager(Clock clock) {
        this.clock = clock;
    }

    public synchronized PowerGuardState getCurrentState() {
        return currentState;
    }

    public synchronized PowerGuardEvaluation evaluate(SolarTelemetry telemetry, AppSettings settings) {
        this.lastTelemetry = telemetry;
        this.lastSettings = settings;
        return evaluateInternal(telemetry, settings, clock.nowMs());
    }

    public synchronized PowerGuardEvaluation tick(AppSettings settings) {
        this.lastSettings = settings;
        if (lastTelemetry == null && !testActive) {
            return new PowerGuardEvaluation(currentState, false, false, false);
        }
        return evaluateInternal(lastTelemetry, settings, clock.nowMs());
    }

    public synchronized PowerGuardEvaluation startAlarmTest(double contractPowerKwOverride, AppSettings settings) {
        this.lastSettings = settings;
        long nowMs = clock.nowMs();
        this.testActive = true;
        this.testStartMs = nowMs;
        if (contractPowerKwOverride > 0.0) {
            this.testContractKw = contractPowerKwOverride;
        } else if (settings != null && settings.contractPowerKw > 0.0) {
            this.testContractKw = settings.contractPowerKw;
        } else {
            this.testContractKw = 3.0;
        }
        this.lastTestCriticalBeepMs = Long.MIN_VALUE;
        return evaluateInternal(lastTelemetry, settings, nowMs);
    }

    public synchronized void muteAlarmForSession() {
        if (testActive) {
            testActive = false;
            evaluateInternal(lastTelemetry, lastSettings, clock.nowMs());
            return;
        }
        PowerGuardState s = currentState;
        currentState = new PowerGuardState(
            s.band, s.importPowerKw, s.contractPowerKw, s.warningThresholdKw, s.criticalThresholdKw,
            s.remainingSeconds, true, s.shellyPendingDelaySec, s.isShedded, s.shedLoadName,
            s.shellyStatusMessage, s.shellyStatusKind, s.shellyErrorDetail, s.shellyActionInProgress,
            s.isTestSimulation
        );
    }

    public synchronized void resetShellyShed() {
        shellyExceedStartMs = null;
        PowerGuardState s = currentState;
        currentState = new PowerGuardState(
            s.band, s.importPowerKw, s.contractPowerKw, s.warningThresholdKw, s.criticalThresholdKw,
            s.remainingSeconds, s.isMuted, 0, false, s.shedLoadName, null,
            ShellyStatusKind.NONE, null, false
        );
    }

    public synchronized void onShellyCommandResult(boolean success, String loadName, String errorDetail) {
        String displayName = (loadName != null && !loadName.trim().isEmpty()) ? loadName.trim() : "Shelly";
        String msg = success
            ? ("Carico staccato: " + displayName)
            : ("Errore distacco " + displayName + ": " + (errorDetail != null ? errorDetail : "rete non raggiungibile"));
        PowerGuardState s = currentState;
        currentState = new PowerGuardState(
            s.band, s.importPowerKw, s.contractPowerKw, s.warningThresholdKw, s.criticalThresholdKw,
            s.remainingSeconds, s.isMuted, 0, true, displayName, msg,
            success ? ShellyStatusKind.SHED_SUCCESS : ShellyStatusKind.SHED_ERROR,
            success ? null : errorDetail,
            false
        );
    }

    private PowerGuardEvaluation evaluateInternal(SolarTelemetry telemetry, AppSettings settings, long nowMs) {
        if (testActive) {
            long testElapsedMs = Math.max(0L, nowMs - testStartMs);
            if (testElapsedMs >= TEST_ALARM_DURATION_MS) {
                testActive = false;
            } else {
                double warningKw = testContractKw * WARNING_TOLERANCE_FACTOR;
                double criticalKw = testContractKw * CRITICAL_TOLERANCE_FACTOR;
                double simulatedImportKw = testContractKw * TEST_IMPORT_FACTOR;
                long elapsedSec = testElapsedMs / 1000L;
                long remainingSec = Math.max(0L, CRITICAL_DURATION_SEC - elapsedSec);

                boolean triggerTestBeep = false;
                if (lastTestCriticalBeepMs == Long.MIN_VALUE
                    || (nowMs - lastTestCriticalBeepMs) >= CRITICAL_BEEP_INTERVAL_MS) {
                    triggerTestBeep = true;
                    lastTestCriticalBeepMs = nowMs;
                }

                PowerGuardState testState = new PowerGuardState(
                    PowerBand.CRITICAL,
                    simulatedImportKw,
                    testContractKw,
                    warningKw,
                    criticalKw,
                    remainingSec,
                    false,
                    0,
                    currentState.isShedded,
                    currentState.shedLoadName,
                    currentState.shellyStatusMessage,
                    currentState.shellyStatusKind,
                    currentState.shellyErrorDetail,
                    currentState.shellyActionInProgress,
                    true
                );
                currentState = testState;
                return new PowerGuardEvaluation(testState, false, triggerTestBeep, false);
            }
        }

        double contractKw = (settings != null && settings.contractPowerKw > 0.0) ? settings.contractPowerKw : 3.0;
        double warningThresholdKw = contractKw * WARNING_TOLERANCE_FACTOR;
        double criticalThresholdKw = contractKw * CRITICAL_TOLERANCE_FACTOR;

        double importKw = 0.0;
        if (telemetry != null && telemetry.hasMeter && telemetry.isConnected && telemetry.gridPowerW < 0) {
            importKw = (-telemetry.gridPowerW) / 1000.0;
        }

        PowerBand previousBand = currentState.band;
        PowerBand newBand = PowerBand.NORMAL;
        long remainingSec = 0L;
        boolean isMuted = currentState.isMuted;
        boolean triggerWarningBeep = false;
        boolean triggerCriticalBeep = false;

        boolean alarmEnabled = settings != null && settings.meterAlarmEnabled;
        boolean alarmSoundEnabled = settings != null && settings.meterAlarmSoundEnabled;

        if (!alarmEnabled) {
            warningStartMs = null;
            criticalStartMs = null;
            isMuted = false;
        } else {
            newBand = classifyBand(importKw, warningThresholdKw, criticalThresholdKw);
            if (newBand == PowerBand.NORMAL) {
                warningStartMs = null;
                criticalStartMs = null;
                isMuted = false;
                remainingSec = 0L;
            } else if (newBand == PowerBand.WARNING) {
                criticalStartMs = null;
                if (warningStartMs == null) {
                    warningStartMs = nowMs;
                }
                long elapsedSec = Math.max(0L, (nowMs - warningStartMs) / 1000L);
                remainingSec = Math.max(0L, WARNING_DURATION_SEC - elapsedSec);

                boolean canBeep = alarmSoundEnabled && !isMuted;
                if (canBeep && previousBand != PowerBand.WARNING) {
                    if (lastWarningBeepMs == Long.MIN_VALUE || (nowMs - lastWarningBeepMs) >= WARNING_BEEP_COOLDOWN_MS) {
                        triggerWarningBeep = true;
                        lastWarningBeepMs = nowMs;
                    }
                }
            } else { // CRITICAL
                if (criticalStartMs == null) {
                    criticalStartMs = nowMs;
                }
                long elapsedSec = Math.max(0L, (nowMs - criticalStartMs) / 1000L);
                remainingSec = Math.max(0L, CRITICAL_DURATION_SEC - elapsedSec);

                boolean canBeep = alarmSoundEnabled && !isMuted;
                if (canBeep) {
                    if (previousBand != PowerBand.CRITICAL
                        || lastCriticalBeepMs == Long.MIN_VALUE
                        || (nowMs - lastCriticalBeepMs) >= CRITICAL_BEEP_INTERVAL_MS) {
                        triggerCriticalBeep = true;
                        lastCriticalBeepMs = nowMs;
                    }
                }
            }
        }

        boolean triggerShellyShed = false;
        int shellyPendingDelaySec = 0;
        boolean isShedded = currentState.isShedded;
        String shedLoadName = currentState.shedLoadName;
        String shellyStatusMessage = currentState.shellyStatusMessage;
        ShellyStatusKind shellyStatusKind = currentState.shellyStatusKind;
        String shellyErrorDetail = currentState.shellyErrorDetail;
        boolean shellyActionInProgress = currentState.shellyActionInProgress;

        boolean shellyConfigured = settings != null
            && settings.shellySheddingEnabled
            && settings.shelly1Ip != null
            && !settings.shelly1Ip.trim().isEmpty();

        if (!shellyConfigured) {
            shellyExceedStartMs = null;
            shellyPendingDelaySec = 0;
            if (settings == null || !settings.shellySheddingEnabled) {
                isShedded = false;
                shellyStatusMessage = null;
                shellyStatusKind = ShellyStatusKind.NONE;
                shellyErrorDetail = null;
                shellyActionInProgress = false;
            }
        } else if (!isShedded && !shellyActionInProgress) {
            if (importKw > warningThresholdKw + EPSILON) {
                if (shellyExceedStartMs == null) {
                    shellyExceedStartMs = nowMs;
                }
                int delaySec = Math.max(1, settings.shellyTriggerDelaySec);
                long elapsedMs = Math.max(0L, nowMs - shellyExceedStartMs);
                long requiredMs = delaySec * 1000L;
                if (elapsedMs >= requiredMs) {
                    triggerShellyShed = true;
                    isShedded = true;
                    shellyActionInProgress = true;
                    shedLoadName = (settings.shelly1Name != null && !settings.shelly1Name.trim().isEmpty())
                        ? settings.shelly1Name.trim()
                        : "Shelly";
                    shellyStatusMessage = "Distacco in corso: " + shedLoadName + "...";
                    shellyStatusKind = ShellyStatusKind.SHEDDING_IN_PROGRESS;
                    shellyErrorDetail = null;
                    shellyPendingDelaySec = 0;
                } else {
                    long remainingMs = requiredMs - elapsedMs;
                    shellyPendingDelaySec = Math.max(1, (int) Math.ceil(remainingMs / 1000.0));
                }
            } else {
                shellyExceedStartMs = null;
                shellyPendingDelaySec = 0;
            }
        }

        PowerGuardState nextState = new PowerGuardState(
            newBand,
            importKw,
            contractKw,
            warningThresholdKw,
            criticalThresholdKw,
            remainingSec,
            isMuted,
            shellyPendingDelaySec,
            isShedded,
            shedLoadName,
            shellyStatusMessage,
            shellyStatusKind,
            shellyErrorDetail,
            shellyActionInProgress
        );
        currentState = nextState;

        return new PowerGuardEvaluation(
            nextState,
            triggerWarningBeep,
            triggerCriticalBeep,
            triggerShellyShed
        );
    }

    private static PowerBand classifyBand(double importKw, double warningThresholdKw, double criticalThresholdKw) {
        if (importKw > criticalThresholdKw + EPSILON) {
            return PowerBand.CRITICAL;
        }
        if (importKw > warningThresholdKw + EPSILON) {
            return PowerBand.WARNING;
        }
        return PowerBand.NORMAL;
    }
}
