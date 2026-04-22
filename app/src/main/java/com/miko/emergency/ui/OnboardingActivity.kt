package com.miko.emergency.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.miko.emergency.databinding.ActivityOnboardingBinding
import com.miko.emergency.utils.PreferenceManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: PreferenceManager

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_WIFI_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            proceedToMain()
        } else {
            Toast.makeText(this,
                "Bazı izinler reddedildi. Uygulama tam çalışmayabilir.",
                Toast.LENGTH_LONG).show()
            proceedToMain()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        binding.btnGrantPermissions.setOnClickListener {
            val alias = binding.etAlias.text.toString().trim()
            if (alias.isEmpty()) {
                binding.tilAlias.error = "Lütfen bir takma ad girin"
                return@setOnClickListener
            }
            prefs.alias = alias
            requestPermissions()
        }

        binding.btnSkip.setOnClickListener {
            val alias = binding.etAlias.text.toString().trim()
            if (alias.isNotEmpty()) prefs.alias = alias
            proceedToMain()
        }
    }

    private fun requestPermissions() {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) {
            proceedToMain()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun proceedToMain() {
        prefs.isFirstLaunch = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
