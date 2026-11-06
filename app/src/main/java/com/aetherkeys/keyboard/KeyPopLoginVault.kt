package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File
import java.net.URI
import java.util.Locale

data class VaultAccount(val id:String,val target:String,val label:String,
                        val username:String,val password:String,val created:Long)

object VaultTarget {
    fun canonical(value:String):String {
        val text=value.trim().lowercase(Locale.ROOT)
        val host=if(text.startsWith("https://")) runCatching { URI(text).host.orEmpty() }.getOrDefault("") else text
        if(!Regex("[a-z0-9][a-z0-9_.-]{2,245}").matches(host)||!host.contains('.'))return ""
        if(host.contains(".."))return ""
        return host
    }
    fun matches(saved:String,actual:String):Boolean= saved.isNotEmpty() && saved==canonical(actual)
}

/** No account data leaves the device; OS-backed AES-GCM protects the saved file. */
class KeyPopLoginVault(private val context:Context) {
    private val file=File(context.filesDir,"keypop-logins.enc")
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopCredentialVault",null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("KeyPopCredentialVault",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    private fun encrypt(bytes:ByteArray):ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        return cipher.iv+cipher.doFinal(bytes)
    }
    private fun decrypt(bytes:ByteArray):ByteArray {
        require(bytes.size>=29) { "Encrypted vault is damaged" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    @Synchronized fun list():List<VaultAccount> {
        if(!file.exists())return emptyList()
        val array=JSONArray(String(decrypt(file.readBytes()),Charsets.UTF_8))
        return List(array.length()){ i->
            val json=array.getJSONObject(i)
            VaultAccount(json.getString("id"),json.getString("target"),json.optString("label"),
                json.getString("username"),json.getString("password"),json.getLong("created"))
        }.sortedByDescending{it.created}
    }
    private fun persist(logins:List<VaultAccount>) {
        val array=JSONArray()
        for(v in logins) array.put(JSONObject().apply {
            put("id",v.id);put("target",v.target);put("label",v.label)
            put("username",v.username);put("password",v.password);put("created",v.created)
        })
        val temp=File(context.filesDir,"keypop-logins.tmp")
        temp.writeBytes(encrypt(array.toString().toByteArray(Charsets.UTF_8)))
        if(!temp.renameTo(file)) {temp.delete();error("Could not save encrypted vault") }
    }
    @Synchronized fun save(target:String,label:String,user:String,password:String):Boolean {
        val canonical=VaultTarget.canonical(target)
        if(canonical.isBlank()||user.isBlank()||password.isBlank()||password.length>4096)return false
        persist(list()+VaultAccount(UUID.randomUUID().toString(),canonical,label.take(80),user.take(300),
            password,System.currentTimeMillis()))
        return true
    }
    @Synchronized fun delete(id:String) { persist(list().filterNot{it.id==id}) }
    @Synchronized fun find(target:String):List<VaultAccount> = list().filter{VaultTarget.matches(it.target,target)}
    @Synchronized fun get(id:String)=list().firstOrNull{it.id==id}
}

/** A device lock is mandatory before viewing or editing saved accounts. */
class KeyPopVaultActivity:Activity() {
    private val request=70
    private val vault by lazy{KeyPopLoginVault(this)}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val km=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(!km.isDeviceSecure) {
            Toast.makeText(this,"Configure a device screen lock first",Toast.LENGTH_LONG).show()
            finish();return
        }
        @Suppress("DEPRECATION")
        val intent=km.createConfirmDeviceCredentialIntent("KeyPop Login Vault","Unlock your saved accounts")
        if(intent==null){finish();return}
        @Suppress("DEPRECATION")
        startActivityForResult(intent,request)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==request && resultCode==RESULT_OK)render() else finish()
    }
    private fun render() {
        val screen=ScrollView(this)
        val col=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(24),dp(18),dp(24))
        }
        screen.addView(col);setContentView(screen)
        col.addView(TextView(this).apply {text="KeyPop · Secure Login Vault";textSize=23f})
        col.addView(TextView(this).apply {
            text="Your accounts are encrypted locally. Add an exact website hostname or Android package. Compatible apps can request Autofill; filling always requires device authentication."
            textSize=13f;setPadding(0,dp(13),0,dp(12))
        })
        col.addView(Button(this).apply{text="＋ Add a login";setOnClickListener{addLogin()}})
        try {
            val saved=vault.list()
            if(saved.isEmpty())col.addView(TextView(this).apply{text="No saved logins yet."})
            for(entry in saved) {
                col.addView(TextView(this).apply {
                    text=entry.target+" · "+entry.label+"\n"+entry.username+"   ·   Password: ••••••••"
                    textSize=15f;setPadding(0,dp(14),0,dp(6))
                })
                col.addView(Button(this).apply {
                    text="Delete this account"
                    setOnClickListener{
                        AlertDialog.Builder(this@KeyPopVaultActivity).setTitle("Delete account?")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Delete"){_,_->vault.delete(entry.id);render()}.show()
                    }
                })
            }
        }catch(_:Exception){
            col.addView(TextView(this).apply {
                text="Cannot decrypt saved credentials. Existing data was not overwritten."
                setTextColor(android.graphics.Color.RED)
            })
        }
    }
    private fun addLogin() {
        val form=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)}
        fun field(hint:String,kind:Int):EditText {
            val view=EditText(this).apply{
                this.hint=hint;inputType=kind;setSingleLine(true)
            }
            form.addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(53)))
            return view
        }
        val host=field("example.com or com.example.app",InputType.TYPE_CLASS_TEXT)
        val label=field("Nickname (optional)",InputType.TYPE_CLASS_TEXT)
        val username=field("Username or email",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=field("Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        AlertDialog.Builder(this).setTitle("Add secure login").setView(form)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save"){_,_->
                val ok=runCatching{vault.save(host.text.toString(),label.text.toString(),
                    username.text.toString(),password.text.toString())}.getOrDefault(false)
                Toast.makeText(this,if(ok)"Login saved" else "Could not save account",Toast.LENGTH_LONG).show()
                if(ok)render()
            }.show()
    }
}
