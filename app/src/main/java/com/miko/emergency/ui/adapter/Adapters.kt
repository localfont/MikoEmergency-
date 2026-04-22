package com.miko.emergency.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miko.emergency.databinding.ItemNodeCardBinding
import com.miko.emergency.databinding.ItemMessageBinding
import com.miko.emergency.databinding.ItemNodeDetailBinding
import com.miko.emergency.model.EmergencyMessage
import com.miko.emergency.model.MessageType
import com.miko.emergency.model.MeshNode
import com.miko.emergency.model.TransportMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────── NodeAdapter (horizontal cards) ────────────────

class NodeAdapter : ListAdapter<MeshNode, NodeAdapter.ViewHolder>(NodeDiff()) {

    class ViewHolder(val binding: ItemNodeCardBinding) : RecyclerView.ViewHolder(binding.root)

    class NodeDiff : DiffUtil.ItemCallback<MeshNode>() {
        override fun areItemsTheSame(a: MeshNode, b: MeshNode) = a.nodeId == b.nodeId
        override fun areContentsTheSame(a: MeshNode, b: MeshNode) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNodeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        holder.binding.apply {
            tvNodeId.text = node.nodeId.take(12)
            tvTransport.text = when (node.transportMode) {
                TransportMode.WIFI_DIRECT -> "WiFi Direct"
                TransportMode.BLUETOOTH -> "Bluetooth"
                TransportMode.LOCAL_NETWORK -> "WiFi LAN"
                TransportMode.INTERNET -> "İnternet"
                TransportMode.MESH_HOP -> "Hop ${node.hopCount}"
            }
            tvStatus.text = if (node.isReachable) "● Aktif" else "○ Pasif"
            tvStatus.setTextColor(
                if (node.isReachable) root.context.getColor(android.R.color.holo_green_light)
                else root.context.getColor(android.R.color.holo_red_light)
            )
            if (node.hasInternet) {
                tvGateway.text = "GW"
                tvGateway.visibility = android.view.View.VISIBLE
            } else {
                tvGateway.visibility = android.view.View.GONE
            }
        }
    }
}

// ──────────────── MessageAdapter ────────────────

class MessageAdapter : ListAdapter<EmergencyMessage, MessageAdapter.ViewHolder>(MessageDiff()) {

    class ViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    class MessageDiff : DiffUtil.ItemCallback<EmergencyMessage>() {
        override fun areItemsTheSame(a: EmergencyMessage, b: EmergencyMessage) = a.id == b.id
        override fun areContentsTheSame(a: EmergencyMessage, b: EmergencyMessage) = a == b
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        holder.binding.apply {
            val typeIcon = when (msg.type) {
                MessageType.SOS -> "🆘"
                MessageType.LOCATION_SHARE -> "📍"
                MessageType.TEXT -> "💬"
                MessageType.ACK -> "✅"
                MessageType.PING -> "🔍"
                MessageType.DISCOVERY -> "📡"
                MessageType.GATEWAY_ANNOUNCE -> "🌐"
            }
            tvType.text = "$typeIcon ${msg.type.name}"
            tvSender.text = msg.senderAlias.ifEmpty { msg.senderId.take(12) }
            tvContent.text = msg.content.take(80)
            tvTime.text = timeFormat.format(Date(msg.timestamp))
            tvHops.text = if (msg.hopCount > 0) "hop: ${msg.hopCount}" else "direkt"

            if (msg.location != null) {
                tvLocation.text = "📍 ${"%.4f".format(msg.location.latitude)}, ${"%.4f".format(msg.location.longitude)}"
                tvLocation.visibility = android.view.View.VISIBLE
            } else {
                tvLocation.visibility = android.view.View.GONE
            }

            val bgColor = when (msg.type) {
                MessageType.SOS -> root.context.getColor(com.miko.emergency.R.color.msg_sos)
                MessageType.LOCATION_SHARE -> root.context.getColor(com.miko.emergency.R.color.msg_location)
                else -> root.context.getColor(com.miko.emergency.R.color.msg_default)
            }
            root.setCardBackgroundColor(bgColor)
        }
    }
}

// ──────────────── NodeDetailAdapter ────────────────

class NodeDetailAdapter : ListAdapter<MeshNode, NodeDetailAdapter.ViewHolder>(NodeDetailDiff()) {

    class ViewHolder(val binding: ItemNodeDetailBinding) : RecyclerView.ViewHolder(binding.root)

    class NodeDetailDiff : DiffUtil.ItemCallback<MeshNode>() {
        override fun areItemsTheSame(a: MeshNode, b: MeshNode) = a.nodeId == b.nodeId
        override fun areContentsTheSame(a: MeshNode, b: MeshNode) = a == b
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNodeDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        holder.binding.apply {
            tvNodeId.text = node.nodeId
            tvIpPort.text = if (node.ipAddress.isNotEmpty()) "${node.ipAddress}:${node.port}" else "IP bilinmiyor"
            tvTransportMode.text = node.transportMode.name
            tvLastSeen.text = "Son: ${timeFormat.format(Date(node.lastSeen))}"
            tvHopCount.text = "Hop: ${node.hopCount}"
            tvMeshUrl.text = node.meshUrl
            tvGateway.visibility = if (node.hasInternet) android.view.View.VISIBLE else android.view.View.GONE

            val statusColor = if (node.isReachable)
                root.context.getColor(android.R.color.holo_green_light)
            else root.context.getColor(android.R.color.holo_red_light)
            tvStatus.text = if (node.isReachable) "● Aktif" else "○ Pasif"
            tvStatus.setTextColor(statusColor)

            if (node.location != null) {
                tvLocation.text = "📍 ${"%.4f".format(node.location.latitude)}, ${"%.4f".format(node.location.longitude)}"
                tvLocation.visibility = android.view.View.VISIBLE
            } else {
                tvLocation.visibility = android.view.View.GONE
            }
        }
    }
}
