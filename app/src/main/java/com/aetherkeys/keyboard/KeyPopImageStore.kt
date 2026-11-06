package com.aetherkeys.keyboard

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class KeyPopImageClip(val id:String,val time:Long,val mime:String,val pinned:Boolean)

/** Encrypted image vault, bounded at 12 MiB per image and without automatic photo-library access. */
class KeyPopImageStore(private val context:Context) {
    private val folder=File(context.filesDir,"clip-images").apply{mkdirs()}
    private val prefs=context.getSharedPreferences("keypop_clip_images",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopImageClips",null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("KeyPopImageClips",
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
        require(bytes.size>28){"Corrupted encrypted image"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    @Synchronized fun list():List<KeyPopImageClip> {
        val saved=prefs.getString("items",null)?:return emptyList()
        val array=JSONArray(String(decrypt(Base64.decode(saved,Base64.NO_WRAP)),Charsets.UTF_8))
        return List(array.length()) { index->
            val item=array.getJSONObject(index)
            KeyPopImageClip(item.getString("id"),item.getLong("time"),item.getString("mime"),
                item.optBoolean("pinned"))
        }.sortedByDescending{it.time}
    }
    private fun persist(items:List<KeyPopImageClip>) {
        val array=JSONArray()
        for(item in items)array.put(JSONObject().apply{
            put("id",item.id);put("time",item.time);put("mime",item.mime);put("pinned",item.pinned)
        })
        prefs.edit().putString("items",Base64.encodeToString(
            encrypt(array.toString().toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).commit()
    }
    @Synchronized fun save(uri:Uri):Boolean {
        val mime=context.contentResolver.getType(uri) ?:return false
        if(!mime.startsWith("image/"))return false
        val input=context.contentResolver.openInputStream(uri) ?:return false
        val bytes=input.use { stream->
            val out=ByteArrayOutputStream()
            val buf=ByteArray(16384)
            while(true) {
                val read=stream.read(buf)
                if(read<0)break
                out.write(buf,0,read)
                if(out.size()>12*1024*1024)return false
            }
            out.toByteArray()
        }
        if(bytes.isEmpty())return false
        val id=UUID.randomUUID().toString()
        val file=File(folder,id+".enc")
        file.writeBytes(encrypt(bytes))
        try {persist(list()+KeyPopImageClip(id,System.currentTimeMillis(),mime,false))}
        catch(e:Exception){file.delete();throw e}
        return true
    }
    @Synchronized fun pin(id:String) {
        persist(list().map{if(it.id==id)it.copy(pinned=!it.pinned) else it})
    }
    @Synchronized fun delete(id:String) {
        persist(list().filterNot{it.id==id})
        File(folder,id+".enc").delete()
    }
    @Synchronized fun clearUnpinned() {
        val entries=list()
        persist(entries.filter{it.pinned})
        entries.filterNot{it.pinned}.forEach{File(folder,it.id+".enc").delete()}
    }
    /** Decrypt only when the user explicitly taps Paste or Share. */
    fun uriForShare(image:KeyPopImageClip):Uri? {
        val source=File(folder,image.id+".enc")
        if(!source.isFile)return null
        val ext=when(image.mime) {"image/jpeg"->".jpg";"image/gif"->".gif";
            "image/webp"->".webp";else->".png"}
        val dir=File(context.cacheDir,"keypop-shared-images").apply{mkdirs()}
        val result=File(dir,image.id+ext)
        return try{
            result.writeBytes(decrypt(source.readBytes()))
            FileProvider.getUriForFile(context,context.packageName+".files",result)
        }catch(_:Exception){null}
    }
}
