package com.vu.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.vu.tvremote.data.TvDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers Android TVs on the local Wi-Fi network using Android's native NsdManager.
 * Zero external libraries; acquires MulticastLock to ensure mDNS packets pass on all Android vendors.
 */
class TvDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "TvDiscoveryManager"
        const val SERVICE_TYPE_V2 = "_androidtvremote2._tcp."
        const val SERVICE_TYPE_V1 = "_androidtvremote._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredDevices = MutableStateFlow<List<TvDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<TvDevice>> = _discoveredDevices.asStateFlow()

    private var isScanning = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "Discovery failed to start: code $errorCode")
            stopDiscovery()
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "Discovery failed to stop: code $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d(TAG, "Service discovery started for $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d(TAG, "Service discovery stopped")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service candidate found: ${serviceInfo.serviceName}")
            // Resolve the service to retrieve IP address and port
            resolveService(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            _discoveredDevices.value = _discoveredDevices.value.filter {
                it.name != serviceInfo.serviceName
            }
        }
    }

    fun startDiscovery() {
        if (isScanning) return
        try {
            // Acquire Multicast lock so phone's Wi-Fi chip accepts mDNS broadcast packets
            multicastLock = wifiManager.createMulticastLock("vu_tv_multicast").apply {
                setReferenceCounted(true)
                acquire()
            }

            nsdManager.discoverServices(
                SERVICE_TYPE_V2,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
            isScanning = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NsdManager discovery", e)
        }
    }

    fun stopDiscovery() {
        if (!isScanning) return
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        isScanning = false
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress ?: return
                val port = resolvedInfo.port
                val name = resolvedInfo.serviceName ?: "VU Smart TV"

                val device = TvDevice(
                    name = cleanDeviceName(name),
                    host = host,
                    port = if (port > 0) port else 6466,
                    serviceType = resolvedInfo.serviceType ?: SERVICE_TYPE_V2,
                    isDiscovered = true
                )

                // Update list without duplicates
                val current = _discoveredDevices.value.toMutableList()
                current.removeAll { it.host == host }
                current.add(0, device)
                _discoveredDevices.value = current
            }
        })
    }

    private fun cleanDeviceName(raw: String): String {
        return raw.replace("\\032", " ").trim()
    }
}
