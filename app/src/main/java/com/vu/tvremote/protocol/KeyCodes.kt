package com.vu.tvremote.protocol

/**
 * Android Input KeyCodes for Android TV.
 * Used by com.google.android.tv.remote.service on VU Smart 2K 80V4 TV.
 */
object KeyCodes {
    // Navigation & D-Pad
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23 // OK / Select

    // System Navigation
    const val KEYCODE_BACK = 4
    const val KEYCODE_HOME = 3
    const val KEYCODE_MENU = 82

    // Media & Volume
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_VOLUME_MUTE = 164
    const val KEYCODE_POWER = 26
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_MEDIA_FAST_FORWARD = 90
    const val KEYCODE_MEDIA_REWIND = 89

    enum class Direction(val value: Int) {
        START(1), // Key Down
        END(2),   // Key Up
        SHORT(3)  // Complete click
    }
}
