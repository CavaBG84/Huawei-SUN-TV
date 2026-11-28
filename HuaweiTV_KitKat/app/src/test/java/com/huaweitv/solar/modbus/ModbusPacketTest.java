package com.huaweitv.solar.modbus;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModbusPacketTest {

    @Test
    public void testBuildReadHoldingRegistersRequest() {
        byte[] request = ModbusPacket.buildReadHoldingRegistersRequest(0x1234, 0, 32064, 2);
        assertEquals(12, request.length);
        assertEquals(0x12, request[0]);
        assertEquals(0x34, request[1]);
        assertEquals(0x00, request[2]);
        assertEquals(0x00, request[3]);
        assertEquals(0x00, request[4]);
        assertEquals(0x06, request[5]);
        assertEquals(0x00, request[6]); // Unit ID = 0
        assertEquals(0x03, request[7]); // FC 03
        assertEquals(0x7D, request[8]); // 32064 hi
        assertEquals(0x40, request[9]); // 32064 lo
        assertEquals(0x00, request[10]);
        assertEquals(0x02, request[11]);
    }

    @Test
    public void testParseInt32() {
        byte[] bytes = new byte[]{0x00, 0x00, 0x0D, (byte) 0xAC}; // 3500
        assertEquals(3500, ModbusPacket.parseInt32(bytes, 0));
    }

    @Test
    public void testParseUInt16() {
        byte[] bytes = new byte[]{0x03, 0x56}; // 854 (85.4%)
        assertEquals(854, ModbusPacket.parseUInt16(bytes, 0));
    }
}
