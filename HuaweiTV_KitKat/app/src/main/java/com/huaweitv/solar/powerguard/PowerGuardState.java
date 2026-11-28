package com.huaweitv.solar.powerguard;

/**
 * Immutable snapshot of the current power threshold guard and Shelly shedding state.
 */
public class PowerGuardState {
    public final PowerBand band;
    public final double importPowerKw;
    public final double contractPowerKw;
    public final double warningThresholdKw;
    public final double criticalThresholdKw;
    public final long remainingSeconds;
    public final boolean isMuted;
    public final int shellyPendingDelaySec;
    public final boolean isShedded;
    public final String shedLoadName;
    public final String shellyStatusMessage;
    public final ShellyStatusKind shellyStatusKind;
    public final String shellyErrorDetail;
    public final boolean shellyActionInProgress;
    public final boolean isTestSimulation;

    public PowerGuardState() {
        this(PowerBand.NORMAL, 0.0, 3.0, 3.30, 3.99, 0L, false, 0, false, "", null, ShellyStatusKind.NONE, null, false, false);
    }

    public PowerGuardState(
        PowerBand band,
        double importPowerKw,
        double contractPowerKw,
        double warningThresholdKw,
        double criticalThresholdKw,
        long remainingSeconds,
        boolean isMuted,
        int shellyPendingDelaySec,
        boolean isShedded,
        String shedLoadName,
        String shellyStatusMessage,
        boolean shellyActionInProgress
    ) {
        this(
            band, importPowerKw, contractPowerKw, warningThresholdKw, criticalThresholdKw,
            remainingSeconds, isMuted, shellyPendingDelaySec, isShedded, shedLoadName,
            shellyStatusMessage, ShellyStatusKind.NONE, null, shellyActionInProgress, false
        );
    }

    public PowerGuardState(
        PowerBand band,
        double importPowerKw,
        double contractPowerKw,
        double warningThresholdKw,
        double criticalThresholdKw,
        long remainingSeconds,
        boolean isMuted,
        int shellyPendingDelaySec,
        boolean isShedded,
        String shedLoadName,
        String shellyStatusMessage,
        ShellyStatusKind shellyStatusKind,
        String shellyErrorDetail,
        boolean shellyActionInProgress
    ) {
        this(
            band, importPowerKw, contractPowerKw, warningThresholdKw, criticalThresholdKw,
            remainingSeconds, isMuted, shellyPendingDelaySec, isShedded, shedLoadName,
            shellyStatusMessage, shellyStatusKind, shellyErrorDetail, shellyActionInProgress, false
        );
    }

    public PowerGuardState(
        PowerBand band,
        double importPowerKw,
        double contractPowerKw,
        double warningThresholdKw,
        double criticalThresholdKw,
        long remainingSeconds,
        boolean isMuted,
        int shellyPendingDelaySec,
        boolean isShedded,
        String shedLoadName,
        String shellyStatusMessage,
        ShellyStatusKind shellyStatusKind,
        String shellyErrorDetail,
        boolean shellyActionInProgress,
        boolean isTestSimulation
    ) {
        this.band = band != null ? band : PowerBand.NORMAL;
        this.importPowerKw = importPowerKw;
        this.contractPowerKw = contractPowerKw;
        this.warningThresholdKw = warningThresholdKw;
        this.criticalThresholdKw = criticalThresholdKw;
        this.remainingSeconds = remainingSeconds;
        this.isMuted = isMuted;
        this.shellyPendingDelaySec = shellyPendingDelaySec;
        this.isShedded = isShedded;
        this.shedLoadName = shedLoadName != null ? shedLoadName : "";
        this.shellyStatusMessage = shellyStatusMessage;
        this.shellyStatusKind = shellyStatusKind != null ? shellyStatusKind : ShellyStatusKind.NONE;
        this.shellyErrorDetail = shellyErrorDetail;
        this.shellyActionInProgress = shellyActionInProgress;
        this.isTestSimulation = isTestSimulation;
    }

    public boolean shouldShowBanner() {
        return band != PowerBand.NORMAL || isShedded || shellyStatusMessage != null || isTestSimulation;
    }
}
