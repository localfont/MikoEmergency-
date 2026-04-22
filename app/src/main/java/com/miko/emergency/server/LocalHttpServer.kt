package com.miko.emergency.server

import android.util.Log
import com.google.gson.Gson
import com.miko.emergency.model.EmergencyMessage
import com.miko.emergency.model.MeshNode
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class LocalHttpServer(
    private val port: Int = 8888,
    private val nodeId: String,
    private val onMessageReceived: (EmergencyMessage) -> Unit,
    private val onNodeDiscovered: (MeshNode) -> Unit
) {

    private val TAG = "LocalHttpServer"
    private val gson = Gson()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val serverUrl: String get() = "http://0.0.0.0:$port"

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "HTTP Server started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim()] = line.substring(colonIdx + 1).trim()
                }
                line = reader.readLine()
            }

            // Read body if POST
            val body = if (method == "POST") {
                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                val chars = CharArray(contentLength)
                reader.read(chars, 0, contentLength)
                String(chars)
            } else ""

            val response = handleRequest(method, path, body, client.inetAddress.hostAddress ?: "")
            writer.print(response)
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Client handling error: ${e.message}")
        } finally {
            try { client.close() } catch (e: Exception) { }
        }
    }

    private fun handleRequest(method: String, path: String, body: String, clientIp: String): String {
        return when {
            path == "/help" || path == "/" -> {
                val html = buildHelpPage()
                buildHttpResponse(200, "text/html", html)
            }
            path == "/ping" -> {
                val json = gson.toJson(mapOf(
                    "nodeId" to nodeId,
                    "status" to "alive",
                    "timestamp" to System.currentTimeMillis()
                ))
                buildHttpResponse(200, "application/json", json)
            }
            path == "/node-info" -> {
                val json = gson.toJson(mapOf(
                    "nodeId" to nodeId,
                    "port" to port,
                    "ip" to clientIp,
                    "timestamp" to System.currentTimeMillis()
                ))
                buildHttpResponse(200, "application/json", json)
            }
            path == "/message" && method == "POST" -> {
                handleIncomingMessage(body)
                buildHttpResponse(200, "application/json", """{"status":"received"}""")
            }
            path == "/discover" -> {
                handleNodeDiscovery(body, clientIp)
                buildHttpResponse(200, "application/json", """{"nodeId":"$nodeId","status":"ok"}""")
            }
            else -> buildHttpResponse(404, "text/plain", "Not Found")
        }
    }

    private fun handleIncomingMessage(body: String) {
        try {
            val message = gson.fromJson(body, EmergencyMessage::class.java)
            if (message != null) {
                onMessageReceived(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun handleNodeDiscovery(body: String, clientIp: String) {
        try {
            val node = gson.fromJson(body, MeshNode::class.java)
            if (node != null) {
                onNodeDiscovered(node.copy(ipAddress = clientIp))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse node: ${e.message}")
        }
    }

    private fun buildHelpPage(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Miko Emergency - Mesh Node</title>
                <style>
                    body { background: #1a1a2e; color: #e0e0e0; font-family: Arial; text-align: center; padding: 20px; }
                    .sos { background: #e74c3c; color: white; padding: 20px; border-radius: 50%; 
                           width: 120px; height: 120px; display: flex; align-items: center; 
                           justify-content: center; margin: 20px auto; font-size: 28px; font-weight: bold; }
                    .info { background: #16213e; padding: 15px; border-radius: 10px; margin: 10px 0; }
                    h1 { color: #e74c3c; }
                </style>
            </head>
            <body>
                <h1>🆘 Miko Emergency</h1>
                <div class="sos">SOS</div>
                <div class="info">
                    <p><strong>Node ID:</strong> $nodeId</p>
                    <p><strong>Mesh URL:</strong> miko://$nodeId/help</p>
                    <p><strong>Zaman:</strong> ${java.util.Date()}</p>
                    <p>Bu cihaz aktif bir Mesh Ağ düğümüdür.</p>
                </div>
                <p>Miko Emergency Mesh Network v1.0</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildHttpResponse(code: Int, contentType: String, body: String): String {
        val statusText = when (code) {
            200 -> "OK"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
        return "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType; charset=UTF-8\r\n" +
                "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n" +
                body
    }
}
