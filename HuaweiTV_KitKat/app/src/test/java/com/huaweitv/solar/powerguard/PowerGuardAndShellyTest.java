package com.huaweitv.solar.powerguard;

import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.ShellyGen;
import com.huaweitv.solar.model.SolarTelemetry;
import com.huaweitv.solar.shelly.ShellyController;
import org.junit.Test;

import static org.junit.Assert.*;

public class PowerGuardAndShellyTest {

    private static SolarTelemetry telemetryWithGrid(int gridPowerW, boolean hasMeter) {
        return new SolarTelemetry(
            0, 0, Math.max(0, -gridPowerW), 0.0, gridPowerW, 0.0, 0,
            System.currentTimeMillis(), true, false, hasMeter, null
        );
    }

    @Test
    public void testThresholdTransitionsAndCountdowns() {
        final long[] nowMs = new long[]{1_000_000L};
        PowerGuardManager manager = new PowerGuardManager(() -> nowMs[0]);
        AppSettings settings = new AppSettings(
            InverterBrand.HUAWEI, "192.168.1.50", 502, 1, 1, 10, false,
            true, 3.0, true,
            false, "", "", 0, ShellyGen.GEN2, 15
        );

        // 1. Export or <= 3.30 kW import -> NORMAL
        PowerGuardEvaluation eval = manager.evaluate(telemetryWithGrid(500, true), settings);
        assertEquals(PowerBand.NORMAL, eval.state.band);
        assertEquals(0.0, eval.state.importPowerKw, 0.001);
        assertFalse(eval.state.shouldShowBanner());

        eval = manager.evaluate(telemetryWithGrid(-3300, true), settings);
        assertEquals(PowerBand.NORMAL, eval.state.band);

        // 2. 3.50 kW -> WARNING with 180 min (10,800s) countdown
        eval = manager.evaluate(telemetryWithGrid(-3500, true), settings);
        assertEquals(PowerBand.WARNING, eval.state.band);
        assertEquals(10_800L, eval.state.remainingSeconds);
        assertTrue(eval.shouldTriggerWarningBeep);
        assertFalse(eval.shouldTriggerCriticalBeep);
        assertTrue(eval.state.shouldShowBanner());

        nowMs[0] += 45_000L;
        eval = manager.tick(settings);
        assertEquals(10_755L, eval.state.remainingSeconds);
        assertFalse(eval.shouldTriggerWarningBeep);

        // 3. 4.10 kW -> CRITICAL with 120s countdown
        eval = manager.evaluate(telemetryWithGrid(-4100, true), settings);
        assertEquals(PowerBand.CRITICAL, eval.state.band);
        assertEquals(120L, eval.state.remainingSeconds);
        assertTrue(eval.shouldTriggerCriticalBeep);

        nowMs[0] += 30_000L;
        eval = manager.tick(settings);
        assertEquals(90L, eval.state.remainingSeconds);
        assertTrue(eval.shouldTriggerCriticalBeep);

        manager.muteAlarmForSession();
        nowMs[0] += 5_000L;
        eval = manager.tick(settings);
        assertTrue(eval.state.isMuted);
        assertFalse(eval.shouldTriggerCriticalBeep);

        // Drop back to NORMAL
        eval = manager.evaluate(telemetryWithGrid(-1500, true), settings);
        assertEquals(PowerBand.NORMAL, eval.state.band);
        assertEquals(0L, eval.state.remainingSeconds);
        assertFalse(eval.state.isMuted);
    }

