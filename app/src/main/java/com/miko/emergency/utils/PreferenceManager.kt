package com.miko.emergency.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.miko.emergency.crypto.MessageEncryption

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "miko_emergency_prefs"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_ALIAS = "alias"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
        private const val KEY_SOS_MESSAGE = "sos_message"
        private const val KEY_MESSAGES_SENT = "messages_sent"
        private const val KEY_MESSAGES_RELAYED = "messages_relayed"
        private const val KEY_VIBRATE_ON_RELAY = "vibrate_on_relay"
        private const val KEY_SOUND_ON_RECEIVE = "sound_on_receive"
        private const val KEY_MAX_HOP_COUNT = "max_hop_count"
        private const val KEY_LOCAL_SERVER_PORT = "local_server_port"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getNodeId(context: Context): String {
        var nodeId = prefs.getString(KEY_NODE_ID, null)
        if (nodeId == null) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            nodeId = MessageEncryption.generateNodeId(androidId ?: "unknown")
            prefs.edit().putString(KEY_NODE_ID, nodeId).apply()
        }
        return nodeId
    }

    var alias: String
        get() = prefs.getString(KEY_ALIAS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALIAS, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var emergencyContact: String
        get() = prefs.getString(KEY_EMERGENCY_CONTACT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMERGENCY_CONTACT, value).apply()

    var sosMessage: String
        get() = prefs.getString(KEY_SOS_MESSAGE, "ACİL YARDIM GEREKİYOR! Konumumu paylaşıyorum.") ?: ""
        set(value) = prefs.edit().putString(KEY_SOS_MESSAGE, value).apply()

    var messagesSent: Int
        get() = prefs.getInt(KEY_MESSAGES_SENT, 0)
        set(value) = prefs.edit().putInt(KEY_MESSAGES_SENT, value).apply()

    var messagesRelayed: Int
        get() = prefs.getInt(KEY_MESSAGES_RELAYED, 0)
        set(value) = prefs.edit().putInt(KEY_MESSAGES_RELAYED, value).apply()

    var vibrateOnRelay: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_RELAY, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ON_RELAY, value).apply()

    var soundOnReceive: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ON_RECEIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ON_RECEIVE, value).apply()

    var maxHopCount: Int
        get() = prefs.getInt(KEY_MAX_HOP_COUNT, 10)
        set(value) = prefs.edit().putInt(KEY_MAX_HOP_COUNT, value).apply()

    var localServerPort: Int
        get() = prefs.getInt(KEY_LOCAL_SERVER_PORT, 8888)
        set(value) = prefs.edit().putInt(KEY_LOCAL_SERVER_PORT, value).apply()

    fun incrementMessagesSent() { messagesSent++ }
    fun incrementMessagesRelayed() { messagesRelayed++ }
}
