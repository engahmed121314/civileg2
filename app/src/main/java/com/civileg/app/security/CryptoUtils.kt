package com.civileg.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cryptographic utilities for securing sensitive data at rest.
 * Uses AES-256-GCM with Android KeyStore for hardware-backed security.
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object CryptoUtils {

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CivilEngineerPro_MasterKey"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128

    // App-specific key derivation salt
    private const val APP_SALT = "CivilEngineerPro_2024_AhmedMagdy"
    private val secureRandom = SecureRandom()

    /**
     * Retrieves the master key from Android Keystore or creates it if it doesn't exist.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * Encrypts data using AES-256-GCM with the hardware-backed master key.
     */
    fun encrypt(plaintext: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
            cipher.updateAAD(APP_SALT.toByteArray(Charsets.UTF_8))

            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decrypts data using AES-256-GCM with the hardware-backed master key.
     */
    fun decrypt(encryptedBase64: String): String {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_SIZE) return ""
            
            val iv = combined.copyOfRange(0, IV_SIZE)
            val encrypted = combined.copyOfRange(IV_SIZE, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
            cipher.updateAAD(APP_SALT.toByteArray(Charsets.UTF_8))

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates a secure random token.
     */
    fun generateSecureToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Hashes a string using SHA-256.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
