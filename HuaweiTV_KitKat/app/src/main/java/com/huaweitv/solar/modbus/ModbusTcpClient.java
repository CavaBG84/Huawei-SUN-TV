package com.huaweitv.solar.modbus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Modbus TCP Client using pure Java Sockets.
 * Zero external libraries. 100% compatible with Android 4.4.4.
 *
 * Requirements:
 * - Persistent socket reused across polls.
 * - 1500 ms delay immediately after socket connect.
 * - 200 ms delay between register queries.
 * - 5000 ms socket timeout.
 */
public class ModbusTcpClient {

    private Socket socket;
    private InputStream inStream;
    private BufferedOutputStream outStream;
    private final AtomicInteger transactionCounter = new AtomicInteger(1);

    private String currentHost;
    private int currentPort = -1;

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    public synchronized void ensureConnected(String host, int port) throws IOException, InterruptedException {
        if (isConnected() && host.equals(currentHost) && port == currentPort) {
            return;
        }

        close();

        Socket newSocket = new Socket();
        newSocket.setSoTimeout(5000);
        newSocket.setTcpNoDelay(true);
        newSocket.setKeepAlive(true);

        newSocket.connect(new InetSocketAddress(host, port), 5000);

        this.socket = newSocket;
        this.inStream = new BufferedInputStream(newSocket.getInputStream());
        this.outStream = new BufferedOutputStream(newSocket.getOutputStream());
        this.currentHost = host;
        this.currentPort = port;

        // Mandatory Huawei delay: Dongle drops connection if queried too quickly after handshake
        Thread.sleep(1500);
    }

    public synchronized byte[] readHoldingRegisters(int unitId, int startAddress, int quantity) throws IOException {
        if (!isConnected()) {
            throw new IOException("Socket non connesso");
        }

        int txId = transactionCounter.getAndIncrement() & 0xFFFF;
        byte[] request = ModbusPacket.buildReadHoldingRegistersRequest(txId, unitId, startAddress, quantity);

        outStream.write(request);
        outStream.flush();

        // Read 7-byte MBAP header: [TxHi, TxLo, ProtoHi, ProtoLo, LenHi, LenLo, UnitId]
        byte[] mbapHeader = new byte[7];
        readFully(inStream, mbapHeader);

        int respProto = ((mbapHeader[2] & 0xFF) << 8) | (mbapHeader[3] & 0xFF);
        if (respProto != 0) {
            throw new IOException("Protocollo Modbus TCP non valido: " + respProto);
        }

        // Read Function Code
        int fcByte = inStream.read();
        if (fcByte == -1) {
            throw new EOFException("Connessione chiusa dall'inverter durante la lettura del Function Code");
        }

        // Check for exception (highest bit set, e.g. 0x83)
        if ((fcByte & 0x80) != 0) {
            int exceptionCode = inStream.read();
            throw new ModbusPacket.ModbusException(
                "Modbus Exception 0x" + Integer.toHexString(fcByte) + " (codice: " + exceptionCode + ") sul registro " + startAddress,
                exceptionCode
            );
        }

        if (fcByte != HuaweiRegisters.FC_READ_HOLDING_REGISTERS) {
            throw new IOException("Function Code inatteso: " + fcByte);
        }

        // Read Byte Count
        int byteCount = inStream.read();
        if (byteCount == -1) {
            throw new EOFException("Connessione chiusa durante la lettura del Byte Count");
        }

        int expectedBytes = quantity * 2;
        if (byteCount != expectedBytes) {
            throw new IOException("Byte ricevuti (" + byteCount + ") diversi da quelli attesi (" + expectedBytes + ")");
        }

        byte[] registerData = new byte[byteCount];
        readFully(inStream, registerData);

        return registerData;
    }

    private void readFully(InputStream is, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int bytesRead = is.read(buffer, offset, buffer.length - offset);
            if (bytesRead == -1) {
                throw new EOFException("Fine stream inaspettata (letto " + offset + " di " + buffer.length + " byte)");
            }
            offset += bytesRead;
        }
    }

    public synchronized void close() {
        if (inStream != null) {
            try { inStream.close(); } catch (Exception ignored) {}
            inStream = null;
        }
        if (outStream != null) {
            try { outStream.close(); } catch (Exception ignored) {}
            outStream = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
        currentHost = null;
        currentPort = -1;
    }
}
