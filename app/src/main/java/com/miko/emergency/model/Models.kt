package com.miko.emergency.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class MessageType {
    SOS,
    LOCATION_SHARE,
    TEXT,
    ACK,
    PING,
    DISCOVERY,
    GATEWAY_ANNOUNCE
}

enum class MessageStatus {
    PENDING,
    SENDING,
    RELAYED,
    DELIVERED,
    FAILED
}

enum class TransportMode {
    INTERNET,
    WIFI_DIRECT,
    BLUETOOTH,
    LOCAL_NETWORK,
    MESH_HOP
}

@Parcelize
data class GpsLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class EmergencyMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val senderAlias: String = "",
    val targetId: String = "BROADCAST",
    val type: MessageType = MessageType.SOS,
    val content: String = "",
    val location: GpsLocation? = null,
    val ttl: Int = 10,
    val hopCount: Int = 0,
    val hopPath: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = "",
    val encryptedPayload: String = "",
    var status: MessageStatus = MessageStatus.PENDING,
    var transportMode: TransportMode = TransportMode.MESH_HOP
) : Parcelable

@Parcelize
data class MeshNode(
    val nodeId: String = "",
    val alias: String = "",
    val deviceName: String = "",
    val macAddress: String = "",
    val ipAddress: String = "",
    val port: Int = 8888,
    val hasInternet: Boolean = false,
    val signalStrength: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val location: GpsLocation? = null,
    val transportMode: TransportMode = TransportMode.WIFI_DIRECT,
    val hopCount: Int = 0
) : Parcelable {
    val meshUrl: String get() = "miko://$nodeId/help"
    val localUrl: String get() = "http://$ipAddress:$port"
    val isReachable: Boolean get() = System.currentTimeMillis() - lastSeen < 60_000L
}

data class NetworkStatus(
    val hasInternet: Boolean = false,
    val hasWifiDirect: Boolean = false,
    val hasBluetooth: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val connectedNodes: Int = 0,
    val gatewayAvailable: Boolean = false
)

data class MeshStats(
    val totalNodes: Int = 0,
    val reachableNodes: Int = 0,
    val gatewayNodes: Int = 0,
    val messagesSent: Int = 0,
    val messagesRelayed: Int = 0,
    val uptime: Long = 0L
)
