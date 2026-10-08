package com.vu.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.vu.tvremote.ui.RemoteScreen
import com.vu.tvremote.ui.theme.VuTvRemoteTheme
import com.vu.tvremote.viewmodel.RemoteViewModel

/**
 * VU Smart 2K 80V4 Android TV Remote Control App
 * Target TV: Model Smart 2K ATV4 running Android 9
 * Service: com.google.android.tv.remote.service
 */
class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge layout for immersive remote experience
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            VuTvRemoteTheme {
                RemoteScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startScanning()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopScanning()
    }
}
