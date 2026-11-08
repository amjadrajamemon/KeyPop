package com.aetherkeys.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** There is no arbitrary item-count or age eviction. Storage is device-limited. */
data class ClipItem(val text:String,val pinned:Boolean,val time:Long,val id:Long=0L)

/**
 * Each clip has a separately AES-GCM-encrypted SQLite row. Unlike a single
 * SharedPreferences JSON blob, saving a new clip does not rewrite the entire
 * archive. Saved clips stay until the user deletes them or clears app data.
 */
class ClipStore(context:Context) {
    private val app=context.applicationContext
    private val legacy=app.getSharedPreferences("clips",Context.MODE_PRIVATE)
    private val helper=object:SQLiteOpenHelper(app,"keypop_clip_history.db",null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE clips (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "cipher BLOB NOT NULL, pinned INTEGER NOT NULL DEFAULT 0, " +
                "copied_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX idx_clip_newest ON clips(_id DESC)")
        }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
            // Never destroy user's history in a version upgrade.
            throw IllegalStateException("Unsupported clipboard archive schema")
        }
    }
    companion object { private val migrationLock=Any() }

    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopClipboardAES",null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("KeyPopClipboardAES",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    private fun encrypt(text:String):ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        return cipher.iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
    private fun decrypt(blob:ByteArray):String {
        require(blob.size>=28){"Invalid encrypted clipboard record"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,blob.copyOfRange(0,12)))
        return String(cipher.doFinal(blob.copyOfRange(12,blob.size)),Charsets.UTF_8)
    }
    private fun oldItems():List<ClipItem> {
        val raw=legacy.getString("encrypted",null)?:return emptyList()
        // Decryption errors must never silently replace an archive with empty data.
        val json=JSONArray(decrypt(Base64.decode(raw,Base64.DEFAULT)))
        return (0 until json.length()).mapNotNull { index->
            json.optJSONObject(index)?.let {
                ClipItem(it.optString("text"),it.optBoolean("pinned"),it.optLong("time"))
            }
        }
    }
    private fun migrateIfNeeded() {
        if(legacy.getBoolean("sqlite_migrated",false))return
        synchronized(migrationLock) {
            if(legacy.getBoolean("sqlite_migrated",false))return
            val historical=oldItems()
            val db=helper.writableDatabase
            db.beginTransaction()
            try {
                // Old JSON was newest-first; insert oldest-first to preserve order.
                for(item in historical.asReversed())insert(db,item)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            check(legacy.edit().putBoolean("sqlite_migrated",true).commit()) {
                "Clipboard history migration marker could not be saved"
            }
            // Retain the old encrypted blob as a non-destructive migration backup.
        }
    }
    private fun insert(db:SQLiteDatabase,item:ClipItem):Long {
        val values=ContentValues().apply {
            put("cipher",encrypt(item.text))
            put("pinned",if(item.pinned)1 else 0)
            put("copied_at",item.time)
        }
        return db.insertOrThrow("clips",null,values)
    }
    private fun record(cursor:Cursor):ClipItem =
        ClipItem(decrypt(cursor.getBlob(1)),cursor.getInt(2)!=0,cursor.getLong(3),
            cursor.getLong(0))
    private fun query(pinnedOnly:Boolean,limit:String?=null):List<ClipItem> {
        migrateIfNeeded()
        val output=ArrayList<ClipItem>()
        helper.readableDatabase.query("clips",arrayOf("_id","cipher","pinned","copied_at"),
            if(pinnedOnly)"pinned=1" else null,null,null,null,"_id DESC",limit).use {cursor->
            while(cursor.moveToNext())output.add(record(cursor))
        }
        return output
    }
    @Synchronized fun list():List<ClipItem> = query(false)
    /** Indexed paging avoids drawing/decrypting the entire history at once. */
    @Synchronized fun page(limit:Int=100,offset:Int=0,pinnedOnly:Boolean=false):List<ClipItem> =
        query(pinnedOnly,"${offset.coerceAtLeast(0)},${limit.coerceIn(1,200)}")
    @Synchronized fun count(pinnedOnly:Boolean=false):Long {
        migrateIfNeeded()
        helper.readableDatabase.rawQuery(
            if(pinnedOnly)"SELECT COUNT(*) FROM clips WHERE pinned=1"
            else "SELECT COUNT(*) FROM clips",null).use {c->
            return if(c.moveToFirst())c.getLong(0) else 0L
        }
    }
    @Synchronized fun save(text:String) {
        // Per-item bound only; there is NO count cap and NO automatic eviction.
        // No item-count/retention limit; only a generous per-entry safety
        // ceiling (16 million characters) to guard against memory exhaustion.
        if(text.isBlank() || text.length>16_000_000)return
        migrateIfNeeded()
        insert(helper.writableDatabase,ClipItem(text,false,System.currentTimeMillis()))
    }
    @Synchronized fun pin(id:Long,pinned:Boolean) {
        migrateIfNeeded()
        helper.writableDatabase.execSQL("UPDATE clips SET pinned=? WHERE _id=?",
            arrayOf(if(pinned)1 else 0,id))
    }
    @Synchronized fun pin(text:String,pinned:Boolean) {
        for(item in list())if(item.text==text)pin(item.id,pinned)
    }
    @Synchronized fun delete(id:Long) {
        migrateIfNeeded()
        helper.writableDatabase.delete("clips","_id=?",arrayOf(id.toString()))
    }
    @Synchronized fun delete(text:String) {
        for(item in list())if(item.text==text)delete(item.id)
    }
    @Synchronized fun clearUnpinned() {
        migrateIfNeeded()
        helper.writableDatabase.delete("clips","pinned=0",null)
    }
    @Synchronized fun search(query:String,pinnedOnly:Boolean=false):List<ClipItem> =
        query(pinnedOnly).filter { it.text.contains(query,ignoreCase=true) }
    /** Visit records in a SQLite cursor so a backup does not have to load
     * years of clipboard history into RAM. Oldest first for natural restore. */
    @Synchronized fun visitForBackup(visit:(ClipItem)->Unit):Int {
        migrateIfNeeded()
        var count=0
        helper.readableDatabase.query("clips",
            arrayOf("_id","cipher","pinned","copied_at"),
            null,null,null,null,"_id ASC").use {cursor->
            while(cursor.moveToNext()) {
                visit(record(cursor))
                count++
            }
        }
        return count
    }
    /** Preserve timestamp, duplicates and pinned state when importing a
     * password-encrypted backup; append instead of erasing existing history. */
    @Synchronized fun restoreBatch(items:List<ClipItem>):Int {
        if(items.isEmpty())return 0
        migrateIfNeeded()
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            items.forEach { insert(db,it) }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return items.size
    }
    @Synchronized fun clear() {
        migrateIfNeeded()
        helper.writableDatabase.delete("clips",null,null)
    }
}
