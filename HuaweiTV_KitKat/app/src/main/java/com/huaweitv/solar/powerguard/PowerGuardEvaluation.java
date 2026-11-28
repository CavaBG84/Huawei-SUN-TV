package com.huaweitv.solar.powerguard;

public class PowerGuardEvaluation {
    public final PowerGuardState state;
    public final boolean shouldTriggerWarningBeep;
    public final boolean shouldTriggerCriticalBeep;
    public final boolean shouldTriggerShellyShed;

    public PowerGuardEvaluation(
        PowerGuardState state,
        boolean shouldTriggerWarningBeep,
        boolean shouldTriggerCriticalBeep,
        boolean shouldTriggerShellyShed
    ) {
        this.state = state;
        this.shouldTriggerWarningBeep = shouldTriggerWarningBeep;
        this.shouldTriggerCriticalBeep = shouldTriggerCriticalBeep;
        this.shouldTriggerShellyShed = shouldTriggerShellyShed;
    }
}
