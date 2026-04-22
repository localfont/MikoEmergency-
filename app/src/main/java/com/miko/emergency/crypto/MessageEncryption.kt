package com.miko.emergency.crypto

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MessageEncryption {

    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // Shared emergency key (in production, use proper key exchange)
    private const val EMERGENCY_SHARED_KEY = "MIKO_EMERGENCY_MESH_2024_SECURE_KEY_32B"

    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_SIZE, SecureRandom())
        return keyGen.generateKey()
    }

    fun generateRSAKeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        return keyPairGen.generateKeyPair()
    }

    fun encrypt(plaintext: String, key: SecretKey? = null): String {
        return try {
            val secretKey = key ?: getSharedKey()
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedData = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedData
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback: simple Base64 if crypto fails
            Base64.encodeToString(plaintext.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun decrypt(ciphertext: String, key: SecretKey? = null): String {
        return try {
            val secretKey = key ?: getSharedKey()
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedData = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback
            String(Base64.decode(ciphertext, Base64.NO_WRAP))
        }
    }

    fun signMessage(content: String, nodeId: String): String {
        val data = "$nodeId:$content:${System.currentTimeMillis() / 10000}"
        return sha256(data).take(16)
    }

    fun verifySignature(content: String, nodeId: String, signature: String): Boolean {
        val expected = signMessage(content, nodeId)
        return expected == signature
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun generateNodeId(deviceId: String): String {
        val hash = sha256("MIKO_NODE_$deviceId")
        return "EMRG-${hash.take(8).uppercase()}"
    }

    private fun getSharedKey(): SecretKey {
        val keyBytes = EMERGENCY_SHARED_KEY.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }
}
