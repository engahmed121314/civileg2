package com.civileg.app.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities for securing sensitive data at rest.
 * Uses AES-256-GCM for encryption.
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object CryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 32 // 256 bits
    private const val IV_SIZE = 12  // GCM recommended IV size
    private const val TAG_LENGTH = 128 // GCM auth tag length in bits

    // App-specific key derivation salt (not a secret, but adds uniqueness)
    private const val APP_SALT = "CivilEngineerPro_2024_AhmedMagdy"

    private val secureRandom = SecureRandom()

    /**
     * Generate a secure random AES-256 key.
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(KEY_SIZE)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Generate a secure random IV.
     */
    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Encrypt data using AES-256-GCM.
     * Returns: Base64(IV + ciphertext + tag)
     */
    fun encrypt(plaintext: String, key: ByteArray): String {
        val iv = generateIv()
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        cipher.updateAAD(APP_SALT.toByteArray(Charsets.UTF_8))

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt data using AES-256-GCM.
     * Input: Base64(IV + ciphertext + tag)
     */
    fun decrypt(encryptedBase64: String, key: ByteArray): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)

        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        cipher.updateAAD(APP_SALT.toByteArray(Charsets.UTF_8))

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Generate a secure random token for session management.
     */
    fun generateSecureToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Hash a string using SHA-256 (for non-secret data fingerprinting).
     */
    fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}