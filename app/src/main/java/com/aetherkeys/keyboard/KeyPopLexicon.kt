package com.aetherkeys.keyboard

import android.content.Context
import java.util.Locale

/**
 * Offline local word-completion engine. A redistributable SCOWL familiarity-tier
 * lexicon supplies ~39k word forms; the first two letters index each lookup
 * to avoid scanning the full dictionary on every input event.
 * This does not copy Gboard's proprietary ranking or language model.
 */
class KeyPopLexicon(context:Context) {
    private val application=context.applicationContext
    private data class RankedWord(val word:String,val tier:Int)
    private val scowlIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val index=HashMap<String,MutableList<RankedWord>>()
        // First load occurs in KeyPop's prediction executor, never in draw().
        runCatching {
            application.assets.open("keypop_english_scowl.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val tab=line.indexOf('\t')
                    if(tab<=0)return@forEach
                    val tier=line.substring(0,tab).toIntOrNull()?:return@forEach
                    val word=line.substring(tab+1)
                    if(word.length !in 2..24 || word.any { !it.isLetter() })return@forEach
                    index.getOrPut(word.take(2)){ArrayList()}.add(RankedWord(word,tier))
                }
            }
        }
        index
    }
    private val qwertyRows=listOf("qwertyuiop","asdfghjkl","zxcvbnm")
    /** Neighbouring first-letter substitutions are cheap to recover by
     * adding a few nearby two-letter buckets, not rescanning 39k words. */
    private fun nearby(letter:Char):Set<Char> {
        val row=qwertyRows.indexOfFirst{letter in it}
        if(row<0)return emptySet()
        val column=qwertyRows[row].indexOf(letter)
        val candidates=linkedSetOf<Char>()
        for(r in (row-1).coerceAtLeast(0)..(row+1).coerceAtMost(2))
            for(c in (column-1).coerceAtLeast(0)..(column+1).coerceAtMost(qwertyRows[r].lastIndex))
                candidates.add(qwertyRows[r][c])
        // Staggered bottom row: c and v are adjacent, but v is also close
        // to r's vertical column and the typed vreate => create case.
        if(letter=='v')candidates.add('c')
        return candidates-letter
    }
    private fun rankCandidates(fragment:String):List<RankedWord> {
        val input=fragment.trim().lowercase(Locale.ROOT)
        if(input.isBlank())return emptyList()
        if(input.length<2)
            return scowlIndex.asSequence().filter { it.key.startsWith(input) }
                .flatMap { it.value.asSequence() }.filter { it.tier<=10 }.toList()
        val keys=linkedSetOf(input.take(2))
        if(input.length>=3) {
            for(other in nearby(input[0]))keys.add(""+other+input[1])
            for(other in nearby(input[1]))keys.add(""+input[0]+other)
            keys.add(""+input[1]+input[0])
        }
        return keys.asSequence().flatMap {scowlIndex[it].orEmpty().asSequence()}
            .distinctBy {it.word}.toList()
    }
    private val store=context.getSharedPreferences("keypop_lexicon",Context.MODE_PRIVATE)
    private val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
    private val english=("the and that have for not with you this but his from they say her she"+
        " will one all would there their what about which when make can like time just know"+
        " take people into year your good some could them see other than then now look only"+
        " come its over think also back after use two how our work first well way even new"+
        " want because any these give day most us we are were was did does do done at an"+
        " hello world message meeting please thanks thank tomorrow today morning evening"+
        " where who why what when always never maybe okay yes no file send receive check"+
        " update latest request review document keyboard typing phone settings download"+
        " office project report approved approval good better best need available working"+
        " important confirm really great fine welcome home family friend name number"+
        " account password secure please help write translate english urdu sindhi"+
        " keyboard theme layout colors clipboard image screenshot screenshots copied saved history word words should more").split(Regex("\\s+")).toSet()
    private val urdu=("میں ہم آپ تم وہ یہ ہے ہیں تھا تھی تھے ہوں کیوں کیا کہاں کیسے کب اور"+
        " نہیں جی ہاں شکریہ بہت سلام علیکم صبح شام رات اچھا ٹھیک کام کل آج ابھی"+
        " معلوم اطلاع دوست خاندان دن وقت اہم درخواست رپورٹ دفتر کے کو کا کی"+
        " ایک دو تین چار پانچ چھ سات آٹھ نو دس استعمال کریں بھی سب ہمارا"+
        " میرا آپکا اس نے اگر لیکن پھر فیصلہ ساتھ براہ کرم").split(Regex("\\s+")).toSet()
    private val sindhi=("مان اسان توهان توھان هو هي آهي آهن هوءَ ڇا ڇو ڪٿي ڪيئن ڪڏهن"+
        " ۽ نه ها سٺو مهرباني تمام سلام صبح شام رات ٺيڪ ڪم سڀاڻي اڄ"+
        " هاڻي خبر دوست خاندان ڏينهن وقت اهم درخواست رپورٽ آفيس جي جو"+
        " کي هڪ ٻه ٽي چار پنج ڇهه ست اٺ نو ڏهه استعمال ڪريو پڻ سڀ"+
        " اسانجو منهنجو توهانجو هن انهي ڪري پر پوءِ فيصلو گڏ").split(Regex("\\s+")).toSet()

    private fun words(lang:Int):Set<String> {
        val base=when(lang){1->urdu;2->sindhi;else->english}
        return base+store.getStringSet("learned_"+lang,emptySet()).orEmpty()
    }
    fun learned(lang:Int):List<String> =
        store.getStringSet("learned_"+lang,emptySet()).orEmpty().sorted()

    fun addManual(lang:Int,word:String):Boolean {
        val value=word.trim().lowercase(Locale.ROOT)
        if(value.length !in 2..32 || value.any{!it.isLetter()})return false
        val previous=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        val manuallyAdded=store.getStringSet("manual_"+lang,emptySet()).orEmpty()
        store.edit().putStringSet("learned_"+lang,(previous+value).take(3000).toSet())
            .putStringSet("manual_"+lang,(manuallyAdded+value).take(3000).toSet())
            .apply()
        return true
    }

    fun forget(lang:Int,word:String) {
        val current=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        val normalized=word.lowercase(Locale.ROOT)
        val manual=store.getStringSet("manual_"+lang,emptySet()).orEmpty()
        store.edit().putStringSet("learned_"+lang,current-normalized)
            .putStringSet("manual_"+lang,manual-normalized).apply()
    }

    fun clearLearned(lang:Int) {
        store.edit().remove("learned_"+lang).remove("manual_"+lang).apply()
    }

    fun learn(lang:Int,word:String) {
        if(!prefs.getBoolean("learningEnabled",true))return
        val value=word.trim().lowercase(Locale.ROOT)
        if(value.length !in 2..32 || value.any{!it.isLetter()})return
        // Do not silently teach the keyboard that a known high-confidence
        // misspelling is a valid word; users can add it explicitly instead.
        if(KeyPopTypingIntelligence.correction(lang,value)!=null)return
        val old=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        if(value in old)return
        val updated=(old+value).toList().takeLast(2000).toSet()
        store.edit().putStringSet("learned_"+lang,updated).apply()
    }
    fun suggestions(lang:Int,fragment:String,limit:Int=3,previous:String="",
                    beforeCursor:String=""):List<String> {
        val learned=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        val familiar=if(lang==0)rankCandidates(fragment) else emptyList()
        val base=when(lang){1->urdu;2->sindhi;else->english}
        val dictionary=base.toList()+familiar.map {it.word}
        val tiers=if(lang==0)familiar.associate {it.word to it.tier} else emptyMap()
        return KeyPopTypingIntelligence.suggestions(lang,fragment,previous,
            learned,limit,dictionary,tiers,beforeCursor)
    }
    fun nextWords(lang:Int,previous:String):List<String> =
        KeyPopTypingIntelligence.nextWords(lang,previous)
    fun correction(lang:Int,word:String):String? {
        val normalized=word.trim().lowercase(Locale.ROOT)
        val manual=store.getStringSet("manual_"+lang,emptySet()).orEmpty()
        if(normalized in manual)return null
        val base=when(lang){1->urdu;2->sindhi;else->english}
        if(normalized in base)return null
        val learned=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        return KeyPopTypingIntelligence.correction(lang,word,learned)
    }
    fun glide(lang:Int,path:List<String>):String? {
        if(lang!=0 || path.size<3)return null
        val sequence=path.joinToString("").lowercase(Locale.ROOT)
        val start=sequence.firstOrNull() ?:return null
        val end=sequence.lastOrNull() ?:return null
        fun isSubsequence(word:String):Boolean {
            var i=0
            for(ch in sequence)if(i<word.length && word[i]==ch)i++
            return i==word.length
        }
        return (words(lang)+KeyPopTypingIntelligence.list(lang)).asSequence().filter {
            it.length>=3 && it.first()==start && it.last()==end && isSubsequence(it)
        }.minByOrNull { kotlin.math.abs(it.length-sequence.length) }
    }
    private fun editDistanceAtMost2(a:String,b:String):Int {
        if(kotlin.math.abs(a.length-b.length)>2)return 3
        val previous=IntArray(b.length+1){it}
        val current=IntArray(b.length+1)
        for(i in 1..a.length) {
            current[0]=i
            for(j in 1..b.length) current[j]=minOf(
                previous[j]+1,current[j-1]+1,previous[j-1]+if(a[i-1]==b[j-1])0 else 1)
            if((1..b.length).minOfOrNull{current[it]} ?:0 >2)return 3
            for(j in previous.indices)previous[j]=current[j]
        }
        return previous[b.length]
    }
}
