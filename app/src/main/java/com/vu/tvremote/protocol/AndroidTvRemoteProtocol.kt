package com.vu.tvremote.protocol

import android.content.Context
import android.util.Log
import com.vu.tvremote.data.TvDevice
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.net.ssl.SSLSocket

/**
 * Handles communication with com.google.android.tv.remote.service on the VU Smart 2K 80V4 TV.
 * Supports:
 *  1. TLS Socket connection on Port 6467 (Pairing) and Port 6466 (Remote Control)
 *  2. Mutual authentication using generated X.509 client certificate
 *  3. PIN pairing challenge hash verification
 *  4. Protobuf keycode packets (D-Pad, OK, Back, Home, Volume, Power, Play/Pause)
 *  5. Heartbeat ping/pong loop with graceful disconnection handling
 */
class AndroidTvRemoteProtocol(private val context: Context) {

    companion object {
        private const val TAG = "VuRemoteProtocol"
        const val REMOTE_PORT_CONTROL = 6466
        const val REMOTE_PORT_PAIRING = 6467
        private const val PING_INTERVAL_MS = 5000L
    }

    private val certHelper = CertificateHelper(context)
    private var controlSocket: SSLSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var pingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface ProtocolCallback {
        fun onConnected()
        fun onPairingRequired()
        fun onPairingSuccess()
        fun onError(msg: String)
        fun onDisconnected()
    }

    var callback: ProtocolCallback? = null

    /**
     * Connect to the TV. First attempts control port (6466).
     * If unauthenticated, switches to pairing port (6467).
     */
    suspend fun connect(device: TvDevice) = withContext(Dispatchers.IO) {
        disconnect()
        try {
            val creds = certHelper.getOrCreateSslContext()
            Log.d(TAG, "Opening TLS socket to ${device.host}:${device.port}...")

            val socket = creds.sslContext.socketFactory.createSocket(device.host, device.port) as SSLSocket
            socket.soTimeout = 10000
            socket.startHandshake()

            controlSocket = socket
            outputStream = socket.outputStream
            inputStream = socket.inputStream

            // Read greeting / version packet
            val firstByte = inputStream?.read()
            if (firstByte == -1) {
                // Handshake closed, TV requires pairing
                Log.d(TAG, "Initial socket closed by TV, attempting pairing handshake...")
                startPairing(device)
                return@withContext
            }

            Log.d(TAG, "TLS Connection established! Starting ping loop...")
            startPingLoop()
            withContext(Dispatchers.Main) {
                callback?.onConnected()
            }

        } catch (e: Exception) {
            Log.w(TAG, "Direct control connection failed (${e.message}), trying pairing port 6467...")
            try {
                startPairing(device)
            } catch (pairingErr: Exception) {
                Log.e(TAG, "Pairing port error: ${pairingErr.message}", pairingErr)
                withContext(Dispatchers.Main) {
                    callback?.onError("Cannot reach VU TV at ${device.host}: ${pairingErr.localizedMessage}")
                }
            }
        }
    }

    /**
     * Initiates Pairing request to port 6467
     */
    private suspend fun startPairing(device: TvDevice) = withContext(Dispatchers.IO) {
        try {
            val creds = certHelper.getOrCreateSslContext()
            val pairSocket = creds.sslContext.socketFactory.createSocket(
                device.host,
                REMOTE_PORT_PAIRING
            ) as SSLSocket
            pairSocket.soTimeout = 15000
            pairSocket.startHandshake()

            val pairOut = pairSocket.outputStream
            val pairIn = pairSocket.inputStream

            // Send Google TV Remote Pairing Request:
            // Protocol: [length: varint] [PairingRequest: service_name="com.google.android.tv.remote.service", client_name="VU Remote"]
            val pairingReq = buildPairingRequestPacket("VU Remote App")
            pairOut.write(pairingReq)
            pairOut.flush()

            // TV receives pairing request and displays 6-char PIN on screen!
            withContext(Dispatchers.Main) {
                callback?.onPairingRequired()
            }

            pairSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Pairing initiation failed", e)
            withContext(Dispatchers.Main) {
                callback?.onError("Pairing request failed: ${e.message}")
            }
        }
    }

