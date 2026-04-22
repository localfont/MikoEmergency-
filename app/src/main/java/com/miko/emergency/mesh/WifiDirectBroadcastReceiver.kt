package com.miko.emergency.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?,
    private val onStateChanged: (Boolean) -> Unit,
    private val onPeersChanged: (List<WifiP2pDevice>) -> Unit,
    private val onConnectionInfoAvailable: (String?) -> Unit
) : BroadcastReceiver() {

    private val TAG = "WifiDirectReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d(TAG, "WiFi P2P state: ${if (isEnabled) "ENABLED" else "DISABLED"}")
                onStateChanged(isEnabled)
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager?.requestPeers(channel) { peerList ->
                    val devices = peerList.deviceList.toList()
                    Log.d(TAG, "Peers discovered: ${devices.size}")
                    onPeersChanged(devices)
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                manager?.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        val ownerAddress = info.groupOwnerAddress?.hostAddress
                        Log.d(TAG, "Connected! Group owner: $ownerAddress, isOwner: ${info.isGroupOwner}")
                        onConnectionInfoAvailable(if (info.isGroupOwner) "0.0.0.0" else ownerAddress)
                    } else {
                        onConnectionInfoAvailable(null)
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d(TAG, "This device changed")
            }
        }
    }
}
