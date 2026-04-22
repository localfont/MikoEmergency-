package com.miko.emergency.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

object NetworkUtils {

    private val CONNECTIVITY_TEST_URLS = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.google.com",
        "https://cloudflare.com"
    )

    suspend fun hasRealInternetAccess(): Boolean = withContext(Dispatchers.IO) {
        for (url in CONNECTIVITY_TEST_URLS) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = false
                connection.connect()
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..399) return@withContext true
            } catch (e: Exception) {
                continue
            }
        }
        false
    }

    fun isConnectedToNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun enableWifi(context: Context) {
        // Note: on Android 10+, apps cannot directly enable WiFi
        // We guide user to settings instead
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.contains('.') && !hostAddr.startsWith("169.254")) {
                            return hostAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "192.168.49.1" // WiFi Direct group owner default
    }

    fun isHostReachable(host: String, timeout: Int = 3000): Boolean {
        return try {
            InetAddress.getByName(host).isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }

    fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == "wlan0") {
                    val mac = iface.hardwareAddress ?: continue
                    return mac.joinToString(":") { "%02X".format(it) }
                }
            }
            "00:00:00:00:00:00"
        } catch (e: Exception) {
            "00:00:00:00:00:00"
        }
    }

    fun formatHopPath(path: List<String>): String {
        return path.joinToString(" → ")
    }

    fun getNetworkQuality(context: Context): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return 0
        val caps = cm.getNetworkCapabilities(network) ?: return 0
        return caps.linkDownstreamBandwidthKbps
    }
}
