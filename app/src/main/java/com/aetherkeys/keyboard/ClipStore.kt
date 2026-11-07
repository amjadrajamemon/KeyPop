package com.aetherkeys.keyboard

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ClipItem(val text: String, val pinned: Boolean, val time: Long)
class ClipStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("clips", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("KeyPopClipboardAES", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("KeyPopClipboardAES",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    private fun decrypt(): String {
        val stored = prefs.getString("encrypted", null) ?: return "[]"
        return try {
            val bytes = Base64.decode(stored, Base64.DEFAULT)
            if (bytes.size < 13) return "[]"
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (error: Exception) { throw IllegalStateException("Clipboard history cannot be decrypted; no data was erased", error) }
    }
    private fun persist(items: List<ClipItem>) {
        val data = JSONArray()
        items.forEach { data.put(JSONObject().put("text", it.text).put("pinned", it.pinned).put("time", it.time)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
    @Synchronized fun list(): List<ClipItem> {
        val arr = JSONArray(decrypt())
        val out = ArrayList<ClipItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(ClipItem(o.optString("text"), o.optBoolean("pinned"), o.optLong("time")))
        }
        return out.sortedByDescending { it.time }
    }
    @Synchronized fun save(text: String) {
        if (text.isBlank() || text.length > 100000) return
        val all = list().filterNot { it.text == text }
        persist(listOf(ClipItem(text, false, System.currentTimeMillis())) + all)
    }
    @Synchronized fun pin(text: String, pinned: Boolean) =
        persist(list().map { if (it.text == text) it.copy(pinned = pinned) else it })
    @Synchronized fun delete(text: String) = persist(list().filterNot { it.text == text })
    @Synchronized fun clearUnpinned() = persist(list().filter { it.pinned })
    @Synchronized fun search(query: String, pinnedOnly: Boolean = false): List<ClipItem> =
        list().filter { (!pinnedOnly || it.pinned) && it.text.contains(query, ignoreCase = true) }
    @Synchronized fun clear() = persist(emptyList())
}
