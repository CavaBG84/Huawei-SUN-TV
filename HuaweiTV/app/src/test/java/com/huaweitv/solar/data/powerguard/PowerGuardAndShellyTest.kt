package com.huaweitv.solar.data.powerguard

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.ShellyGen
import com.huaweitv.solar.data.model.SolarTelemetry
import com.huaweitv.solar.data.shelly.ShellyController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerGuardAndShellyTest {

    private fun telemetryWithGrid(gridPowerW: Int, hasMeter: Boolean = true): SolarTelemetry {
        return SolarTelemetry(
            pvPowerW = 0,
            activePowerW = 0,
            gridPowerW = gridPowerW,
            isConnected = true,
            hasMeter = hasMeter
        )
    }

    @Test
    fun testThresholdTransitionsAndCountdowns() {
        var nowMs = 1_000_000L
        val manager = PowerGuardManager { nowMs }
        val settings = AppSettings(
            meterAlarmEnabled = true,
            contractPowerKw = 3.0,
            meterAlarmSoundEnabled = true
        )

        // 1. Export or <= 3.30 kW import -> NORMAL
        var eval = manager.evaluate(telemetryWithGrid(500), settings) // exporting
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertEquals(0.0, eval.state.importPowerKw, 0.001)
        assertFalse(eval.state.shouldShowBanner)

        eval = manager.evaluate(telemetryWithGrid(-3300), settings) // exactly 3.30 kW (+10%)
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertEquals(3.30, eval.state.importPowerKw, 0.001)

        // 2. 3.50 kW import -> WARNING with 180 min (10,800s) countdown + warning beep
        eval = manager.evaluate(telemetryWithGrid(-3500), settings)
        assertEquals(PowerBand.WARNING, eval.state.band)
        assertEquals(10_800L, eval.state.remainingSeconds)
        assertTrue(eval.shouldTriggerWarningBeep)
        assertFalse(eval.shouldTriggerCriticalBeep)
        assertTrue(eval.state.shouldShowBanner)

        // Advance 45 seconds -> remaining 10,755s, no repeated warning beep
        nowMs += 45_000L
        eval = manager.tick(settings)
        assertEquals(PowerBand.WARNING, eval.state.band)
        assertEquals(10_755L, eval.state.remainingSeconds)
        assertFalse(eval.shouldTriggerWarningBeep)

        // 3. 4.10 kW import (> 3.99 kW) -> CRITICAL with 120s countdown + critical beep
        eval = manager.evaluate(telemetryWithGrid(-4100), settings)
        assertEquals(PowerBand.CRITICAL, eval.state.band)
        assertEquals(120L, eval.state.remainingSeconds)
        assertTrue(eval.shouldTriggerCriticalBeep)

        // Advance 30 seconds -> remaining 90s
        nowMs += 30_000L
        eval = manager.tick(settings)
        assertEquals(PowerBand.CRITICAL, eval.state.band)
        assertEquals(90L, eval.state.remainingSeconds)
        assertTrue(eval.shouldTriggerCriticalBeep)

        // Mute session -> stops critical beeps
        manager.muteAlarmForSession()
        nowMs += 5_000L
        eval = manager.tick(settings)
        assertTrue(eval.state.isMuted)
        assertFalse(eval.shouldTriggerCriticalBeep)

        // Drop back to NORMAL -> resets timers & mute
        eval = manager.evaluate(telemetryWithGrid(-1500), settings)
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertEquals(0L, eval.state.remainingSeconds)
        assertFalse(eval.state.isMuted)
    }

    @Test
    fun testAlarmDisabledOrMissingMeterStaysNormal() {
        val manager = PowerGuardManager { 10_000L }

        val disabledSettings = AppSettings(meterAlarmEnabled = false, contractPowerKw = 3.0)
        var eval = manager.evaluate(telemetryWithGrid(-5000), disabledSettings)
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertFalse(eval.shouldTriggerWarningBeep)
        assertFalse(eval.shouldTriggerCriticalBeep)

        val enabledSettings = AppSettings(meterAlarmEnabled = true, contractPowerKw = 3.0)
        eval = manager.evaluate(telemetryWithGrid(-5000, hasMeter = false), enabledSettings)
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertEquals(0.0, eval.state.importPowerKw, 0.001)
    }

    @Test
    fun testShellyAntiSpuntoFilterAndAntiBounceLock() {
        var nowMs = 5_000_000L
        val manager = PowerGuardManager { nowMs }
        val settings = AppSettings(
            meterAlarmEnabled = true,
            contractPowerKw = 3.0,
            shellySheddingEnabled = true,
            shelly1Name = "Scaldabagno",
            shelly1Ip = "192.168.1.120",
            shelly1Channel = 0,
            shelly1Gen = ShellyGen.GEN2,
            shellyTriggerDelaySec = 15
        )

        // Transient spike above 3.30 kW for 10 seconds (< 15s) -> pending 15s then 5s, no trigger
        var eval = manager.evaluate(telemetryWithGrid(-3600), settings)
        assertFalse(eval.shouldTriggerShellyShed)
        assertEquals(15, eval.state.shellyPendingDelaySec)

        nowMs += 10_000L
        eval = manager.tick(settings)
        assertFalse(eval.shouldTriggerShellyShed)
        assertEquals(5, eval.state.shellyPendingDelaySec)

        // Drop back within NORMAL before 15s -> anti-spunto resets without firing
        nowMs += 1_000L
        eval = manager.evaluate(telemetryWithGrid(-2500), settings)
        assertFalse(eval.shouldTriggerShellyShed)
        assertEquals(0, eval.state.shellyPendingDelaySec)
        assertFalse(eval.state.isShedded)

        // Sustained overload for full 15s -> triggers Shelly shed once
        nowMs += 1_000L
        eval = manager.evaluate(telemetryWithGrid(-3600), settings)
        assertFalse(eval.shouldTriggerShellyShed)

        nowMs += 15_000L
        eval = manager.tick(settings)
        assertTrue(eval.shouldTriggerShellyShed)
        assertTrue(eval.state.isShedded)
        assertEquals("Scaldabagno", eval.state.shedLoadName)

        // Subsequent ticks do NOT re-trigger while locked
        manager.onShellyCommandResult(success = true, loadName = "Scaldabagno", errorDetail = null)
        nowMs += 20_000L
        eval = manager.tick(settings)
        assertFalse(eval.shouldTriggerShellyShed)
        assertTrue(eval.state.isShedded)

        // Manual reset clears lock
        manager.resetShellyShed()
        assertFalse(manager.currentState.isShedded)
    }

    @Test
    fun testShellyTurnOffUrlBuilders() {
        assertEquals(
            "http://192.168.1.120/relay/0?turn=off",
            ShellyController.buildTurnOffUrl("192.168.1.120", 0, ShellyGen.GEN1)
        )
        assertEquals(
            "http://192.168.1.121/relay/1?turn=off",
            ShellyController.buildTurnOffUrl(" 192.168.1.121 ", 1, ShellyGen.GEN1)
        )
        assertEquals(
            "http://192.168.1.120/rpc/Switch.Set?id=0&on=false",
            ShellyController.buildTurnOffUrl("192.168.1.120", 0, ShellyGen.GEN2)
        )
        assertEquals(
            "http://192.168.1.122/rpc/Switch.Set?id=2&on=false",
            ShellyController.buildTurnOffUrl("192.168.1.122", 2, ShellyGen.GEN2)
        )
    }

    @Test
    fun testManualCriticalAlarmSimulation() {
        var nowMs = 2_000_000L
        val manager = PowerGuardManager { nowMs }
        val settings = AppSettings(
            meterAlarmEnabled = false,
            contractPowerKw = 3.0,
            meterAlarmSoundEnabled = false,
            shellySheddingEnabled = true,
            shelly1Ip = "192.168.1.120",
            shellyTriggerDelaySec = 5
        )

        // Start 8s CRITICAL simulation (even with meterAlarmEnabled == false)
        var eval = manager.startAlarmTest(3.0, settings)
        assertTrue(eval.state.isTestSimulation)
        assertEquals(PowerBand.CRITICAL, eval.state.band)
        assertEquals(4.20, eval.state.importPowerKw, 0.001)
        assertEquals(120L, eval.state.remainingSeconds)
        assertTrue(eval.shouldTriggerCriticalBeep)
        assertFalse(eval.shouldTriggerShellyShed)

        // After 6s (> shellyTriggerDelaySec=5s), simulation stays active and MUST NOT trigger Shelly
        nowMs += 6_000L
        eval = manager.tick(settings)
        assertTrue(eval.state.isTestSimulation)
        assertEquals(PowerBand.CRITICAL, eval.state.band)
        assertEquals(114L, eval.state.remainingSeconds)
        assertTrue(eval.shouldTriggerCriticalBeep)
        assertFalse(eval.shouldTriggerShellyShed)
        assertEquals(0, eval.state.shellyPendingDelaySec)

        // At 8s, simulation ends automatically and restores NORMAL real state
        nowMs += 2_000L
        eval = manager.tick(settings)
        assertFalse(eval.state.isTestSimulation)
        assertEquals(PowerBand.NORMAL, eval.state.band)
        assertFalse(eval.shouldTriggerShellyShed)

        // Mute during active simulation immediately stops test and restores real telemetry
        manager.evaluate(telemetryWithGrid(200), settings)
        eval = manager.startAlarmTest(3.0, settings)
        assertTrue(eval.state.isTestSimulation)
        manager.muteAlarmForSession()
        assertFalse(manager.currentState.isTestSimulation)
        assertEquals(PowerBand.NORMAL, manager.currentState.band)
    }
}
