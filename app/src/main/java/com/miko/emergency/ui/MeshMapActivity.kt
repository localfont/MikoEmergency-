package com.miko.emergency.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.miko.emergency.databinding.ActivityMeshMapBinding
import com.miko.emergency.service.MeshForegroundService
import com.miko.emergency.ui.adapter.NodeDetailAdapter
import com.miko.emergency.utils.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeshMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeshMapBinding
    private var meshService: MeshForegroundService? = null
    private var isBound = false
    private lateinit var nodeDetailAdapter: NodeDetailAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            meshService = (service as MeshForegroundService.LocalBinder).getService()
            isBound = true
            observeNodes()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeshMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        nodeDetailAdapter = NodeDetailAdapter()
        binding.rvNodeDetails.apply {
            layoutManager = LinearLayoutManager(this@MeshMapActivity)
            adapter = nodeDetailAdapter
        }

        val localIp = NetworkUtils.getLocalIpAddress()
        binding.tvLocalUrl.text = "Yerel Sunucu: http://$localIp:8888/help"

        bindService(
            Intent(this, MeshForegroundService::class.java),
            serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    private fun observeNodes() {
        val manager = meshService?.meshManager ?: return
        binding.tvMyNodeId.text = "Node ID: ${manager.nodeId}"

        lifecycleScope.launch {
            manager.knownNodes.collectLatest { nodes ->
                nodeDetailAdapter.submitList(nodes)
                binding.tvNodeCount.text = "Toplam: ${nodes.size} düğüm (${nodes.count { it.isReachable }} aktif)"

                val hops = nodes.filter { it.hopCount > 0 }
                binding.tvHopInfo.text = if (hops.isNotEmpty()) {
                    "Çoklu-hop: ${hops.size} düğüm (maks ${hops.maxOf { it.hopCount }} hop)"
                } else {
                    "Doğrudan bağlantı"
                }
            }
        }

        lifecycleScope.launch {
            manager.meshStats.collectLatest { stats ->
                binding.tvStats.text = buildString {
                    appendLine("📊 Mesh İstatistikleri")
                    appendLine("Erişilebilir: ${stats.reachableNodes}/${stats.totalNodes}")
                    appendLine("Gateway: ${stats.gatewayNodes}")
                    appendLine("Gönderilen: ${stats.messagesSent}")
                    appendLine("İletilen: ${stats.messagesRelayed}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
    }
}
