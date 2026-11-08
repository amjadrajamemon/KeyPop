package com.aetherkeys.keyboard

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, password-encrypted TEXT clipboard backups for future KeyPop
 * installations. Uses independently authenticated AES-GCM records PLUS a
 * whole-archive HMAC (ordering, metadata and truncation). Record streaming
 * avoids buffering years of history in memory. Never exports passwords or
 * data automatically. Images/GIFs are not in this text-only format.
 */
object KeyPopClipboardBackup {
    private val magic="KPCB017!".toByteArray(Charsets.US_ASCII)
    private const val iterations=200_000
    private const val maxRecordBytes=64*1024*1024
    private const val nonceSize=12
    private data class Keys(val aes:SecretKeySpec,val mac:SecretKeySpec)

    private fun keys(password:String,salt:ByteArray):Keys {
        require(password.length>=8){"Use a backup password of at least 8 characters"}
        val p=PBEKeySpec(password.toCharArray(),salt,iterations,512)
        return try {
            val data=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(p).encoded
            Keys(SecretKeySpec(data.copyOfRange(0,32),"AES"),
                SecretKeySpec(data.copyOfRange(32,64),"HmacSHA256"))
        } finally {p.clearPassword()}
    }
    private fun intBytes(value:Int):ByteArray =
        ByteBuffer.allocate(4).putInt(value).array()
    private fun cipher(key:SecretKeySpec,nonce:ByteArray,mode:Int):Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode,key,GCMParameterSpec(128,nonce))
        }
    private fun record(clip:ClipItem):ByteArray {
        val bytes=clip.text.toByteArray(Charsets.UTF_8)
        require(bytes.size<=maxRecordBytes){"Clipboard record exceeds backup safety bound"}
        val buffer=ByteArrayOutputStream(bytes.size+32)
        DataOutputStream(buffer).use {
            it.writeInt(bytes.size);it.write(bytes)
            it.writeBoolean(clip.pinned);it.writeLong(clip.time)
        }
        return buffer.toByteArray()
    }
    private fun parseRecord(bytes:ByteArray):ClipItem {
        DataInputStream(ByteArrayInputStream(bytes)).use {input->
            val length=input.readInt()
            require(length in 0..maxRecordBytes){"Invalid clipboard record length"}
            val chars=ByteArray(length);input.readFully(chars)
            val pinned=input.readBoolean()
            val time=input.readLong()
            require(input.read()==-1){"Invalid clipboard record trailing bytes"}
            return ClipItem(String(chars,Charsets.UTF_8),pinned,time)
        }
    }

    fun export(context:Context,uri:Uri,password:String):Int {
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
        val keys=keys(password,salt)
        val mac=Mac.getInstance("HmacSHA256").apply{init(keys.mac)}
        val raw=context.contentResolver.openOutputStream(uri,"w")
            ?:error("Cannot write the chosen backup destination")
        var count=0
        DataOutputStream(BufferedOutputStream(raw)).use {out->
            out.write(magic);out.write(salt)
            mac.update(magic);mac.update(salt)
            count=ClipStore(context).visitForBackup {item->
                val iv=ByteArray(nonceSize).also{SecureRandom().nextBytes(it)}
                val sealed=cipher(keys.aes,iv,Cipher.ENCRYPT_MODE).doFinal(record(item))
                out.writeInt(sealed.size);out.write(iv);out.write(sealed)
                mac.update(intBytes(sealed.size));mac.update(iv);mac.update(sealed)
            }
            out.writeInt(-1)
            mac.update(intBytes(-1))
            out.write(mac.doFinal())
        }
        return count
    }
    /** Parse a fully verified, package-private temporary plaintext buffer. */
    private fun eachVerifiedRecord(file:File,accept:(ClipItem)->Unit):Int {
        var count=0
        DataInputStream(BufferedInputStream(FileInputStream(file))).use {input->
            while(true) {
                val length=input.readInt()
                if(length==-1)break
                require(length in 0..(maxRecordBytes+40)){"Invalid backup record length"}
                val bytes=ByteArray(length);input.readFully(bytes)
                accept(parseRecord(bytes));count++
            }
            require(input.read()==-1){"Unexpected trailing backup data"}
        }
        return count
    }
    fun restore(context:Context,uri:Uri,password:String):Int {
        val raw=context.contentResolver.openInputStream(uri)
            ?:error("Cannot read the selected backup file")
        val temp=File.createTempFile("keypop-verified-backup-",".tmp",context.cacheDir)
        try {
            DataInputStream(BufferedInputStream(raw)).use {input->
                val header=ByteArray(magic.size)
                input.readFully(header)
                require(header.contentEquals(magic)){"Not a KeyPop encrypted text backup"}
                val salt=ByteArray(16);input.readFully(salt)
                val key=keys(password,salt)
                val mac=Mac.getInstance("HmacSHA256").apply {init(key.mac)}
                mac.update(header);mac.update(salt)
                DataOutputStream(BufferedOutputStream(FileOutputStream(temp))).use {plain->
                    while(true) {
                        val length=input.readInt()
                        mac.update(intBytes(length))
                        if(length==-1)break
                        require(length in 16..(maxRecordBytes+56)){
                            "Invalid encrypted backup length"
                        }
                        val iv=ByteArray(nonceSize);input.readFully(iv)
                        val sealed=ByteArray(length);input.readFully(sealed)
                        mac.update(iv);mac.update(sealed)
                        val record=try {
                            cipher(key.aes,iv,Cipher.DECRYPT_MODE).doFinal(sealed)
                        } catch(e:AEADBadTagException) {
                            throw IllegalArgumentException(
                                "Incorrect backup password or altered archive",e)
                        }
                        plain.writeInt(record.size);plain.write(record)
                    }
                    plain.writeInt(-1)
                }
                val expected=ByteArray(32);input.readFully(expected)
                require(input.read()==-1){"Unexpected bytes after encrypted backup"}
                require(MessageDigest.isEqual(expected,mac.doFinal())) {
                    "Backup authentication failed: wrong password or missing records"
                }
            }
            // Validate all rows BEFORE adding anything to the user's archive.
            eachVerifiedRecord(temp){}
            val store=ClipStore(context)
            val batch=ArrayList<ClipItem>(100)
            var imported=0
            eachVerifiedRecord(temp){clip->
                batch.add(clip)
                if(batch.size==100) {
                    imported+=store.restoreBatch(batch)
                    batch.clear()
                }
            }
            if(batch.isNotEmpty())imported+=store.restoreBatch(batch)
            return imported
        }finally {temp.delete()}
    }
}
