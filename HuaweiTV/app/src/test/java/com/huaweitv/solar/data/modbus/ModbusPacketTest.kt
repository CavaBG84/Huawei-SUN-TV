package com.huaweitv.solar.data.modbus

import org.junit.Assert.assertEquals
import org.junit.Test

class ModbusPacketTest {

    @Test
    fun testBuildReadHoldingRegistersRequest() {
        val txId = 0x1234
        val unitId = 1
        val startAddr = 32064 // 0x7D40
        val qty = 2

        val request = ModbusPacket.buildReadHoldingRegistersRequest(txId, unitId, startAddr, qty)

        assertEquals(12, request.size)
        // TxId
        assertEquals(0x12.toByte(), request[0])
        assertEquals(0x34.toByte(), request[1])
        // Protocol ID = 0
        assertEquals(0.toByte(), request[2])
        assertEquals(0.toByte(), request[3])
        // Length = 6
        assertEquals(0.toByte(), request[4])
        assertEquals(6.toByte(), request[5])
        // Unit ID
        assertEquals(1.toByte(), request[6])
        // Function Code = 0x03
        assertEquals(0x03.toByte(), request[7])
        // Start Address = 32064 (0x7D40)
        assertEquals(0x7D.toByte(), request[8])
        assertEquals(0x40.toByte(), request[9])
        // Quantity = 2
        assertEquals(0x00.toByte(), request[10])
        assertEquals(0x02.toByte(), request[11])
    }

    @Test
    fun testParseSignedInt32Positive() {
        // 3500 W = 0x00000DAC
        val bytes = byteArrayOf(0x00, 0x00, 0x0D, 0xAC.toByte())
        val value = ModbusPacket.parseInt32(bytes)
        assertEquals(3500, value)
    }

    @Test
    fun testParseSignedInt32Negative() {
        // -1500 W = 0xFFFFFFA4
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFA.toByte(), 0x24.toByte())
        val value = ModbusPacket.parseInt32(bytes)
        assertEquals(-1500, value)
    }

    @Test
    fun testParseUnsignedInt32() {
        // 154200 = 0x00025A58
        val bytes = byteArrayOf(0x00, 0x02, 0x5A, 0x58)
        val value = ModbusPacket.parseUInt32(bytes)
        assertEquals(154200L, value)
    }

    @Test
    fun testParseUnsignedInt16() {
        // 854 (SoC 85.4%) = 0x0356
        val bytes = byteArrayOf(0x03, 0x56)
        val value = ModbusPacket.parseUInt16(bytes)
        assertEquals(854, value)
    }
}
