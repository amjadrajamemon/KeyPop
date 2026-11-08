package com.aetherkeys.keyboard

import android.content.Context
import android.net.Uri
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, authenticated text + screenshot archive. Encrypted independently
 * of the app's Android Keystore: can be restored after package/signature
 * changes when the user knows the backup password.
 *
 * KPF0211!: 8-byte magic, 16-byte salt, AES-GCM records and whole-stream HMAC.
 * Complete archive is verified BEFORE any records are imported. */
object KeyPopFullBackup {
    private val magic="KPF0211!".toByteArray(Charsets.US_ASCII)
    private const val maxEntry=36*1024*1024
    private const val iterations=200_000
    private val random=SecureRandom()
    private data class Keys(val aes:SecretKeySpec,val hmac:SecretKeySpec)
    private fun keys(password:String,salt:ByteArray):Keys {
        require(password.length>=8){"Use at least eight characters"}
        val spec=PBEKeySpec(password.toCharArray(),salt,iterations,512)
        return try{
            val result=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            Keys(SecretKeySpec(result.copyOfRange(0,32),"AES"),
                SecretKeySpec(result.copyOfRange(32,64),"HmacSHA256"))
        }finally{spec.clearPassword()}
    }
    private fun intBytes(v:Int):ByteArray=ByteBuffer.allocate(4).putInt(v).array()
    private fun crypt(key:SecretKeySpec,iv:ByteArray,mode:Int)=
        Cipher.getInstance("AES/GCM/NoPadding").apply{
            init(mode,key,GCMParameterSpec(128,iv))
        }
    private fun text(item:ClipItem):ByteArray {
        val chars=item.text.toByteArray(Charsets.UTF_8)
        require(chars.size<=16_000_000){"Clipboard entry too large"}
        return ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use{out->
                out.writeByte(1);out.writeLong(item.time);out.writeBoolean(item.pinned)
                out.writeInt(chars.size);out.write(chars)
            }
        }.toByteArray()
    }
    private fun imageRecord(item:KeyPopImageClip,data:ByteArray):ByteArray {
        require(data.size<=32*1024*1024)
        return ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use{out->
                out.writeByte(2);out.writeLong(item.time);out.writeBoolean(item.pinned)
                out.writeUTF(item.mime);out.writeUTF(item.label.take(80))
                out.writeInt(data.size);out.write(data)
            }
        }.toByteArray()
    }
    private fun entryType(entry:ByteArray):Int =
        DataInputStream(ByteArrayInputStream(entry)).use{input->
            val type=input.readUnsignedByte()
            require(type==1||type==2){"Unrecognized archive record"}
            input.readLong();input.readBoolean()
            if(type==1){
                val len=input.readInt()
                require(len in 0..16_000_000)
                val text=ByteArray(len);input.readFully(text)
            }else{
                val mime=input.readUTF();input.readUTF()
                require(mime in setOf("image/png","image/jpeg","image/webp",
                    "image/heic","image/heif"))
                val len=input.readInt();require(len in 1..32*1024*1024)
                val data=ByteArray(len);input.readFully(data)
            }
            require(input.read()==-1){"Unexpected archive record suffix"}
            type
        }
    /** Export returns [text records, screenshot records]. */
    fun export(context:Context,uri:Uri,password:String):Pair<Int,Int> {
        val salt=ByteArray(16).also{random.nextBytes(it)}
        val secret=keys(password,salt)
        val mac=Mac.getInstance("HmacSHA256").apply{init(secret.hmac)}
        val stream=context.contentResolver.openOutputStream(uri,"w")
            ?:error("Cannot open backup destination")
        var clips=0;var images=0
        DataOutputStream(BufferedOutputStream(stream)).use{out->
            out.write(magic);out.write(salt)
            mac.update(magic);mac.update(salt)
            fun record(data:ByteArray) {
                require(data.size<=maxEntry)
                val iv=ByteArray(12).also{random.nextBytes(it)}
                val sealed=crypt(secret.aes,iv,Cipher.ENCRYPT_MODE).doFinal(data)
                out.writeInt(sealed.size);out.write(iv);out.write(sealed)
                mac.update(intBytes(sealed.size));mac.update(iv);mac.update(sealed)
            }
            clips=ClipStore(context).visitForBackup {record(text(it))}
            val vault=KeyPopImageStore(context)
            for(image in vault.list()) {
                if(image.mime=="image/gif")continue
                val data=vault.readForBackup(image)
                    ?:error("Missing screenshot media; backup aborted")
                record(imageRecord(image,data))
                images++
            }
            out.writeInt(-1);mac.update(intBytes(-1))
            out.write(mac.doFinal())
        }
        return clips to images
    }
    /** No data is imported before integrity and password are verified. */
    fun restore(context:Context,uri:Uri,password:String):Pair<Int,Int> {
        val input=context.contentResolver.openInputStream(uri)
            ?:error("Cannot read backup")
        val tmp=File.createTempFile("keypop-full-verified-",".tmp",context.cacheDir)
        try{
            DataInputStream(BufferedInputStream(input)).use{source->
                val header=ByteArray(magic.size);source.readFully(header)
                require(header.contentEquals(magic)){"Unsupported full backup"}
                val salt=ByteArray(16);source.readFully(salt)
                val keys=keys(password,salt)
                val mac=Mac.getInstance("HmacSHA256").apply{init(keys.hmac)}
                mac.update(header);mac.update(salt)
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use{plain->
                    while(true){
                        val size=source.readInt();mac.update(intBytes(size))
                        if(size==-1)break
                        require(size in 16..(maxEntry+32)){"Invalid backup record"}
                        val iv=ByteArray(12);source.readFully(iv)
                        val sealed=ByteArray(size);source.readFully(sealed)
                        mac.update(iv);mac.update(sealed)
                        val record=crypt(keys.aes,iv,Cipher.DECRYPT_MODE).doFinal(sealed)
                        entryType(record)
                        plain.writeInt(record.size);plain.write(record)
                    }
                    plain.writeInt(-1)
                }
                val signature=ByteArray(32);source.readFully(signature)
                require(source.read()==-1){"Unexpected trailing backup data"}
                require(MessageDigest.isEqual(signature,mac.doFinal())){
                    "Wrong password or incomplete/altered archive"
                }
            }
            // Integrity passed. Import in batches without deleting existing data.
            var textCount=0;var imageCount=0
            val clips=ClipStore(context)
            val screenshots=KeyPopImageStore(context)
            val batch=ArrayList<ClipItem>()
            DataInputStream(BufferedInputStream(FileInputStream(tmp))).use{input->
                while(true){
                    val len=input.readInt()
                    if(len==-1)break
                    require(len in 1..maxEntry)
                    val data=ByteArray(len);input.readFully(data)
                    DataInputStream(ByteArrayInputStream(data)).use{record->
                        val type=record.readUnsignedByte()
                        val time=record.readLong()
                        val pinned=record.readBoolean()
                        if(type==1) {
                            val size=record.readInt();require(size in 0..16_000_000)
                            val bytes=ByteArray(size);record.readFully(bytes)
                            batch.add(ClipItem(String(bytes,Charsets.UTF_8),pinned,time))
                            if(batch.size>=100){
                                textCount+=clips.restoreBatch(batch)
                                batch.clear()
                            }
                        }else{
                            val mime=record.readUTF();val name=record.readUTF()
                            val size=record.readInt();require(size in 1..32*1024*1024)
                            val bytes=ByteArray(size);record.readFully(bytes)
                            if(screenshots.saveBytes(bytes,mime,name,pinned,time))imageCount++
                        }
                    }
                }
                if(batch.isNotEmpty())textCount+=clips.restoreBatch(batch)
            }
            return textCount to imageCount
        }finally{
            // Temporary plaintext records remain app-private only during
            // restore and are deleted whether verification succeeds or fails.
            tmp.delete()
        }
    }
}
