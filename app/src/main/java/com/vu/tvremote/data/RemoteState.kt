package com.vu.tvremote.data

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val device: TvDevice) : ConnectionState
    data class PairingRequired(val device: TvDevice) : ConnectionState
    data class Connected(val device: TvDevice) : ConnectionState
    data class Error(val message: String, val canRetry: Boolean = true) : ConnectionState
}

data class RemoteUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val discoveredDevices: List<TvDevice> = emptyList(),
    val activeDevice: TvDevice? = null,
    val showManualIpDialog: Boolean = false,
    val showPairingDialog: Boolean = false,
    val lastPressedKey: String? = null
)
