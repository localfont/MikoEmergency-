package com.miko.emergency.mesh

import android.content.Context
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.miko.emergency.model.*
import com.miko.emergency.server.LocalHttpServer
import com.miko.emergency.utils.LocationUtils
import com.miko.emergency.utils.NetworkUtils
import com.miko.emergency.utils.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class MeshNetworkManager(private val context: Context) {

    private val TAG = "MeshNetworkManager"
    private val prefs = PreferenceManager.getInstance(context)
    val nodeId: String get() = prefs.getNodeId(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _networkStatus = MutableStateFlow(NetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus
    private val _knownNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val knownNodes: StateFlow<List<MeshNode>> = _knownNodes
    private val _incomingMessages = MutableStateFlow<List<EmergencyMessage>>(emptyList())
    val incomingMessages: StateFlow<List<EmergencyMessage>> = _incomingMessages
    private val _meshStats = MutableStateFlow(MeshStats())
    val meshStats: StateFlow<MeshStats> = _meshStats

    private var httpServer: LocalHttpServer? = null
    private val messageRouter by lazy { MessageRouter(nodeId, prefs.maxHopCount) }
    private val nodeMap = ConcurrentHashMap<String, MeshNode>()
    private var bleScanner: BluetoothMeshScanner? = null
    private var udpDiscovery: UdpBroadcastDiscovery? = null
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiDirectReceiver: WifiDirectBroadcastReceiver? = null
    private var isWifiP2pEnabled = false
    private var wifiReceiverRegistered = false
    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private val SERVICE_TYPE = "_mikoem._tcp."

    var onMessageReceived: ((EmergencyMessage) -> Unit)? = null
    var onNodeDiscovered: ((MeshNode) -> Unit)? = null

    fun initialize() {
        Log.d(TAG, "Initializing node: $nodeId")
        LocationUtils.init(context)
        startLocalServer()
        initWifiDirect()
        registerNsdService()
        startBleDiscovery()
        startUdpDiscovery()
        startNodeDiscovery()
        startStatusMonitor()
    }

    private fun startLocalServer() {
        httpServer = LocalHttpServer(
            port = prefs.localServerPort,
            nodeId = nodeId,
            onMessageReceived = { msg -> handleIncomingMessage(msg) },
            onNodeDiscovered = { node -> addOrUpdateNode(node) }
        )
        httpServer?.start()
    }

    private fun initWifiDirect() {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
            wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
            wifiDirectReceiver = WifiDirectBroadcastReceiver(
                manager = wifiP2pManager,
                channel = wifiP2pChannel,
                onStateChanged = { enabled ->
                    isWifiP2pEnabled = enabled
                    scope.launch { updateNetworkStatusAsync() }
                    if (enabled) discoverWifiPeers()
                },
                onPeersChanged = { devices -> handleWifiPeers(devices) },
                onConnectionInfoAvailable = { ownerIp ->
                    if (ownerIp != null) scope.launch { delay(1500); probeNode(ownerIp, prefs.localServerPort) }
                }
            )
            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            context.registerReceiver(wifiDirectReceiver, intentFilter)
            wifiReceiverRegistered = true
        } catch (e: Exception) { Log.e(TAG, "WiFi Direct init: ${e.message}") }
    }

    private fun discoverWifiPeers() {
        wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) { scope.launch { delay(5000); discoverWifiPeers() } }
        })
    }

    private fun handleWifiPeers(devices: List<WifiP2pDevice>) {
        devices.forEach { device ->
            addOrUpdateNode(MeshNode(
                nodeId = "WIFI-${device.deviceAddress.replace(":", "")}",
                alias = device.deviceName,
                macAddress = device.deviceAddress,
                transportMode = TransportMode.WIFI_DIRECT
            ))
        }
    }

    private fun registerNsdService() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "MikoEM-${nodeId.takeLast(6)}"
                serviceType = SERVICE_TYPE
                port = prefs.localServerPort
            }
            nsdRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, c: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, c: Int) {}
            }
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
            nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(st: String) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceType == SERVICE_TYPE && !info.serviceName.contains(nodeId.takeLast(6))) {
                        nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo, c: Int) {}
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                val host = si.host?.hostAddress ?: return
                                scope.launch { probeNode(host, si.port) }
                            }
                        })
                    }
                }
                override fun onServiceLost(i: NsdServiceInfo) {}
                override fun onDiscoveryStopped(st: String) {}
                override fun onStartDiscoveryFailed(st: String, c: Int) {}
                override fun onStopDiscoveryFailed(st: String, c: Int) {}
            }
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) { Log.e(TAG, "NSD error: ${e.message}") }
    }

    private fun startBleDiscovery() {
        bleScanner = BluetoothMeshScanner(context, nodeId) { node -> addOrUpdateNode(node) }
        bleScanner?.startAdvertising()
        bleScanner?.startScanning()
    }

    private fun startUdpDiscovery() {
        udpDiscovery = UdpBroadcastDiscovery(
            nodeId = nodeId,
            alias = prefs.alias,
            httpPort = prefs.localServerPort,
            onNodeDiscovered = { node -> addOrUpdateNode(node) }
        )
        udpDiscovery?.start()
    }

    private fun startNodeDiscovery() {
        scope.launch {
            while (isActive) {
                val localIp = NetworkUtils.getLocalIpAddress()
                if (!localIp.startsWith("127.")) scanSubnet(localIp)
                delay(45_000L)
            }
        }
    }

    private suspend fun scanSubnet(localIp: String) {
        val subnet = localIp.substringBeforeLast(".") ?: return
        try {
            withTimeout(12_000L) {
                (1..254).map { last ->
                    async {
                        val host = "$subnet.$last"
                        if (host != localIp) probeNode(host, prefs.localServerPort)
                    }
                }.awaitAll()
            }
        } catch (_: TimeoutCancellationException) {}
    }

    private suspend fun probeNode(host: String, port: Int) {
        try {
            val url = java.net.URL("http://$host:$port/node-info")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 1500; conn.readTimeout = 1500
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText(); conn.disconnect()
                val gson = com.google.gson.Gson()
                @Suppress("UNCHECKED_CAST")
                val info = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return
                val remoteId = info["nodeId"] as? String ?: return
                if (remoteId == nodeId) return
                addOrUpdateNode(MeshNode(nodeId = remoteId, ipAddress = host, port = port,
                    lastSeen = System.currentTimeMillis(), transportMode = TransportMode.LOCAL_NETWORK))
            } else conn.disconnect()
        } catch (_: Exception) {}
    }

    private fun startStatusMonitor() {
        scope.launch {
            while (isActive) {
                updateNetworkStatusAsync()
                pingKnownNodes()
                updateStats()
                delay(10_000L)
            }
        }
    }

    private suspend fun updateNetworkStatusAsync() {
        val hasInternet = NetworkUtils.hasRealInternetAccess()
        val nodes = knownNodes.value
        _networkStatus.value = NetworkStatus(
            hasInternet = hasInternet,
            hasWifiDirect = isWifiP2pEnabled,
            isWifiEnabled = NetworkUtils.isWifiEnabled(context),
            connectedNodes = nodes.count { it.isReachable },
            gatewayAvailable = nodes.any { it.hasInternet && it.isReachable }
        )
    }

    private suspend fun pingKnownNodes() {
        val now = System.currentTimeMillis()
        nodeMap.entries.removeIf { now - it.value.lastSeen > 180_000L }
        nodeMap.values.toList().forEach { node ->
            if (messageRouter.pingNode(node))
                nodeMap[node.nodeId] = node.copy(lastSeen = System.currentTimeMillis())
        }
        updateNodeList()
    }

    private fun updateStats() {
        val nodes = nodeMap.values.toList()
        _meshStats.value = MeshStats(
            totalNodes = nodes.size,
            reachableNodes = nodes.count { it.isReachable },
            gatewayNodes = nodes.count { it.hasInternet },
            messagesSent = prefs.messagesSent,
            messagesRelayed = prefs.messagesRelayed
        )
    }

    private fun handleIncomingMessage(message: EmergencyMessage) {
        messageRouter.markMessageSeen(message.id)
        val current = _incomingMessages.value.toMutableList()
        if (current.none { it.id == message.id }) {
            current.add(0, message)
            if (current.size > 100) current.removeLast()
            _incomingMessages.value = current
        }
        onMessageReceived?.invoke(message)
        if (messageRouter.shouldRelayMessage(message)) {
            scope.launch {
                prefs.incrementMessagesRelayed()
                messageRouter.updateNodes(nodeMap.values.toList())
                messageRouter.routeMessage(message, _networkStatus.value.hasInternet) {}
            }
        }
    }

    fun addOrUpdateNode(node: MeshNode) {
        if (node.nodeId == nodeId) return
        val existing = nodeMap[node.nodeId]
        val updated = existing?.copy(
            ipAddress = if (node.ipAddress.isNotEmpty()) node.ipAddress else existing.ipAddress,
            lastSeen = System.currentTimeMillis(),
            signalStrength = if (node.signalStrength != 0) node.signalStrength else existing.signalStrength,
            hasInternet = node.hasInternet || existing.hasInternet
        ) ?: node.copy(lastSeen = System.currentTimeMillis())
        nodeMap[node.nodeId] = updated
        updateNodeList()
        messageRouter.updateNodes(nodeMap.values.toList())
        onNodeDiscovered?.invoke(updated)
    }

    private fun updateNodeList() {
        _knownNodes.value = nodeMap.values.sortedByDescending { it.lastSeen }
    }

    suspend fun sendEmergencyMessage(type: MessageType, content: String, customLocation: GpsLocation? = null): EmergencyMessage {
        val location = customLocation ?: LocationUtils.getCurrentLocation(context)
        val message = EmergencyMessage(
            senderId = nodeId, senderAlias = prefs.alias,
            type = type, content = content, location = location, ttl = prefs.maxHopCount
        )
        prefs.incrementMessagesSent()
        messageRouter.markMessageSeen(message.id)
        messageRouter.updateNodes(nodeMap.values.toList())
        scope.launch { messageRouter.routeMessage(message, _networkStatus.value.hasInternet) {} }
        return message
    }

    fun destroy() {
        try { if (wifiReceiverRegistered) context.unregisterReceiver(wifiDirectReceiver) } catch (_: Exception) {}
        try { nsdManager?.unregisterService(nsdRegistrationListener) } catch (_: Exception) {}
        try { nsdManager?.stopServiceDiscovery(nsdDiscoveryListener) } catch (_: Exception) {}
        httpServer?.stop()
        bleScanner?.destroy()
        udpDiscovery?.stop()
        messageRouter.destroy()
        scope.cancel()
    }
}