    @Test
    public void testShellyAntiSpuntoFilterAndAntiBounceLock() {
        final long[] nowMs = new long[]{5_000_000L};
        PowerGuardManager manager = new PowerGuardManager(() -> nowMs[0]);
        AppSettings settings = new AppSettings(
            InverterBrand.HUAWEI, "192.168.1.50", 502, 1, 1, 10, false,
            true, 3.0, true,
            true, "Scaldabagno", "192.168.1.120", 0, ShellyGen.GEN2, 15
        );

        PowerGuardEvaluation eval = manager.evaluate(telemetryWithGrid(-3600, true), settings);
        assertFalse(eval.shouldTriggerShellyShed);
        assertEquals(15, eval.state.shellyPendingDelaySec);

        nowMs[0] += 10_000L;
        eval = manager.tick(settings);
        assertFalse(eval.shouldTriggerShellyShed);
        assertEquals(5, eval.state.shellyPendingDelaySec);

        // Spike disappears before 15s -> resets without firing
        nowMs[0] += 1_000L;
        eval = manager.evaluate(telemetryWithGrid(-2500, true), settings);
        assertFalse(eval.shouldTriggerShellyShed);
        assertEquals(0, eval.state.shellyPendingDelaySec);
        assertFalse(eval.state.isShedded);

        // Sustained overload for 15s -> triggers once
        nowMs[0] += 1_000L;
        manager.evaluate(telemetryWithGrid(-3600, true), settings);
        nowMs[0] += 15_000L;
        eval = manager.tick(settings);
        assertTrue(eval.shouldTriggerShellyShed);
        assertTrue(eval.state.isShedded);
        assertEquals("Scaldabagno", eval.state.shedLoadName);

        manager.onShellyCommandResult(true, "Scaldabagno", null);
        nowMs[0] += 20_000L;
        eval = manager.tick(settings);
        assertFalse(eval.shouldTriggerShellyShed);
        assertTrue(eval.state.isShedded);

        manager.resetShellyShed();
        assertFalse(manager.getCurrentState().isShedded);
    }

    @Test
    public void testShellyTurnOffUrlBuilders() {
        assertEquals(
            "http://192.168.1.120/relay/0?turn=off",
            ShellyController.buildTurnOffUrl("192.168.1.120", 0, ShellyGen.GEN1)
        );
        assertEquals(
            "http://192.168.1.120/rpc/Switch.Set?id=0&on=false",
            ShellyController.buildTurnOffUrl("192.168.1.120", 0, ShellyGen.GEN2)
        );
        assertEquals(
            "http://192.168.1.122/rpc/Switch.Set?id=2&on=false",
            ShellyController.buildTurnOffUrl(" 192.168.1.122 ", 2, ShellyGen.GEN2)
        );
    }

    @Test
    public void testManualCriticalAlarmSimulation() {
        final long[] nowMs = new long[]{2_000_000L};
        PowerGuardManager manager = new PowerGuardManager(() -> nowMs[0]);
        AppSettings settings = new AppSettings(
            InverterBrand.HUAWEI, "192.168.1.50", 502, 1, 1, 10, false,
            false, 3.0, false,
            true, "Scaldabagno", "192.168.1.120", 0, ShellyGen.GEN2, 5
        );

        PowerGuardEvaluation eval = manager.startAlarmTest(3.0, settings);
        assertTrue(eval.state.isTestSimulation);
        assertEquals(PowerBand.CRITICAL, eval.state.band);
        assertEquals(4.20, eval.state.importPowerKw, 0.001);
        assertEquals(120L, eval.state.remainingSeconds);
        assertTrue(eval.shouldTriggerCriticalBeep);
        assertFalse(eval.shouldTriggerShellyShed);

        nowMs[0] += 6_000L;
        eval = manager.tick(settings);
        assertTrue(eval.state.isTestSimulation);
        assertEquals(PowerBand.CRITICAL, eval.state.band);
        assertEquals(114L, eval.state.remainingSeconds);
        assertTrue(eval.shouldTriggerCriticalBeep);
        assertFalse(eval.shouldTriggerShellyShed);
        assertEquals(0, eval.state.shellyPendingDelaySec);

        nowMs[0] += 2_000L;
        eval = manager.tick(settings);
        assertFalse(eval.state.isTestSimulation);
        assertEquals(PowerBand.NORMAL, eval.state.band);
        assertFalse(eval.shouldTriggerShellyShed);

        manager.evaluate(telemetryWithGrid(200, true), settings);
        eval = manager.startAlarmTest(3.0, settings);
        assertTrue(eval.state.isTestSimulation);
        manager.muteAlarmForSession();
        assertFalse(manager.getCurrentState().isTestSimulation);
        assertEquals(PowerBand.NORMAL, manager.getCurrentState().band);
    }
}
