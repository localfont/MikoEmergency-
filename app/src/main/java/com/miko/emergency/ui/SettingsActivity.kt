package com.miko.emergency.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.miko.emergency.databinding.ActivitySettingsBinding
import com.miko.emergency.utils.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        loadSettings()
        setupButtons()
    }

    private fun loadSettings() {
        binding.etAlias.setText(prefs.alias)
        binding.etSosMessage.setText(prefs.sosMessage)
        binding.etEmergencyContact.setText(prefs.emergencyContact)
        binding.etMaxHops.setText(prefs.maxHopCount.toString())
        binding.etServerPort.setText(prefs.localServerPort.toString())
        binding.switchAutoStart.isChecked = prefs.autoStartOnBoot
        binding.switchVibrate.isChecked = prefs.vibrateOnRelay
        binding.switchSound.isChecked = prefs.soundOnReceive

        val nodeId = prefs.getNodeId(this)
        binding.tvNodeIdDisplay.text = nodeId
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnCopyNodeId.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Node ID", prefs.getNodeId(this))
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Node ID kopyalandı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        val alias = binding.etAlias.text.toString().trim()
        val sosMessage = binding.etSosMessage.text.toString().trim()
        val contact = binding.etEmergencyContact.text.toString().trim()
        val maxHops = binding.etMaxHops.text.toString().toIntOrNull() ?: 10
        val port = binding.etServerPort.text.toString().toIntOrNull() ?: 8888

        if (alias.isEmpty()) {
            binding.tilAlias.error = "Takma ad boş olamaz"
            return
        }

        prefs.alias = alias
        prefs.sosMessage = sosMessage
        prefs.emergencyContact = contact
        prefs.maxHopCount = maxHops.coerceIn(1, 20)
        prefs.localServerPort = port.coerceIn(1024, 65535)
        prefs.autoStartOnBoot = binding.switchAutoStart.isChecked
        prefs.vibrateOnRelay = binding.switchVibrate.isChecked
        prefs.soundOnReceive = binding.switchSound.isChecked

        Toast.makeText(this, "✅ Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
        finish()
    }
}
