package com.aetherkeys.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Official, authenticated Google Cloud Translation Basic v2 (not a scraped
 * consumer Google Translate endpoint). Supports en, ur, sd in either direction.
 * Users must supply their own Cloud Translation key and knowingly enable online
 * translation; Google Cloud billing may apply. The key is encrypted at rest.
 */
object KeyPopCloudTranslation {
    private const val STORE="keypop_google_cloud_credentials"
    private const val ALIAS="keypop_google_cloud_aes_v1"
    private val main=Handler(Looper.getMainLooper())

    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        val existing=store.getKey(ALIAS,null) as? SecretKey
        if(existing!=null)return existing
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    fun saveKey(context:Context,value:String) {
        val secret=value.trim()
        require(secret.isNotEmpty()){"Enter a valid Google Cloud Translation API key"}
        require(secret.length<=300){"API key is unexpectedly long"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val output=cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString("nonce",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("encrypted",Base64.encodeToString(output,Base64.NO_WRAP)).apply()
    }
    fun clearKey(context:Context) {
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun configured(context:Context):Boolean {
        val store=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        return !store.getString("encrypted",null).isNullOrBlank()
    }
    private fun readKey(context:Context):String {
        val store=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        val nonce=store.getString("nonce",null) ?:throw IllegalStateException("Google Cloud key is not configured")
        val encrypted=store.getString("encrypted",null)
            ?:throw IllegalStateException("Google Cloud key is not configured")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),
            GCMParameterSpec(128,Base64.decode(nonce,Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),Charsets.UTF_8)
    }

    fun translate(context:Context,source:String,target:String,text:String,
                  done:(Result<String>)->Unit) {
        if(!KeyPopEasyTranslateDirections.supports(source,target)){
            done(Result.failure(IllegalArgumentException("Unsupported language pair")));return
        }
        if(!context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("googleCloudConsent",false)){
            done(Result.failure(IllegalStateException("Enable online Google Cloud translation in Settings.")))
            return
        }
        if(!configured(context)){
            done(Result.failure(IllegalStateException("Enter your Google Cloud Translation API key in Settings.")))
            return
        }
        Thread {
            val answer=runCatching {
                val secret=readKey(context)
                val url=URL("https://translation.googleapis.com/language/translate/v2?key="+
                    URLEncoder.encode(secret,"UTF-8"))
                val connection=url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod="POST"
                    connection.connectTimeout=10000
                    connection.readTimeout=14000
                    connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                    connection.doOutput=true
                    val body=JSONObject().put("q",text).put("source",source)
                        .put("target",target).put("format","text")
                    connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
                    if(connection.responseCode !in 200..299){
                        val code=connection.responseCode
                        throw IllegalStateException("Google Cloud returned HTTP "+code+
                            ". Check API key, billing, quotas and internet connection.")
                    }
                    val json=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
                    val result=json.getJSONObject("data").getJSONArray("translations")
                        .getJSONObject(0).getString("translatedText")
                    Html.fromHtml(result,Html.FROM_HTML_MODE_LEGACY).toString().trim()
                }finally{connection.disconnect()}
            }
            main.post{done(answer)}
        }.start()
    }
}
