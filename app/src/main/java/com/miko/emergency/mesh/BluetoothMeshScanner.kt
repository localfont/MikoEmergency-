package com.miko.emergency.mesh

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.miko.emergency.model.MeshNode
import com.miko.emergency.model.TransportMode
import java.util.UUID

class BluetoothMeshScanner(
    private val context: Context,
    private val nodeId: String,
    private val onNodeFound: (MeshNode) -> Unit
) {

    private val TAG = "BleMeshScanner"

    // Miko Emergency BLE Service UUID
    private val MIKO_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val MIKO_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var isScanning = false
    private var isAdvertising = false

    init {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun startAdvertising() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
            Log.w(TAG, "No BLUETOOTH_ADVERTISE permission")
            return
        }
        if (isAdvertising) return
        bluetoothAdapter?.takeIf { it.isEnabled } ?: return

        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null) {
            Log.w(TAG, "BLE advertising not supported")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        // Encode nodeId (first 8 chars) as manufacturer data
        val nodeBytes = nodeId.take(8).toByteArray(Charsets.US_ASCII).copyOf(8)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(MIKO_SERVICE_UUID))
            .addManufacturerData(0x4D4B, nodeBytes) // 'MK' prefix
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                isAdvertising = true
                Log.d(TAG, "BLE advertising started for node: $nodeId")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed: $errorCode")
            }
        }

        try {
            bleAdvertiser?.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting BLE advertising: ${e.message}")
        }
    }

    fun startScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "No BLUETOOTH_SCAN permission")
            return
        }
        if (isScanning) return
        bluetoothAdapter?.takeIf { it.isEnabled } ?: return

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MIKO_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processScanResult(result)
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { processScanResult(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                isScanning = false
            }
        }

        try {
            bleScanner?.startScan(listOf(filter), settings, callback)
            isScanning = true
            Log.d(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting BLE scan: ${e.message}")
        }
    }

    private fun processScanResult(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            val manufacturerData = record.getManufacturerSpecificData(0x4D4B) ?: return
            val remoteNodeId = "EMRG-" + String(manufacturerData, Charsets.US_ASCII).trim()
            val deviceAddress = result.device?.address ?: return

            if (remoteNodeId == nodeId) return // Skip self

            val node = MeshNode(
                nodeId = remoteNodeId,
                macAddress = deviceAddress,
                signalStrength = result.rssi,
                transportMode = TransportMode.BLUETOOTH,
                lastSeen = System.currentTimeMillis()
            )

            Log.d(TAG, "BLE node discovered: $remoteNodeId (RSSI: ${result.rssi})")
            onNodeFound(node)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing BLE scan result: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        try {
            bleScanner?.stopScan(object : ScanCallback() {})
        } catch (e: Exception) { }
        isScanning = false
        Log.d(TAG, "BLE scanning stopped")
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) return
        try {
            bleAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
        } catch (e: Exception) { }
        isAdvertising = false
        Log.d(TAG, "BLE advertising stopped")
    }

    fun destroy() {
        stopScanning()
        stopAdvertising()
    }
}
