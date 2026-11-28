package com.huaweitv.solar.modbus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Modbus TCP Packet Builder and Parser for Function Code 03. Pure Java.
 */
public final class ModbusPacket {
    private ModbusPacket() {}

    public static class ModbusException extends IOException {
        public final int code;

        public ModbusException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    public static byte[] buildReadHoldingRegistersRequest(int transactionId, int unitId, int startAddress, int quantity) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) (transactionId & 0xFFFF));
        buffer.putShort((short) 0); // Protocol ID = 0 (Modbus TCP)
        buffer.putShort((short) 6); // Length = 6 bytes following
        buffer.put((byte) (unitId & 0xFF));
        buffer.put((byte) HuaweiRegisters.FC_READ_HOLDING_REGISTERS);
        buffer.putShort((short) (startAddress & 0xFFFF));
        buffer.putShort((short) (quantity & 0xFFFF));
        return buffer.array();
    }

    public static int parseInt32(byte[] bytes, int offset) {
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        int b2 = bytes[offset + 2] & 0xFF;
        int b3 = bytes[offset + 3] & 0xFF;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public static long parseUInt32(byte[] bytes, int offset) {
        long b0 = bytes[offset] & 0xFFL;
        long b1 = bytes[offset + 1] & 0xFFL;
        long b2 = bytes[offset + 2] & 0xFFL;
        long b3 = bytes[offset + 3] & 0xFFL;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public static int parseUInt16(byte[] bytes, int offset) {
        int b0 = bytes[offset] & 0xFF;
        int b1 = bytes[offset + 1] & 0xFF;
        return (b0 << 8) | b1;
    }
}
