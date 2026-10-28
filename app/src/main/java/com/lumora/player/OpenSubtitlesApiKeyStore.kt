package com.lumora.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the user's OpenSubtitles API key encrypted with an Android Keystore AES key. */
object OpenSubtitlesApiKeyStore {
    private const val PREFS = "lumora_secrets"
    private const val PREF_KEY = "opensubtitles_api_key"
    private const val KEY_ALIAS = "lumora_opensubtitles_api_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Synchronized
    fun read(): String {
        return try {
            val packed = AppContext.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_KEY, null) ?: return ""
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            if (bytes.size <= 12) return ""
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    @Synchronized
    fun write(value: String): Boolean = try {
        val prefs = AppContext.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value.isBlank()) {
            prefs.edit().remove(PREF_KEY).commit()
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val packed = Base64.encodeToString(cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            prefs.edit().putString(PREF_KEY, packed).commit()
        }
    } catch (_: Exception) { false }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

internal object AppContext { lateinit var app: Context }
