package com.huaweitv.solar.data.modbus

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Modbus TCP Packet Builder and Parser (Function Code 03 - Read Holding Registers).
 * No external Modbus libraries are used.
 */
object ModbusPacket {

    class ModbusException(message: String, val code: Int = 0) : IOException(message)

    /**
     * Builds a 12-byte Modbus TCP ADU (Application Data Unit) for FC 03.
     *
     * Bytes 0-1: Transaction Identifier (Big-Endian)
     * Bytes 2-3: Protocol Identifier (0x0000 = Modbus TCP)
     * Bytes 4-5: Length (0x0006 = 6 bytes following: UnitID, FC, AddrHi, AddrLo, QtyHi, QtyLo)
     * Byte 6:    Unit Identifier / Slave ID
     * Byte 7:    Function Code (0x03)
     * Bytes 8-9: Starting Address (Big-Endian)
     * Bytes 10-11: Quantity of Registers (Big-Endian)
     */
    fun buildReadHoldingRegistersRequest(
        transactionId: Int,
        unitId: Int,
        startAddress: Int,
        quantity: Int
    ): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(transactionId.toShort())
        buffer.putShort(0.toShort()) // Protocol ID = 0
        buffer.putShort(6.toShort()) // Length = 6
        buffer.put(unitId.toByte())
        buffer.put(HuaweiRegisters.FC_READ_HOLDING_REGISTERS.toByte())
        buffer.putShort(startAddress.toShort())
        buffer.putShort(quantity.toShort())
        return buffer.array()
    }

    /**
     * Parses a Big-Endian signed 32-bit integer (INT32) from byte array.
     */
    fun parseInt32(bytes: ByteArray, offset: Int = 0): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /**
     * Parses a Big-Endian unsigned 32-bit integer (UINT32) from byte array.
     */
    fun parseUInt32(bytes: ByteArray, offset: Int = 0): Long {
        val b0 = bytes[offset].toLong() and 0xFF
        val b1 = bytes[offset + 1].toLong() and 0xFF
        val b2 = bytes[offset + 2].toLong() and 0xFF
        val b3 = bytes[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /**
     * Parses a Big-Endian unsigned 16-bit integer (UINT16) from byte array.
     */
    fun parseUInt16(bytes: ByteArray, offset: Int = 0): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return (b0 shl 8) or b1
    }
}
