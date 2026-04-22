package com.miko.emergency.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.miko.emergency.R
import com.miko.emergency.databinding.ActivityEmergencyBinding
import com.miko.emergency.model.MessageType
import com.miko.emergency.service.MeshForegroundService
import com.miko.emergency.utils.LocationUtils
import com.miko.emergency.utils.PreferenceManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmergencyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyBinding
    private lateinit var prefs: PreferenceManager
    private var meshService: MeshForegroundService? = null
    private var isBound = false
    private var pulseAnimator: ValueAnimator? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MeshForegroundService.LocalBinder
            meshService = binder.getService()
            isBound = true
            observeStatus()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            meshService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        val type = intent.getStringExtra("type") ?: "SOS"
        setupUI(type)
        bindService(
            Intent(this, MeshForegroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun setupUI(type: String) {
        when (type) {
            "SOS" -> {
                binding.tvTitle.text = "🆘 ACİL SOS"
                binding.tvSubtitle.text = "Yardım çağrısı gönderilecek"
                binding.btnSend.text = "SOS GÖNDER"
                binding.btnSend.setBackgroundColor(getColor(R.color.sos_red))
                binding.etMessage.setText(prefs.sosMessage)
                startPulseAnimation()
            }
            "LOCATION" -> {
                binding.tvTitle.text = "📍 KONUM PAYLAŞ"
                binding.tvSubtitle.text = "GPS konumunuz iletilecek"
                binding.btnSend.text = "KONUM GÖNDER"
                binding.btnSend.setBackgroundColor(getColor(R.color.location_blue))
            }
            "MESSAGE" -> {
                binding.tvTitle.text = "💬 ACİL MESAJ"
                binding.tvSubtitle.text = "Mesh ağı üzerinden mesaj gönder"
                binding.btnSend.text = "MESAJ GÖNDER"
                binding.btnSend.setBackgroundColor(getColor(R.color.mesh_purple))
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {
            sendEmergencyMessage(type)
        }

        loadCurrentLocation()
    }

    private fun loadCurrentLocation() {
        lifecycleScope.launch {
            binding.tvLocation.text = "Konum alınıyor..."
            val location = LocationUtils.getCurrentLocation(this@EmergencyActivity)
            binding.tvLocation.text = if (location != null) {
                "📍 ${LocationUtils.formatLocation(location)}"
            } else {
                "⚠️ Konum alınamadı"
            }
        }
    }

    private fun sendEmergencyMessage(type: String) {
        val manager = meshService?.meshManager ?: run {
            Toast.makeText(this, "Servis bağlanamadı, yeniden deneniyor...", Toast.LENGTH_SHORT).show()
            MeshForegroundService.start(this)
            return
        }

        val messageType = when (type) {
            "SOS" -> MessageType.SOS
            "LOCATION" -> MessageType.LOCATION_SHARE
            else -> MessageType.TEXT
        }
        val content = binding.etMessage.text.toString().ifEmpty {
            when (type) {
                "SOS" -> prefs.sosMessage
                "LOCATION" -> "Konum paylaşıyorum"
                else -> "Acil mesaj"
            }
        }

        binding.btnSend.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Gönderiliyor..."

        lifecycleScope.launch {
            val message = manager.sendEmergencyMessage(messageType, content)
            val status = manager.networkStatus.value

            val statusText = when {
                status.hasInternet -> "✅ İnternet üzerinden gönderildi!"
                status.connectedNodes > 0 -> "🔗 Mesh ağı üzerinden iletildi! (${status.connectedNodes} düğüm)"
                else -> "⏳ Mesaj kuyruğa alındı, düğüm bekleniyor..."
            }

            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = statusText
            binding.tvStatus.setTextColor(
                if (status.hasInternet || status.connectedNodes > 0)
                    getColor(R.color.status_online)
                else getColor(android.R.color.holo_orange_light)
            )

            // Show mesh URL
            val meshUrl = "miko://${manager.nodeId}/help"
            binding.tvMeshUrl.text = "Mesh URL: $meshUrl"
            binding.tvMeshUrl.visibility = View.VISIBLE

            binding.btnSend.isEnabled = true
            binding.btnSend.text = "YENİDEN GÖNDER"
        }
    }

    private fun observeStatus() {
        val manager = meshService?.meshManager ?: return
        lifecycleScope.launch {
            manager.networkStatus.collectLatest { status ->
                val connText = when {
                    status.hasInternet -> "📡 İnternet bağlı"
                    status.connectedNodes > 0 -> "🔗 ${status.connectedNodes} mesh düğümü"
                    else -> "⚠️ Bağlantı yok"
                }
                binding.tvConnectionInfo.text = connText
            }
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                binding.btnSend.scaleX = scale
                binding.btnSend.scaleY = scale
            }
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
        if (isBound) unbindService(serviceConnection)
    }
}
