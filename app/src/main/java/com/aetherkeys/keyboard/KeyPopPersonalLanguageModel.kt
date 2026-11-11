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
    private val helper=object:SQLiteOpenHelper(app,"keypop_personal_phrases.db",null,2) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE next_words ("+
                "context_id TEXT NOT NULL, word_id TEXT NOT NULL, "+
                "cipher BLOB NOT NULL, uses INTEGER NOT NULL, "+
                "last_seen INTEGER NOT NULL, "+
                "PRIMARY KEY(context_id,word_id))")
            db.execSQL("CREATE INDEX idx_next_words ON next_words(context_id, uses DESC)")
            createFeedback(db)
        }
        private fun createFeedback(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS correction_feedback ("+
                "lang INTEGER NOT NULL, source_id TEXT NOT NULL, target_id TEXT NOT NULL, "+
                "accepted INTEGER NOT NULL DEFAULT 0, undone INTEGER NOT NULL DEFAULT 0, "+
                "last_seen INTEGER NOT NULL, "+
                "PRIMARY KEY(lang,source_id,target_id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_source "+
                "ON correction_feedback(lang,source_id)")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) {
            if(old<2)createFeedback(db)
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
    // Most users have no explicit correction feedback. Avoid a SQLite read
    // on every prediction for an empty feedback table.
    private var feedbackPresent:Boolean?=null
    private var cachedContextKey:String?=null
    private var cachedWords:List<String> = emptyList()
    private val tokenize=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")
    private fun normalize(value:String):String=KeyPopUnicodeWords.normalize(value)
    private fun words(text:String):List<String> =
        tokenize.findAll(normalize(text.takeLast(240))).map{it.value}
            .filter{it.length in 2..28}.take(40).toList()
    /** Never memorize numeric, URL-like, email-like, command or suspicious text. */
    fun canLearn(text:String,word:String):Boolean {
        val context=text.takeLast(100)
        return KeyPopUnicodeWords.isToken(word) &&
            !context.any{it.isDigit()} &&
            listOf("@","://","/","\\","=","#","[","]","{","}").none{it in context} &&
            context.none{it=='\n'||it=='\r'}
    }
    private fun contexts(language:Int,words:List<String>):List<String> {
        if(words.isEmpty())return emptyList()
        val last=words.takeLast(3)
        val one="l$language|"+last.last()
        val global="l$language|*"
        return when {
            last.size>=3 -> listOf("l$language|"+last.joinToString("|"),
                "l$language|"+last.takeLast(2).joinToString("|"),one,global)
            last.size==2 -> listOf("l$language|"+last.joinToString("|"),one,global)
            else ->listOf(one,global)
        }
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
        }finally{
            db.endTransaction()
            cachedContextKey=null
            cachedWords=emptyList()
        }
    }
    /** Cache only a small decrypted candidate window for the current word.
     * Previously the same 24-72 Android Keystore AES decryptions and SQLite
     * queries ran after EVERY letter, competing with the key rendering thread.
     * This memory-only cache never stores editor text or persists to disk. */
    @Synchronized fun candidates(language:Int,before:String,fragment:String="",limit:Int=3)
        :List<String> {
        if(!prefs.getBoolean("personalHistoryLearning",true) ||
            !prefs.getBoolean("learningEnabled",true))return emptyList()
        val cleanFragment=normalize(fragment)
        val prior=if(cleanFragment.isNotEmpty())
            words(before.removeSuffix(before.takeLastWhile{it.isLetter()})) else words(before)
        if(prior.isEmpty())return emptyList()
        val searchContexts=contexts(language,prior)
        val cacheKey=searchContexts.joinToString("|")
        if(cacheKey!=cachedContextKey) {
            val collected=LinkedHashSet<String>()
            for(context in searchContexts) {
                val id=signature(context)
                helper.readableDatabase.query("next_words",arrayOf("cipher"),
                    "context_id=?",arrayOf(id),null,null,"uses DESC, last_seen DESC",
                    "16").use { cursor->
                    while(cursor.moveToNext() && collected.size<36) {
                        runCatching{open(cursor.getBlob(0))}.getOrNull()
                            ?.let{collected.add(it)}
                    }
                }
                if(collected.size>=36)break
            }
            cachedWords=collected.toList()
            cachedContextKey=cacheKey
        }
        return cachedWords.asSequence()
            .filter{candidate->
                candidate!=cleanFragment && (
                    candidate.startsWith(cleanFragment) ||
                    (cleanFragment.length>=4 &&
                        KeyPopTypingIntelligence.oneEditAway(
                            cleanFragment,candidate.take(cleanFragment.length)))
                )
            }
            .take(limit.coerceIn(1,8)).toList()
    }
    /** Drop decrypted autocomplete fragments when the editor session ends. */
    @Synchronized fun dropCandidateCache() {
        cachedContextKey=null
        cachedWords=emptyList()
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
                val history=all.subList((i-3).coerceAtLeast(0),i)
                val context=history.joinToString(" ")+" "
                if(canLearn(context,all[i])){
                    observe(language,context,all[i])
                    added++
                }
            }
        }
        return added
    }
    /** Explicit acceptance or immediate undo trains the correction policy,
     * without storing raw text: source/target use keyed HMAC identifiers. */
    @Synchronized fun feedback(language:Int,original:String,replacement:String,
                               accepted:Boolean) {
        if(!prefs.getBoolean("learningEnabled",true) ||
            !prefs.getBoolean("personalHistoryLearning",true))return
        val source=normalize(original);val target=normalize(replacement)
        if(source.length !in 2..28 || target.length !in 2..28 ||
            !KeyPopUnicodeWords.isToken(source) ||
            !KeyPopUnicodeWords.isToken(target))return
        feedbackPresent=true
        val a=signature("source|"+source)
        val b=signature("target|"+target)
        val db=helper.writableDatabase
        db.execSQL("INSERT OR IGNORE INTO correction_feedback "+
            "(lang,source_id,target_id,accepted,undone,last_seen) VALUES (?,?,?,0,0,?)",
            arrayOf(language,a,b,System.currentTimeMillis()))
        db.execSQL("UPDATE correction_feedback SET "+
            (if(accepted)"accepted=MIN(accepted+1,10000)" else
                "undone=MIN(undone+1,10000)")+
            ", last_seen=? WHERE lang=? AND source_id=? AND target_id=?",
            arrayOf(System.currentTimeMillis(),language,a,b))
    }
    /** Read on background predictor only, never the keypress thread. */
    @Synchronized fun rejectionWeights(language:Int,original:String,
                              candidates:List<String>):Map<String,Int> {
        if(!prefs.getBoolean("personalHistoryLearning",true) ||
            !prefs.getBoolean("learningEnabled",true) ||
            original.length !in 3..28 || candidates.isEmpty())return emptyMap()
        if(feedbackPresent==null) {
            helper.readableDatabase.rawQuery(
                "SELECT 1 FROM correction_feedback LIMIT 1",null).use {
                feedbackPresent=it.moveToFirst()
            }
        }
        if(feedbackPresent!=true)return emptyMap()
        val id=signature("source|"+normalize(original))
        val records=HashMap<String,Int>()
        helper.readableDatabase.query("correction_feedback",
            arrayOf("target_id","accepted","undone"),"lang=? AND source_id=?",
            arrayOf(language.toString(),id),null,null,null,"64").use{cursor->
            while(cursor.moveToNext())
                records[cursor.getString(0)]=
                    (cursor.getInt(2)*2-cursor.getInt(1)).coerceAtLeast(0)
        }
        return candidates.associateWith{
            records[signature("target|"+normalize(it))]?:0
        }.filterValues{it>0}
    }
    @Synchronized fun clear() {
        helper.writableDatabase.delete("next_words",null,null)
        helper.writableDatabase.delete("correction_feedback",null,null)
        feedbackPresent=false
        cachedContextKey=null
        cachedWords=emptyList()
    }
}
