package com.miko.emergency.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.miko.emergency.R
import com.miko.emergency.databinding.ActivityMainBinding
import com.miko.emergency.model.NetworkStatus
import com.miko.emergency.service.MeshForegroundService
import com.miko.emergency.ui.adapter.NodeAdapter
import com.miko.emergency.ui.adapter.MessageAdapter
import com.miko.emergency.utils.PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceManager
    private var meshService: MeshForegroundService? = null
    private var isBound = false

    private lateinit var nodeAdapter: NodeAdapter
    private lateinit var messageAdapter: MessageAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MeshForegroundService.LocalBinder
            meshService = binder.getService()
            isBound = true
            observeMeshData()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            meshService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        setupUI()
        setupRecyclerViews()
        startAndBindService()
    }

    private fun setupUI() {
        val nodeId = prefs.getNodeId(this)
        binding.tvNodeId.text = nodeId
        binding.tvAlias.text = prefs.alias.ifEmpty { "Adsız Kullanıcı" }

        binding.btnSOS.setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java).apply {
                putExtra("type", "SOS")
            })
        }

        binding.btnShareLocation.setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java).apply {
                putExtra("type", "LOCATION")
            })
        }

        binding.btnSendMessage.setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java).apply {
                putExtra("type", "MESSAGE")
            })
        }

        binding.btnMeshMap.setOnClickListener {
            startActivity(Intent(this, MeshMapActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.fabEmergency.setOnClickListener {
            startActivity(Intent(this, EmergencyActivity::class.java).apply {
                putExtra("type", "SOS")
            })
        }
    }

    private fun setupRecyclerViews() {
        nodeAdapter = NodeAdapter()
        binding.rvNodes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = nodeAdapter
        }

        messageAdapter = MessageAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }
    }

    private fun startAndBindService() {
        MeshForegroundService.start(this)
        val intent = Intent(this, MeshForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeMeshData() {
        val manager = meshService?.meshManager ?: return

        lifecycleScope.launch {
            manager.networkStatus.collectLatest { status ->
                updateNetworkStatusUI(status)
            }
        }

        lifecycleScope.launch {
            manager.knownNodes.collectLatest { nodes ->
                nodeAdapter.submitList(nodes)
                binding.tvNodeCount.text = "${nodes.count { it.isReachable }} aktif düğüm"
                binding.tvEmptyNodes.visibility = if (nodes.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            manager.incomingMessages.collectLatest { messages ->
                messageAdapter.submitList(messages.take(20))
                binding.tvEmptyMessages.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            manager.meshStats.collectLatest { stats ->
                binding.tvStatsSent.text = "Gönderilen: ${stats.messagesSent}"
                binding.tvStatsRelayed.text = "İletilen: ${stats.messagesRelayed}"
                binding.tvStatsNodes.text = "Toplam Düğüm: ${stats.totalNodes}"
            }
        }
    }

    private fun updateNetworkStatusUI(status: NetworkStatus) {
        // Internet status
        val (internetText, internetColor) = if (status.hasInternet) {
            "İnternet: BAĞLI" to getColor(R.color.status_online)
        } else {
            "İnternet: BAĞLI DEĞİL" to getColor(R.color.status_offline)
        }
        binding.tvInternetStatus.text = internetText
        binding.tvInternetStatus.setTextColor(internetColor)

        // Mesh status
        val (meshText, meshColor) = when {
            status.connectedNodes > 0 -> "Mesh: ${status.connectedNodes} düğüm" to getColor(R.color.status_mesh)
            status.hasWifiDirect || status.isWifiEnabled -> "Mesh: Taranıyor..." to getColor(R.color.status_scanning)
            else -> "Mesh: Pasif" to getColor(R.color.status_offline)
        }
        binding.tvMeshStatus.text = meshText
        binding.tvMeshStatus.setTextColor(meshColor)

        // Gateway
        binding.tvGatewayStatus.text = if (status.gatewayAvailable)
            "Gateway: MEVCUT" else "Gateway: YOK"
        binding.tvGatewayStatus.setTextColor(
            if (status.gatewayAvailable) getColor(R.color.status_online)
            else getColor(R.color.status_offline)
        )

        // WiFi Direct indicator
        binding.ivWifiDirect.setColorFilter(
            if (status.hasWifiDirect) getColor(R.color.status_online)
            else getColor(R.color.status_inactive)
        )
        // Bluetooth indicator
        binding.ivBluetooth.setColorFilter(
            if (status.hasBluetooth) getColor(R.color.status_online)
            else getColor(R.color.status_inactive)
        )

        // Overall status card
        val cardColor = when {
            status.hasInternet -> R.color.card_internet
            status.connectedNodes > 0 -> R.color.card_mesh
            else -> R.color.card_offline
        }
        binding.cardNetworkStatus.setCardBackgroundColor(getColor(cardColor))
    }

    override fun onResume() {
        super.onResume()
        binding.tvAlias.text = prefs.alias.ifEmpty { "Adsız Kullanıcı" }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
