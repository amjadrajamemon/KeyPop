package com.aetherkeys.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * On-device adaptive word sequence ranking. Learns only words deliberately
 * committed by KeyPop; no access to other apps' message histories.
 *
 * No full sentence or raw context is stored. Contexts and candidate IDs are
 * HMAC-SHA256 values using an Android Keystore key, candidate words are
 * individually AES-256-GCM-encrypted. Frequency remains queryable locally.
 */
class KeyPopPersonalLanguageModel(context:Context) {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("keypop",Context.MODE_PRIVATE)
    private val helper=object:SQLiteOpenHelper(app,"keypop_personal_phrases.db",null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE next_words ("+
                "context_id TEXT NOT NULL, word_id TEXT NOT NULL, "+
                "cipher BLOB NOT NULL, uses INTEGER NOT NULL, "+
                "last_seen INTEGER NOT NULL, "+
                "PRIMARY KEY(context_id,word_id))")
            db.execSQL("CREATE INDEX idx_next_words ON next_words(context_id, uses DESC)")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) {
            error("Unsupported personal dictionary schema: protect existing data")
        }
    }
    private val secureKey:SecretKey by lazy {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopPersonalHMACv1",null) as? SecretKey)
            ?:KeyGenerator.getInstance("HmacSHA256","AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder("KeyPopPersonalHMACv1",
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
                generateKey()
            }
    }
    private val aesKey:SecretKey by lazy {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopPersonalAESv1",null) as? SecretKey)
            ?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder("KeyPopPersonalAESv1",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
    }
    private fun signature(value:String):String {
        val mac=Mac.getInstance("HmacSHA256").apply{init(secureKey)}
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it)}
    }
    private fun seal(word:String):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,aesKey)
        return c.iv+c.doFinal(word.toByteArray(Charsets.UTF_8))
    }
    private fun open(data:ByteArray):String {
        require(data.size>=28)
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,aesKey,GCMParameterSpec(128,data.copyOfRange(0,12)))
        return String(c.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8)
    }
    private val tokenize=Regex("[\\p{L}]{2,28}")
    private fun normalize(value:String):String=value.lowercase(Locale.ROOT)
    private fun words(text:String):List<String> =
        tokenize.findAll(normalize(text.takeLast(240))).map{it.value}.take(40).toList()
    /** Never memorize numeric, URL-like, email-like, command or suspicious text. */
    fun canLearn(text:String,word:String):Boolean {
        val context=text.takeLast(100)
        return word.length in 2..28 && word.all{it.isLetter()} &&
            !context.any{it.isDigit()} &&
            listOf("@","://","/","\\","=","#","[","]","{","}").none{it in context} &&
            context.none{it=='\n'||it=='\r'}
    }
    private fun contexts(language:Int,words:List<String>):List<String> {
        if(words.isEmpty())return emptyList()
        val last=words.takeLast(2)
        val one="l$language|"+last.last()
        // Trigram > bigram > personal global frequency. This is why
        // repeated words can reappear outside their original sentence.
        val global="l$language|*"
        return if(last.size>=2)
            listOf("l$language|"+last.joinToString("|"),one,global)
            else listOf(one,global)
    }
    /** Pass a snapshot fetched once on Space, not a live InputConnection. */
    @Synchronized fun observe(language:Int,before:String,committed:String) {
        if(!prefs.getBoolean("learningEnabled",true) ||
            !prefs.getBoolean("personalHistoryLearning",true) ||
            !canLearn(before,committed))return
        val word=normalize(committed)
        val history=words(before.removeSuffix(before.takeLastWhile{it.isLetter()}))
        if(history.isEmpty())return
        val db=helper.writableDatabase
        db.beginTransaction()
        try {
            for(context in contexts(language,history)){
                val key=signature(context)
                val id=signature("word|"+word)
                val values=ContentValues().apply{
                    put("context_id",key);put("word_id",id)
                    put("cipher",seal(word))
                    put("uses",0);put("last_seen",System.currentTimeMillis())
                }
                db.insertWithOnConflict("next_words",null,values,SQLiteDatabase.CONFLICT_IGNORE)
                db.execSQL("UPDATE next_words SET uses=MIN(uses+1,1000000), "+
                    "last_seen=? WHERE context_id=? AND word_id=?",
                    arrayOf(System.currentTimeMillis(),key,id))
            }
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    /** Limits first; decrypts at most 24 candidates on the background worker. */
    @Synchronized fun candidates(language:Int,before:String,fragment:String="",limit:Int=3)
        :List<String> {
        if(!prefs.getBoolean("personalHistoryLearning",true) ||
            !prefs.getBoolean("learningEnabled",true))return emptyList()
        val cleanFragment=normalize(fragment)
        val prior=if(cleanFragment.isNotEmpty())
            words(before.removeSuffix(before.takeLastWhile{it.isLetter()})) else words(before)
        if(prior.isEmpty())return emptyList()
        val result=LinkedHashSet<String>()
        for(context in contexts(language,prior)) {
            val id=signature(context)
            helper.readableDatabase.query("next_words",arrayOf("cipher","uses"),
                "context_id=?",arrayOf(id),null,null,"uses DESC, last_seen DESC",
                "24").use{cursor->
                while(cursor.moveToNext() && result.size<limit.coerceIn(1,8)) {
                    val value=runCatching{open(cursor.getBlob(0))}.getOrNull()?:continue
                    if(value.startsWith(cleanFragment) &&
                        value!=cleanFragment)result.add(value)
                }
            }
        }
        return result.toList().take(limit.coerceIn(1,8))
    }
    /** Optional one-time import from the user's already saved, encrypted
     * TEXT clipboard archive. Explicit UI consent is required. Only short,
     * non-sensitive-looking natural-language clips are eligible. */
    fun learnFromSavedClip(language:Int,text:String):Int {
        if(text.length !in 10..1000 || text.any{it.isDigit()} ||
            listOf("@","://","/","\\","=","#").any{it in text})return 0
        val segments=text.split(Regex("[.!?\\n\\r]+"))
        var added=0
        for(segment in segments){
            val all=words(segment)
            if(all.size !in 2..75)continue
            for(i in 1 until all.size) {
                val history=all.subList((i-2).coerceAtLeast(0),i)
                val context=history.joinToString(" ")+" "
                if(canLearn(context,all[i])){
                    observe(language,context,all[i])
                    added++
                }
            }
        }
        return added
    }
    @Synchronized fun clear() {
        helper.writableDatabase.delete("next_words",null,null)
    }
}
