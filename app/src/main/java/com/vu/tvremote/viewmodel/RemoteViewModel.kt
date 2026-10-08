package com.vu.tvremote.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vu.tvremote.data.ConnectionState
import com.vu.tvremote.data.RemoteUiState
import com.vu.tvremote.data.TvDevice
import com.vu.tvremote.discovery.TvDiscoveryManager
import com.vu.tvremote.protocol.AndroidTvRemoteProtocol
import com.vu.tvremote.protocol.KeyCodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val discoveryManager = TvDiscoveryManager(application)
    private val protocol = AndroidTvRemoteProtocol(application)

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        setupProtocolCallbacks()
        observeDiscovery()
        startScanning()
    }

    private fun setupProtocolCallbacks() {
        protocol.callback = object : AndroidTvRemoteProtocol.ProtocolCallback {
            override fun onConnected() {
                val active = _uiState.value.activeDevice ?: return
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.Connected(active),
                        showPairingDialog = false
                    )
                }
            }

            override fun onPairingRequired() {
                val active = _uiState.value.activeDevice ?: return
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.PairingRequired(active),
                        showPairingDialog = true
                    )
                }
            }

            override fun onPairingSuccess() {
                _uiState.update { it.copy(showPairingDialog = false) }
            }

            override fun onError(msg: String) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.Error(msg),
                        showPairingDialog = false
                    )
                }
            }

            override fun onDisconnected() {
                _uiState.update {
                    it.copy(connectionState = ConnectionState.Disconnected)
                }
            }
        }
    }

    private fun observeDiscovery() {
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }

                // Auto-connect to first discovered VU TV if disconnected
                if (_uiState.value.connectionState is ConnectionState.Disconnected && devices.isNotEmpty()) {
                    val candidate = devices.first()
                    connectToDevice(candidate)
                }
            }
        }
    }

    fun startScanning() {
        _uiState.update { it.copy(connectionState = ConnectionState.Discovering) }
        discoveryManager.startDiscovery()
    }

    fun stopScanning() {
        discoveryManager.stopDiscovery()
    }

    fun connectToDevice(device: TvDevice) {
        _uiState.update {
            it.copy(
                activeDevice = device,
                connectionState = ConnectionState.Connecting(device)
            )
        }
        viewModelScope.launch {
            protocol.connect(device)
        }
    }

    fun connectWithManualIp(ip: String) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return
        val device = TvDevice(
            name = "VU Smart 2K (Manual)",
            host = cleanIp,
            port = 6466,
            isDiscovered = false
        )
        _uiState.update { it.copy(showManualIpDialog = false) }
        connectToDevice(device)
    }

    fun submitPairingPin(pin: String) {
        val device = _uiState.value.activeDevice ?: return
        viewModelScope.launch {
            protocol.submitPairingCode(device, pin)
        }
    }

    fun disconnect() {
        protocol.disconnect()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                activeDevice = null
            )
        }
    }

    fun pressKey(keyCode: Int, keyName: String) {
        triggerHaptic()
        _uiState.update { it.copy(lastPressedKey = keyName) }
        protocol.sendKey(keyCode)
    }

    fun showManualIpDialog(show: Boolean) {
        _uiState.update { it.copy(showManualIpDialog = show) }
    }

    fun showPairingDialog(show: Boolean) {
        _uiState.update { it.copy(showPairingDialog = show) }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
        protocol.disconnect()
    }
}