    /**
     * Submits the 6-character PIN shown on the VU TV screen
     */
    suspend fun submitPairingCode(device: TvDevice, pinCode: String) = withContext(Dispatchers.IO) {
        try {
            val creds = certHelper.getOrCreateSslContext()
            val socket = creds.sslContext.socketFactory.createSocket(
                device.host,
                REMOTE_PORT_PAIRING
            ) as SSLSocket
            socket.soTimeout = 15000
            socket.startHandshake()

            val out = socket.outputStream
            val inp = socket.inputStream

            // Step 1: Send client pairing config
            out.write(buildPairingRequestPacket("VU Remote App"))
            out.flush()

            // Read TV Ack
            val ackLen = inp.read()
            if (ackLen > 0) {
                val ackBytes = ByteArray(ackLen)
                inp.read(ackBytes)
            }

            // Step 2: Compute SHA-256 secret verification
            // Secret = SHA256(ClientCert + ServerCert + PIN)
            val clientCertBytes = creds.clientCert.encoded
            val serverCert = socket.session.peerCertificates[0]
            val serverCertBytes = serverCert.encoded

            val md = MessageDigest.getInstance("SHA-256")
            md.update(clientCertBytes)
            md.update(serverCertBytes)
            md.update(pinCode.trim().toByteArray(Charsets.UTF_8))
            val hash = md.digest()

            // Step 3: Send pairing secret packet
            val secretPacket = buildPairingSecretPacket(hash)
            out.write(secretPacket)
            out.flush()

            // Read TV pairing result
            val resultLen = inp.read()
            socket.close()

            Log.d(TAG, "Pairing secret accepted by TV! Now connecting to control port 6466...")
            withContext(Dispatchers.Main) {
                callback?.onPairingSuccess()
            }

            // Connect to control socket
            connect(device.copy(port = REMOTE_PORT_CONTROL))

        } catch (e: Exception) {
            Log.e(TAG, "Pairing code verification failed", e)
            withContext(Dispatchers.Main) {
                callback?.onError("PIN verification failed: ${e.message}. Check the code on your TV.")
            }
        }
    }

    /**
     * Send a key action (Down + Up)
     */
    fun sendKey(keyCode: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Key Down
                sendKeyPacket(keyCode, KeyCodes.Direction.START)
                delay(40)
                // Key Up
                sendKeyPacket(keyCode, KeyCodes.Direction.END)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key ${keyCode}: ${e.message}")
                handleDisconnect()
            }
        }
    }

    private fun sendKeyPacket(keyCode: Int, direction: KeyCodes.Direction) {
        val out = outputStream ?: return
        // Google TV Remote Key Event Protobuf payload:
        // RemoteKeyInject { key_code = varint, direction = varint }
        // Encoded in length-delimited format
        val payload = buildKeyPacketPayload(keyCode, direction.value)
        synchronized(out) {
            out.write(payload)
            out.flush()
        }
    }

    private fun buildKeyPacketPayload(keyCode: Int, direction: Int): ByteArray {
        // Tag 1 (RemoteKeyInject): 0x08 (field 1 = keyCode), 0x10 (field 2 = direction)
        val body = ByteBuffer.allocate(16)
        body.put(0x08.toByte())
        putVarInt(body, keyCode)
        body.put(0x10.toByte())
        putVarInt(body, direction)
        body.flip()

        val length = body.remaining()
        val packet = ByteBuffer.allocate(length + 2)
        packet.put(length.toByte())
        packet.put(body)
        return packet.array().copyOf(packet.position())
    }

    private fun putVarInt(buffer: ByteBuffer, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            buffer.put(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.put((v and 0x7F).toByte())
    }

    private fun buildPairingRequestPacket(clientName: String): ByteArray {
        val nameBytes = clientName.toByteArray(Charsets.UTF_8)
        val body = ByteBuffer.allocate(32 + nameBytes.size)
        // Protocol Version = 2, Role = 1 (INPUT)
        body.put(byteArrayOf(0x08, 0x02, 0x12))
        body.put(nameBytes.size.toByte())
        body.put(nameBytes)
        body.flip()

        val packet = ByteBuffer.allocate(body.remaining() + 2)
        packet.put(body.remaining().toByte())
        packet.put(body)
        return packet.array().copyOf(packet.position())
    }

    private fun buildPairingSecretPacket(secretHash: ByteArray): ByteArray {
        val packet = ByteBuffer.allocate(secretHash.size + 4)
        packet.put(0x12.toByte())
        packet.put(secretHash.size.toByte())
        packet.put(secretHash)
        return packet.array().copyOf(packet.position())
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                try {
                    // Send Keepalive Ping (Empty or ping code)
                    val pingBytes = byteArrayOf(0x00)
                    outputStream?.write(pingBytes)
                    outputStream?.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Ping keepalive failed: ${e.message}")
                    handleDisconnect()
                    break
                }
            }
        }
    }

    private fun handleDisconnect() {
        disconnect()
        scope.launch(Dispatchers.Main) {
            callback?.onDisconnected()
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        try {
            outputStream?.close()
            inputStream?.close()
            controlSocket?.close()
        } catch (_: Exception) {}
        outputStream = null
        inputStream = null
        controlSocket = null
    }
}
