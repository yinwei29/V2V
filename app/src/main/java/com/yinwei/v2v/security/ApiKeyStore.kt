package com.yinwei.v2v.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES key stays in Android Keystore; only IV+ciphertext are stored in noBackupFilesDir. */
class ApiKeyStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "provider-key.bin")
    @Synchronized fun save(value: String) {
        require(value.isNotBlank() && value.length <= 1024) { "請輸入有效的 API Key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val temporary = File(file.parentFile, "provider-key.tmp")
        try {
            temporary.outputStream().use { it.write(cipher.iv.size); it.write(cipher.iv); it.write(encrypted) }
            check(temporary.renameTo(file)) { "無法儲存加密 Key" }
        } finally { temporary.delete() }
    }
    @Synchronized fun read(): String? {
        if (!file.exists()) return null
        return try {
            val data = file.readBytes()
            require(data.size in 30..2048)
            val ivSize = data[0].toInt() and 255
            require(ivSize == 12 && data.size > ivSize + 17)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(1, 1 + ivSize)))
            cipher.doFinal(data, 1 + ivSize, data.size - 1 - ivSize).toString(Charsets.UTF_8)
        } catch (_: Exception) { file.delete(); null } // Restored/invalidated key requires re-entry.
    }
    @Synchronized fun remove() {
        if (file.exists()) check(file.delete()) { "Cannot delete encrypted key" }
        val temporary = File(file.parentFile, "provider-key.tmp")
        if (temporary.exists()) check(temporary.delete()) { "Cannot delete encrypted key cache" }
    }
    fun configured(): Boolean = read() != null
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private companion object { const val ALIAS = "v2v.provider.aes.v1" }
}
