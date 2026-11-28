package com.huaweitv.solar.data.modbus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal, zero-dependency Modbus TCP Client using pure Java Sockets.
 * Optimized specifically for Huawei SUN2000 inverters and SDongle/WLAN modules.
 *
 * Requirements enforced:
 * - Socket kept open and reused across polls.
 * - delay(1500) immediately after socket connection before any read.
 * - delay(200) between register queries.
 * - Socket timeout: 5000 ms.
 */
open class ModbusTcpClient {

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: BufferedOutputStream? = null
    private val transactionCounter = AtomicInteger(1)

    private var currentHost: String? = null
    private var currentPort: Int? = null

    open val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed && !it.isInputShutdown && !it.isOutputShutdown } ?: false

    /**
     * Establishes socket connection if not already open to the specified host:port.
     * Enforces the mandatory 1500ms post-connect delay required by Huawei inverters.
     */
    open suspend fun ensureConnected(host: String, port: Int) = withContext(Dispatchers.IO) {
        if (isConnected && currentHost == host && currentPort == port) {
            return@withContext
        }

        // Close any existing connection
        close()

        val newSocket = Socket()
        newSocket.soTimeout = 5000
        newSocket.tcpNoDelay = true
        newSocket.keepAlive = true

        // 5-second connection timeout
        newSocket.connect(InetSocketAddress(host, port), 5000)

        socket = newSocket
        inStream = BufferedInputStream(newSocket.getInputStream())
        outStream = BufferedOutputStream(newSocket.getOutputStream())
        currentHost = host
        currentPort = port

        // MANDATORY Huawei delay: Dongle drops connection if queried immediately after TCP handshake
        delay(1500)
    }

    /**
     * Reads holding registers using Function Code 03.
     * Returns raw register bytes (quantity * 2 bytes).
     */
    open suspend fun readHoldingRegisters(
        unitId: Int,
        startAddress: Int,
        quantity: Int
    ): ByteArray = withContext(Dispatchers.IO) {
        val activeSocket = socket ?: throw IOException("Socket non connesso")
        val activeIn = inStream ?: throw IOException("InputStream non disponibile")
        val activeOut = outStream ?: throw IOException("OutputStream non disponibile")

        val txId = transactionCounter.getAndIncrement() and 0xFFFF
        val request = ModbusPacket.buildReadHoldingRegistersRequest(txId, unitId, startAddress, quantity)

        // Send request
        activeOut.write(request)
        activeOut.flush()

        // Read 7-byte MBAP Header:
        // [TxHi, TxLo, ProtoHi, ProtoLo, LenHi, LenLo, UnitId]
        val mbapHeader = ByteArray(7)
        readFully(activeIn, mbapHeader)

        val respTxId = ((mbapHeader[0].toInt() and 0xFF) shl 8) or (mbapHeader[1].toInt() and 0xFF)
        val respProto = ((mbapHeader[2].toInt() and 0xFF) shl 8) or (mbapHeader[3].toInt() and 0xFF)
        val respLen = ((mbapHeader[4].toInt() and 0xFF) shl 8) or (mbapHeader[5].toInt() and 0xFF)
        val respUnitId = mbapHeader[6].toInt() and 0xFF

        if (respProto != 0) {
            throw IOException("Protocollo Modbus TCP non valido: $respProto")
        }

        // Read Function Code (1 byte)
        val fcByte = activeIn.read()
        if (fcByte == -1) throw EOFException("Connessione chiusa dall'inverter durante la lettura del Function Code")

        // Check for Modbus Exception (FC with highest bit set, e.g. 0x83)
        if ((fcByte and 0x80) != 0) {
            val exceptionCode = activeIn.read()
            throw ModbusPacket.ModbusException(
                "Errore Modbus Exception 0x${fcByte.toString(16)} (codice eccezione: $exceptionCode) sul registro $startAddress",
                exceptionCode
            )
        }

        if (fcByte != HuaweiRegisters.FC_READ_HOLDING_REGISTERS) {
            throw IOException("Risposta con Function Code inatteso: $fcByte")
        }

        // Read Byte Count (1 byte)
        val byteCount = activeIn.read()
        if (byteCount == -1) throw EOFException("Connessione chiusa durante la lettura del Byte Count")

        val expectedBytes = quantity * 2
        if (byteCount != expectedBytes) {
            throw IOException("Numero di byte ricevuti ($byteCount) diverso da quello atteso ($expectedBytes)")
        }

        // Read register data payload
        val registerData = ByteArray(byteCount)
        readFully(activeIn, registerData)

        return@withContext registerData
    }

    /**
     * Reads exactly [buffer.size] bytes from stream, handling partial TCP chunks.
     */
    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = inputStream.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) {
                throw EOFException("Fine stream inaspettata (letto $offset di ${buffer.size} byte)")
            }
            offset += bytesRead
        }
    }

    /**
     * Closes socket and associated streams cleanly.
     */
    open fun close() {
        runCatching { inStream?.close() }
        runCatching { outStream?.close() }
        runCatching { socket?.close() }
        inStream = null
        outStream = null
        socket = null
        currentHost = null
        currentPort = null
    }
}
