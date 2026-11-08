package com.aetherkeys.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class KeyPopImageClip(val id:String,val time:Long,val mime:String,val pinned:Boolean,
    val label:String="")

/** Encrypted screenshot files with an atomic SQLite index. Transparently
 * migrates RC19/20's encrypted SharedPreferences index without deleting it. */
class KeyPopImageStore(private val context:Context) {
    private val app=context.applicationContext
    private val folder=File(app.filesDir,"clip-images").apply{mkdirs()}
    private val legacy=app.getSharedPreferences("keypop_clip_images",Context.MODE_PRIVATE)
    private val dbHelper=object:SQLiteOpenHelper(app,"keypop_images_v21.db",null,1){
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE images (id TEXT PRIMARY KEY, "+
                "captured INTEGER NOT NULL, mime TEXT NOT NULL, label BLOB NOT NULL, "+
                "pinned INTEGER NOT NULL, digest TEXT UNIQUE)")
            db.execSQL("CREATE INDEX idx_images_recent ON images(captured DESC)")
            db.execSQL("CREATE INDEX idx_images_pinned ON images(pinned,captured DESC)")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) {
            error("Unsupported screenshot schema; existing archive preserved")
        }
    }
    companion object{private val migrationLock=Any()}
    private val key:SecretKey by lazy{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey("KeyPopImageClips",null) as? SecretKey)
            ?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder("KeyPopImageClips",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
    }
    private fun encrypt(plain:ByteArray):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,key)
        return c.iv+c.doFinal(plain)
    }
    private fun decrypt(blob:ByteArray):ByteArray {
        require(blob.size>=28){"Invalid encrypted screenshot record"}
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,blob.copyOfRange(0,12)))
        return c.doFinal(blob.copyOfRange(12,blob.size))
    }
    private fun hash(data:ByteArray):String=MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString(""){"%02x".format(it)}
    private fun insert(db:SQLiteDatabase,image:KeyPopImageClip,digest:String?=null):Long {
        val row=ContentValues().apply{
            put("id",image.id);put("captured",image.time);put("mime",image.mime)
            put("label",encrypt(image.label.toByteArray(Charsets.UTF_8)))
            put("pinned",if(image.pinned)1 else 0)
            if(digest!=null)put("digest",digest)else putNull("digest")
        }
        return db.insertWithOnConflict("images",null,row,SQLiteDatabase.CONFLICT_IGNORE)
    }
    private fun migrate() {
        if(legacy.getBoolean("sqliteMigratedV21",false))return
        synchronized(migrationLock) {
            if(legacy.getBoolean("sqliteMigratedV21",false))return
            val old=legacy.getString("items",null)
            val array=if(old==null)JSONArray()
                else JSONArray(String(decrypt(Base64.decode(old,Base64.NO_WRAP)),Charsets.UTF_8))
            val db=dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for(i in 0 until array.length()) {
                    val obj=array.getJSONObject(i)
                    val item=KeyPopImageClip(obj.getString("id"),obj.getLong("time"),
                        obj.getString("mime"),obj.optBoolean("pinned"),
                        obj.optString("label",""))
                    if(File(folder,item.id+".enc").isFile)insert(db,item)
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
            check(legacy.edit().putBoolean("sqliteMigratedV21",true).commit())
            // Retain the legacy encrypted index for rollback.
        }
    }
    private fun decode(cursor:android.database.Cursor)=KeyPopImageClip(
        cursor.getString(0),cursor.getLong(1),cursor.getString(2),
        cursor.getInt(4)!=0,String(decrypt(cursor.getBlob(3)),Charsets.UTF_8))
    @Synchronized fun list():List<KeyPopImageClip> {
        migrate()
        val out=ArrayList<KeyPopImageClip>()
        dbHelper.readableDatabase.query("images",
            arrayOf("id","captured","mime","label","pinned"),
            null,null,null,null,"captured DESC").use {
            while(it.moveToNext())out.add(decode(it))
        }
        return out
    }
    @Synchronized fun page(limit:Int=30,offset:Int=0):List<KeyPopImageClip> {
        migrate()
        val out=ArrayList<KeyPopImageClip>()
        val range=offset.coerceAtLeast(0).toString()+","+
            limit.coerceIn(1,100).toString()
        dbHelper.readableDatabase.query("images",
            arrayOf("id","captured","mime","label","pinned"),
            null,null,null,null,"captured DESC",range).use {
            while(it.moveToNext())out.add(decode(it))
        }
        return out
    }
    @Synchronized fun count():Long {
        migrate()
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM images",null).use{
            return if(it.moveToFirst())it.getLong(0) else 0L
        }
    }
    @Synchronized fun save(uri:Uri):Boolean {
        val mime=context.contentResolver.getType(uri)?:return false
        if(mime !in setOf("image/png","image/jpeg","image/webp","image/heic","image/heif"))
            return false
        val stream=context.contentResolver.openInputStream(uri)?:return false
        val bytes=stream.use{
            val out=ByteArrayOutputStream()
            val buffer=ByteArray(16384)
            while(true){
                val n=it.read(buffer)
                if(n<0)break
                out.write(buffer,0,n)
                if(out.size()>32*1024*1024)return false
            }
            out.toByteArray()
        }
        val name=runCatching{
            context.contentResolver.query(uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else""}?:""
        }.getOrDefault("").take(70).replace(Regex("[\\r\\n\\t]")," ")
        return saveBytes(bytes,mime,name.ifBlank{"Screenshot"})
    }
    /** Used by explicit password-protected image archive restoration as well. */
    @Synchronized fun saveBytes(data:ByteArray,mime:String,label:String,
        pinned:Boolean=false,time:Long=System.currentTimeMillis()):Boolean {
        if(data.isEmpty()||data.size>32*1024*1024||
            mime !in setOf("image/png","image/jpeg","image/webp","image/heic","image/heif"))
            return false
        migrate()
        val digest=hash(data)
        val db=dbHelper.writableDatabase
        val id=UUID.randomUUID().toString()
        val dest=File(folder,id+".enc")
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id FROM images WHERE digest=?",arrayOf(digest)).use{
                if(it.moveToFirst()){
                    db.setTransactionSuccessful()
                    return true
                }
            }
            val tmp=File(folder,id+".tmp")
            try{
                tmp.writeBytes(encrypt(data))
                check(tmp.renameTo(dest)){"Cannot persist encrypted screenshot"}
            }finally{tmp.delete()}
            check(insert(db,KeyPopImageClip(id,time,mime,pinned,label.take(80)),digest)>=0)
            db.setTransactionSuccessful()
            return true
        }catch(e:Exception){
            dest.delete()
            throw e
        }finally{db.endTransaction()}
    }
    @Synchronized fun search(query:String,kind:String="All"):List<KeyPopImageClip> =
        list().filter{it.mime!="image/gif" &&
            (query.isBlank()||it.label.contains(query,true)||it.mime.contains(query,true))}
    @Synchronized fun rename(id:String,label:String):Boolean {
        migrate()
        val cleaned=label.trim().take(80)
        if(cleaned.isEmpty())return false
        val row=ContentValues().apply{put("label",encrypt(cleaned.toByteArray(Charsets.UTF_8)))}
        return dbHelper.writableDatabase.update("images",row,"id=?",arrayOf(id))>0
    }
    @Synchronized fun pin(id:String) {
        migrate()
        dbHelper.writableDatabase.execSQL(
            "UPDATE images SET pinned=CASE WHEN pinned=1 THEN 0 ELSE 1 END WHERE id=?",
            arrayOf(id))
    }
    @Synchronized fun delete(id:String) {
        migrate()
        dbHelper.writableDatabase.delete("images","id=?",arrayOf(id))
        File(folder,id+".enc").delete()
    }
    @Synchronized fun clearUnpinned() {
        migrate()
        val db=dbHelper.writableDatabase
        val ids=ArrayList<String>()
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id FROM images WHERE pinned=0",null).use{
                while(it.moveToNext())ids.add(it.getString(0))
            }
            db.delete("images","pinned=0",null)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        ids.forEach{File(folder,it+".enc").delete()}
    }
    fun storageBytes():Long=folder.listFiles()?.filter{it.extension=="enc"}
        ?.sumOf{it.length()}?:0L
    fun readForBackup(image:KeyPopImageClip):ByteArray? {
        val file=File(folder,image.id+".enc")
        return if(file.isFile)decrypt(file.readBytes())else null
    }
    fun thumbnail(image:KeyPopImageClip,target:Int=144):android.graphics.Bitmap? {
        return try{
            val data=readForBackup(image)?:return null
            val meta=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
            android.graphics.BitmapFactory.decodeByteArray(data,0,data.size,meta)
            if(meta.outWidth<=0||meta.outHeight<=0)return null
            var sample=1
            while(meta.outWidth/sample>target*2||meta.outHeight/sample>target*2)
                sample*=2
            android.graphics.BitmapFactory.decodeByteArray(data,0,data.size,
                android.graphics.BitmapFactory.Options().apply {
                    inSampleSize=sample
                    inPreferredConfig=android.graphics.Bitmap.Config.RGB_565
                })
        }catch(_:Exception){null}
    }
    fun uriForShare(image:KeyPopImageClip):Uri? {
        val data=runCatching{readForBackup(image)}.getOrNull()?:return null
        val extension=when(image.mime){"image/jpeg"->".jpg";"image/webp"->".webp";
            "image/heic"->".heic";"image/heif"->".heif";else->".png"}
        val dir=File(context.cacheDir,"keypop-shared-images").apply{mkdirs()}
        val cutoff=System.currentTimeMillis()-15*60*1000L
        dir.listFiles()?.filter{it.isFile&&it.lastModified()<cutoff}?.forEach{it.delete()}
        return try{
            val file=File(dir,image.id+extension)
            file.writeBytes(data)
            FileProvider.getUriForFile(context,context.packageName+".files",file)
        }catch(_:Exception){null}
    }
}
