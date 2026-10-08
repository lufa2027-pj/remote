package com.vu.tvremote.data

/**
 * Representation of a target Android TV on the local Wi-Fi subnet.
 * VU Smart 2K 80V4 runs Android 9 with com.google.android.tv.remote.service.
 */
data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 6466,
    val serviceType: String = "_androidtvremote2._tcp.",
    val isDiscovered: Boolean = true
)
