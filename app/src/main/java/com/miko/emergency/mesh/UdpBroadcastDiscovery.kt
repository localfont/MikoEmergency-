package com.miko.emergency.mesh

import android.util.Log
import com.google.gson.Gson
import com.miko.emergency.model.MeshNode
import com.miko.emergency.model.TransportMode
import com.miko.emergency.utils.NetworkUtils
import kotlinx.coroutines.*
import java.net.*

class UdpBroadcastDiscovery(
    private val nodeId: String,
    private val alias: String,
    private val httpPort: Int,
    private val onNodeDiscovered: (MeshNode) -> Unit
) {

    private val TAG = "UdpBroadcast"
    private val UDP_PORT = 8889
    private val MULTICAST_GROUP = "239.255.77.77"
    private val BROADCAST_INTERVAL = 15_000L
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: MulticastSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        startListener()
        startBroadcaster()
        Log.d(TAG, "UDP discovery started")
    }

    private fun startListener() {
        scope.launch {
            try {
                socket = MulticastSocket(UDP_PORT).apply {
                    soTimeout = 5000
                    reuseAddress = true
                    val group = InetAddress.getByName(MULTICAST_GROUP)
                    try {
                        joinGroup(group)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not join multicast group, using broadcast")
                    }
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning && isActive) {
                    try {
                        socket?.receive(packet)
                        val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        processDiscoveryPacket(data, packet.address.hostAddress ?: "")
                    } catch (e: SocketTimeoutException) {
                        // Normal timeout, continue
                    } catch (e: Exception) {
                        if (isRunning) Log.w(TAG, "UDP receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener error: ${e.message}")
            }
        }
    }

    private fun startBroadcaster() {
        scope.launch {
            while (isRunning && isActive) {
                sendDiscoveryBeacon()
                delay(BROADCAST_INTERVAL)
            }
        }
    }

    private fun sendDiscoveryBeacon() {
        val localIp = NetworkUtils.getLocalIpAddress()
        val beacon = mapOf(
            "nodeId" to nodeId,
            "alias" to alias,
            "ip" to localIp,
            "port" to httpPort,
            "protocol" to "MIKO_V1",
            "ts" to System.currentTimeMillis()
        )
        val json = gson.toJson(beacon).toByteArray(Charsets.UTF_8)

        // Try multicast
        try {
            val socket = DatagramSocket()
            val group = InetAddress.getByName(MULTICAST_GROUP)
            val packet = DatagramPacket(json, json.size, group, UDP_PORT)
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            // Fall back to broadcast
        }

        // Also try subnet broadcast
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            val broadcast = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(json, json.size, broadcast, UDP_PORT)
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast failed: ${e.message}")
        }
    }

    private fun processDiscoveryPacket(data: String, sourceIp: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(data, Map::class.java) as? Map<String, Any> ?: return
            val protocol = map["protocol"] as? String ?: return
            if (protocol != "MIKO_V1") return

            val remoteNodeId = map["nodeId"] as? String ?: return
            if (remoteNodeId == nodeId) return // Skip self

            val remoteAlias = map["alias"] as? String ?: ""
            val remoteIp = (map["ip"] as? String)?.takeIf { it.isNotEmpty() } ?: sourceIp
            val remotePort = (map["port"] as? Double)?.toInt() ?: httpPort

            val node = MeshNode(
                nodeId = remoteNodeId,
                alias = remoteAlias,
                ipAddress = remoteIp,
                port = remotePort,
                transportMode = TransportMode.LOCAL_NETWORK,
                lastSeen = System.currentTimeMillis()
            )

            Log.d(TAG, "UDP discovered: $remoteNodeId at $remoteIp:$remotePort")
            onNodeDiscovered(node)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse discovery packet: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        try {
            socket?.close()
        } catch (e: Exception) { }
        Log.d(TAG, "UDP discovery stopped")
    }
}
