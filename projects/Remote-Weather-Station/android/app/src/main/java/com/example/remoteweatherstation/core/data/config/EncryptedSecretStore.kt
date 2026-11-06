package com.example.remoteweatherstation.core.data.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.remoteweatherstation.core.model.StationSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedSecretStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.noBackupFilesDir, SECRET_FILE)
    private val json = Json

    @Synchronized
    fun read(): StationSecrets {
        if (!file.exists()) return StationSecrets()
        val encrypted = file.readBytes()
        require(encrypted.size > IV_LENGTH_BYTES) { "Encrypted settings are incomplete" }
        val buffer = ByteBuffer.wrap(encrypted)
        val iv = ByteArray(IV_LENGTH_BYTES).also(buffer::get)
        val body = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return json.decodeFromString(cipher.doFinal(body).decodeToString())
    }

    @Synchronized
    fun write(secrets: StationSecrets) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(json.encodeToString(secrets).encodeToByteArray())
        val output = ByteBuffer.allocate(iv.size + body.size).put(iv).put(body).array()
        val temporary = File(file.parentFile, "$SECRET_FILE.tmp")
        temporary.writeBytes(output)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IllegalStateException("Could not store encrypted connection settings")
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "remote_weather_station_connection_secrets"
        const val SECRET_FILE = "encrypted_connection.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
