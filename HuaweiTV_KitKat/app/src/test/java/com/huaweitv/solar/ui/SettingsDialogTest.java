package com.huaweitv.solar.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsDialogTest {

    @Test
    public void testValidIpv4() {
        assertTrue(SettingsDialog.isValidIpv4("192.168.1.63"));
        assertTrue(SettingsDialog.isValidIpv4("192.168.1.53"));
        assertTrue(SettingsDialog.isValidIpv4("192.168.200.1"));
        assertTrue(SettingsDialog.isValidIpv4("10.0.0.1"));
        assertTrue(SettingsDialog.isValidIpv4("127.0.0.1"));
        assertTrue(SettingsDialog.isValidIpv4("0.0.0.0"));
        assertTrue(SettingsDialog.isValidIpv4("255.255.255.255"));
    }

    @Test
    public void testInvalidIpv4() {
        assertFalse(SettingsDialog.isValidIpv4(""));
        assertFalse(SettingsDialog.isValidIpv4("192.168.1"));
        assertFalse(SettingsDialog.isValidIpv4("192.168.1."));
        assertFalse(SettingsDialog.isValidIpv4("192.168.1.256"));
        assertFalse(SettingsDialog.isValidIpv4("192.168.01.53"));
        assertFalse(SettingsDialog.isValidIpv4("abc.def.ghi.jkl"));
        assertFalse(SettingsDialog.isValidIpv4("192.168.1.53.1"));
    }
}
