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
    @Volatile private var scowlReady=false
    private val firstLetterIndex=HashMap<Char,List<RankedWord>>()
    private val scowlIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val index=HashMap<String,MutableList<RankedWord>>()
        val oneLetter=HashMap<Char,MutableList<RankedWord>>()
        // Only the background prediction worker is allowed to initialize
        // the 39k-word file. The UI keypress path checks scowlReady first.
        runCatching {
            application.assets.open("keypop_english_scowl.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val tab=line.indexOf('\t')
                    if(tab<=0)return@forEach
                    val tier=line.substring(0,tab).toIntOrNull()?:return@forEach
                    val word=line.substring(tab+1)
                    if(word.length !in 2..24 || word.any { !it.isLetter() })return@forEach
                    val item=RankedWord(word,tier)
                    index.getOrPut(word.take(2)){ArrayList()}.add(item)
                    if(tier<=10)oneLetter.getOrPut(word[0]){ArrayList()}.add(item)
                }
            }
        }
        for((letter,candidates) in oneLetter) {
            // A single typed letter must NOT enumerate thousands of words.
            // The 96 familiar completions are cached once at preload.
            firstLetterIndex[letter]=candidates
                .sortedWith(compareBy<RankedWord>{it.tier}.thenBy{it.word.length})
                .take(96)
        }
        scowlReady=true
        index
    }
    /** Warm up the compressed prefix index on the dedicated prediction worker. */
    fun warmup() {scowlIndex.size}
    fun isReady():Boolean=scowlReady
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
            return firstLetterIndex[input[0]].orEmpty()
        val keys=linkedSetOf(input.take(2))
        if(input.length>=3) {
            for(other in nearby(input[0]))keys.add(""+other+input[1])
            for(other in nearby(input[1]))keys.add(""+input[0]+other)
            keys.add(""+input[1]+input[0])
        }
        return keys.asSequence().flatMap {scowlIndex[it].orEmpty().asSequence()}
            .filter { it.word.startsWith(input.take(2)) ||
                (input.length>=3 && it.tier<=20) }
            .distinctBy {it.word}
            .sortedWith(compareBy<RankedWord>{if(it.word.startsWith(input))0 else 1}
                .thenBy{it.tier}.thenBy{it.word.length})
            .take(240).toList()
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
        // Do not silently teach invalid spellings as correct dictionary words.
        // Explicit personal-dictionary entries remain available for names.
        // Missing dictionary warmup must never stall Space. No new
        // speculative word is learned before its dictionary can verify it.
        if(lang==0 && !scowlReady)return
        if(lang==0 && scowlIndex[value.take(2)].orEmpty().none {it.word==value} &&
            value !in english)return
        val updated=(old+value).toList().takeLast(15000).toSet()
        store.edit().putStringSet("learned_"+lang,updated).apply()
    }
    fun suggestions(lang:Int,fragment:String,limit:Int=3,previous:String="",
                    beforeCursor:String=""):List<String> {
        val learned=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        val familiar=if(lang==0)rankCandidates(fragment) else emptyList()
        val base=when(lang){1->urdu;2->sindhi;else->english}
        val prefix=fragment.lowercase(Locale.ROOT).take(2)
        // A large personal dictionary must not create thousands of
        // edit-distance matrices for one keypress. Indexed SCOWL candidates
        // remain the primary source; select only relevant learned entries.
        val relevantLearned=if(prefix.isBlank())emptySet() else
            learned.asSequence().filter {it.startsWith(prefix)}.take(128).toSet()
        val dictionary=base.toList()+familiar.map {it.word}
        val tiers=if(lang==0)familiar.associate {it.word to it.tier} else emptyMap()
        return KeyPopTypingIntelligence.suggestions(lang,fragment,previous,
            relevantLearned,limit,dictionary,tiers,beforeCursor)
    }
    fun nextWords(lang:Int,previous:String,beforeCursor:String=""):List<String> =
        KeyPopTypingIntelligence.nextWords(lang,previous,beforeCursor)
    fun correction(lang:Int,word:String,beforeCursor:String=""):String? {
        val normalized=word.trim().lowercase(Locale.ROOT)
        val manual=store.getStringSet("manual_"+lang,emptySet()).orEmpty()
        if(normalized in manual)return null
        val base=when(lang){1->urdu;2->sindhi;else->english}
        if(normalized in base)return null
        val learned=store.getStringSet("learned_"+lang,emptySet()).orEmpty()
        KeyPopTypingIntelligence.correction(lang,word,learned)?.let{return it}
        // Never initialize the asset dictionary from the IME main thread.
        // High-confidence typo mapping remains active until it is warm.
        if(lang==0 && !scowlReady)return null
        if(lang!=0 || normalized.length !in 4..20 || !normalized.all{it in 'a'..'z'} ||
            normalized in learned)return null
        // Protect actual SCOWL words (including plurals), even if the
        // small curated lexicon did not include them.
        if(scowlIndex[normalized.take(2)].orEmpty().any{it.word==normalized})
            return null
        val context=KeyPopPredictionContext.parse(beforeCursor,word)
        data class Scored(val word:String,val value:Int)
        val ranked=rankCandidates(normalized).asSequence()
            .filter{it.tier<=20 && it.word!=normalized &&
                KeyPopTypingIntelligence.oneEditAway(normalized,it.word)}
            .map {
                val grammar=KeyPopPredictionContext.score(context,normalized,it.word)
                Scored(it.word,(if(it.tier<=10)120 else 70)+grammar)
            }
            .sortedWith(compareByDescending<Scored>{it.value}.thenBy{it.word})
            .take(3).toList()
        val best=ranked.firstOrNull()?:return null
        val next=ranked.getOrNull(1)
        // Automatic edits must be unambiguous. Otherwise expose suggestions
        // to the user (e.g. worke could mean worker OR worked).
        return if(next==null || best.value-next.value>=80) best.word else null
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
