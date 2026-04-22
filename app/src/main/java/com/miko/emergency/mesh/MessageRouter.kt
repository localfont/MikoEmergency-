package com.miko.emergency.mesh

import android.util.Log
import com.google.gson.Gson
import com.miko.emergency.crypto.MessageEncryption
import com.miko.emergency.model.EmergencyMessage
import com.miko.emergency.model.MessageStatus
import com.miko.emergency.model.MessageType
import com.miko.emergency.model.MeshNode
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MessageRouter(
    private val nodeId: String,
    private val maxHopCount: Int = 10
) {

    private val TAG = "MessageRouter"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenMessages = ConcurrentHashMap<String, Long>()
    private val routingTable = ConcurrentHashMap<String, MeshNode>()

    private var knownNodes: List<MeshNode> = emptyList()

    // Cloud endpoints for when internet is available
    private val cloudEndpoints = listOf(
        "https://api.miko-emergency.net/v1/sos",
        "https://emergency-relay.example.com/message"
    )

    fun updateNodes(nodes: List<MeshNode>) {
        knownNodes = nodes
        nodes.forEach { routingTable[it.nodeId] = it }
        // Clean stale entries
        val now = System.currentTimeMillis()
        seenMessages.entries.removeIf { now - it.value > 300_000L }
    }

    fun shouldRelayMessage(message: EmergencyMessage): Boolean {
        // Don't relay our own messages back
        if (message.senderId == nodeId) return false
        // Don't relay if TTL exceeded
        if (message.ttl <= 0 || message.hopCount >= maxHopCount) return false
        // Don't relay if already seen recently
        val lastSeen = seenMessages[message.id]
        if (lastSeen != null && System.currentTimeMillis() - lastSeen < 60_000L) return false
        return true
    }

    fun markMessageSeen(messageId: String) {
        seenMessages[messageId] = System.currentTimeMillis()
    }

    suspend fun routeMessage(
        message: EmergencyMessage,
        hasInternet: Boolean,
        onStatusUpdate: (MessageStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        markMessageSeen(message.id)

        // Step 1: Try internet first
        if (hasInternet) {
            Log.d(TAG, "Routing via internet...")
            val success = sendViaInternet(message)
            if (success) {
                onStatusUpdate(MessageStatus.DELIVERED)
                return@withContext true
            }
        }

        // Step 2: Look for gateway node in mesh
        val gateway = knownNodes.firstOrNull { it.hasInternet && it.isReachable }
        if (gateway != null) {
            Log.d(TAG, "Routing via gateway: ${gateway.nodeId}")
            val success = forwardToNode(message, gateway)
            if (success) {
                onStatusUpdate(MessageStatus.RELAYED)
                return@withContext true
            }
        }

        // Step 3: Broadcast to all reachable nodes (multi-hop)
        val relayMessage = message.copy(
            ttl = message.ttl - 1,
            hopCount = message.hopCount + 1,
            hopPath = message.hopPath + nodeId
        )

        var relayed = false
        val reachableNodes = knownNodes.filter { it.isReachable && it.nodeId != message.senderId }

        for (node in reachableNodes) {
            val success = forwardToNode(relayMessage, node)
            if (success) {
                relayed = true
                Log.d(TAG, "Relayed hop ${relayMessage.hopCount} to ${node.nodeId}")
            }
        }

        if (relayed) {
            onStatusUpdate(MessageStatus.RELAYED)
        } else {
            onStatusUpdate(MessageStatus.PENDING)
        }
        relayed
    }

    private suspend fun sendViaInternet(message: EmergencyMessage): Boolean {
        val encryptedPayload = MessageEncryption.encrypt(gson.toJson(message))
        val payload = gson.toJson(mapOf(
            "nodeId" to message.senderId,
            "type" to message.type.name,
            "payload" to encryptedPayload,
            "timestamp" to message.timestamp
        ))

        for (endpoint in cloudEndpoints) {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Miko-Node", message.senderId)
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }

                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299) {
                    Log.d(TAG, "Message delivered via internet to $endpoint")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reach $endpoint: ${e.message}")
            }
        }
        return false
    }

    suspend fun forwardToNode(message: EmergencyMessage, node: MeshNode): Boolean {
        return try {
            val url = URL("${node.localUrl}/message")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Miko-From", nodeId)
            connection.doOutput = true
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val payload = gson.toJson(message)
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward to ${node.nodeId}: ${e.message}")
            false
        }
    }

    suspend fun pingNode(node: MeshNode): Boolean {
        return try {
            val url = URL("${node.localUrl}/ping")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val code = connection.responseCode
            connection.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    fun buildAckMessage(originalId: String): EmergencyMessage {
        return EmergencyMessage(
            senderId = nodeId,
            targetId = originalId,
            type = MessageType.ACK,
            content = "ACK:$originalId"
        )
    }

    fun buildDiscoveryMessage(): EmergencyMessage {
        return EmergencyMessage(
            senderId = nodeId,
            targetId = "BROADCAST",
            type = MessageType.DISCOVERY,
            content = "MIKO_DISCOVERY:$nodeId"
        )
    }

    fun destroy() {
        scope.cancel()
    }
}
