package com.aetherkeys.keyboard

import java.util.Locale
import kotlin.math.abs

/**
 * Offline assistance for three languages. Small curated lexicons and conversational
 * context; never accesses the network or analyzes password fields.
 * Not a replacement for neural predictions and language-level grammars.
 */
object KeyPopTypingIntelligence {
    private val english=("about above accept account across action address after again against agree all almost already also always amazing amount another answer anyone anything application approve approved around available aware back bank banking because become before begin being below best better between birthday both build business calendar call camera cannot change check child choose clear click close code color common company complete confirm contact continue copy correct could create current custom customer data date day decision default delete design detail device different direct document done download easy edit email enable enough enter error example excellent family favorite feature feel file find finish first follow forgot form friend from full function future general get give good great group happen happy have hello help here history home hour important improve include information inside install issue just keep keyboard know language latest learn leave letter like link list login look make manage many maybe meeting message minimum minutes modern money month more most move much name near need never next normal note number office only open option order other output page password people personal phone photo please possible prepare present pretty previous privacy project properly quality question quick ready really receive recent recommend remember remove reply report request require result review right save screen search secure select send service setting share show simple some source space special speed start status still storage store style support system team thank thanks their them there these thing think this time today tomorrow tool top touch translate typing update upload user using version video view wait want week welcome what when where which while will window with work word words screenshot screenshots world write yes your should would could information details additional important much more other their there though though through receive necessary correct writing spelling punctuation predictive prediction definitely recommend said she he it is were was try again very why whatever both everyone everything something nothing someone someday yesterday we they're isn't that's can't don't doing worked worker workers likewise improvement improvements improving improved improvements improving typing completion completed completing corrected suggestion suggestions predicting prediction predicted predictive completion completions progress progressing download downloads downloaded downloading translation translations translating translate sent sending send").split(' ').filter{it.isNotBlank()}.distinct()
    private val urdu=("آپ آپکا آپکی آپکے آپکو آج ابھی اچھا اچھی اچھے اگر اطلاع ان اندر اور ایک بار بہت بہترین براہ بات بعد پہلے پھر تمام تم تھوڑا تیار تیز جواب جانب جان جانچ جاری جگہ جہاں جتنا جب جس جسے جن جی درخواست دیر دن دوبارہ دوست دین دنیا دیکھ دکھائی رہا رہی رہے رکھ رہا سارا ساتھ سلام سال سب سبق سچ سوال سے صبح صرف شکریہ شامل شام شاندار شروع شخص ضروری فیصلہ فی الحال قبل قلم کل کام کرنے کرتا کرتے کرنا کرم کچھ کئی کس کیسے کیوں کیا کہاں کون کوئی کہ کے کی کیونکہ کو گئی گا گی گئے گزر گیا گھر گیا ہے ہیں ہوتا ہونے ہوئی ہوں ہو ہم ہمارا ہماری ہمارے ہمیں یہاں یہ یہی یاد یقینی واقعی وقت والے والا والی وقت محفوظ معلومات مکمل میرا میری میرے میں نہیں نہ نیا نئے نئی نام نوٹ نرم نرم نہایت درست دستاویز دفتر ماہ مہینہ ملاقات مدد محبت مثال مناسب مشورہ مریض مرحلہ مسئلہ ضروریات طلب استعمال رابطہ رپورٹ کام کاغذ ترجمہ تحریر زبان سندھی اردو انگریزی الفاظ قبول بھی کچھ خریداری مکمل کریں کردی دیجئے بھیجیں پیش معلومات فرما دیجیے").split(' ').filter{it.isNotBlank()}.distinct()
    private val sindhi=("اڄ اڃا اوهان توهان توھان تون مان منهنجو منهنجي منهنجا اسان اسانجو اسانجي اسانجا آهي آھن آهن آھيان آهيان آھيو آيو اچي اچو اچي ٿو اچن اڳ بعد پوءِ جيڪڏھن جيڪڏهن پر ۽ يا به بلڪل تمام سلام مهرباني سٺو سٺي سٺا ٺيڪ ٺيڪ آهي ڪيئن ڪڏهن ڪٿي ڇو ڇا ڪير ڪيترو ڪيتري جڏهن جتي گهر ڪم ڏينھن ڏينهن وقت سڀاڻي اڄ شام رات صبح پوءِ دوست خاندان معلومات ڄاڻ وقت درخواست رپوٽ رپورٽ آفيس دفتر ڪاغذ ترجمو ٻولي انگريزي سنڌي اردو مهرباني ڪري ٻڌايو ٻڌائي ٻڌاءِ ڏيکاريو سمجهيو صحيح غلط نالو فون رابطو نمبر پيغام جواب تيار منهنجو اڪائونٽ محفوظ حفاظتي جائزو پوء ڪيو ڪري ڪرڻ لاءِ کان ۾ کي سان جو جي جا هڪ ٻه ٽي چار پنج ڇهه ست اٺ نو ڏهه نئون نئين سٺو سٺي حاصل ڪرڻ چاهيان ٿو ٿي سگهندو مدد ڪندؤ مهرباني مبارڪ خوش ٿي حاضر آهي").split(' ').filter{it.isNotBlank()}.distinct()
    private val follow=mapOf(
        0 to mapOf(
            "thank" to listOf("you"), "good" to listOf("morning","evening","night"),
            "see" to listOf("you"),"please" to listOf("confirm","review","send"),
            "can" to listOf("you","we"),"happy" to listOf("birthday"),
            "looking" to listOf("forward"),"best" to listOf("regards"),
            "kind" to listOf("regards"),"how" to listOf("are","much"),
            "let" to listOf("me"),"i" to listOf("am","will","have"),
            "it" to listOf("is","was","will"),
            "we" to listOf("are","have","will"),
            "they" to listOf("are","have","will"),
            "you" to listOf("are","can","have"),
            "are" to listOf("you","we","there"),
            "is" to listOf("the","a","there"),
            "this" to listOf("is","was","will"),
            "the" to listOf("best","next","same"),
            "please" to listOf("confirm","check","review"),
            "i'm" to listOf("going","happy","ready"),
            "could" to listOf("you","we"),
            "would" to listOf("you","be"),
            "can" to listOf("you","we","I"),
            "do" to listOf("you","we"),
            "want" to listOf("to","a"),
            "need" to listOf("to","a"),
            "going" to listOf("to"),
            "how" to listOf("are","much","can"),
            "what" to listOf("is","are","if"),
            "when" to listOf("will","is","can"),
            "where" to listOf("is","are"),
            "should" to listOf("we","I"),
            "will" to listOf("be","you","need"),
            "have" to listOf("a","been","to"),
            "thank" to listOf("you","you!"),
            "best" to listOf("regards","wishes"),
            "happy" to listOf("birthday","to"),
            "good" to listOf("morning","evening","luck"),
            "more" to listOf("information","details","than"),
            "show" to listOf("me","the"),
            "tell" to listOf("me","us"),
            "send" to listOf("me","the"),
            "give" to listOf("me","us"),
            "there" to listOf("is","are"),
            "their" to listOf("work","details"),
            "what" to listOf("is","are","can"),
            "should" to listOf("be","we","I"),
            "please" to listOf("check","share","confirm")),
        1 to mapOf(
            "براہ" to listOf("کرم"),"شکریہ" to listOf("بہت"),
            "السلام" to listOf("علیکم"),"آپ" to listOf("کا","کی","کے"),
            "کیا" to listOf("آپ"),"کل" to listOf("صبح")),
        2 to mapOf(
            "مهرباني" to listOf("ڪري"),"توهان" to listOf("ڪيئن","جو"),
            "مان" to listOf("ٺيڪ","اڄ"),"سلام" to listOf("عليڪم"),
            "سڀاڻي" to listOf("صبح")))
    fun list(language:Int):List<String> = when(language) {
        1->urdu;2->sindhi;else->english
    }
    private fun normalized(value:String)=value.trim().lowercase(Locale.ROOT)
    // High-confidence corrections for common neighbouring-key, dropped-key
    // and transposition mistakes; never transmit typed text to a server.
    private val commonTypos=mapOf(
        "tge" to "the","hte" to "the","teh" to "the","thw" to "the",
        "shoukd" to "should","shold" to "should","shoudl" to "should",
        "woudl" to "would","coudl" to "could","adn" to "and",
        "taht" to "that","wiht" to "with","yuo" to "you",
        "youre" to "you're","thier" to "their","becuase" to "because",
        "recieve" to "receive","seperate" to "separate",
        "definately" to "definitely","diferent" to "different",
        "langauge" to "language","remeber" to "remember",
        "wierd" to "weird","dont" to "don't","cant" to "can't",
        "im" to "I'm","ive" to "I've","couldnt" to "couldn't",
        "doesnt" to "doesn't","wasnt" to "wasn't",
        "shoud" to "should","wht" to "what","whst" to "what",
        "thsi" to "this","thn" to "then","plese" to "please",
        "mroe" to "more","moer" to "more",
        "wrod" to "word","screeshots" to "screenshots",
        "screeenshots" to "screenshots","screenshotss" to "screenshots",
        "mesage" to "message","messge" to "message",
        "vreate" to "create","vreated" to "created",
        "screensots" to "screenshots",
        "screnshots" to "screenshots","screenshto" to "screenshot"
    )
    private val frequentEnglish=setOf("the","and","you","your","should","would",
        "could","more","this","that","with","what","where","when",
        "there","their","they","have","will","please","thank",
        "hello","good","morning","today","tomorrow","information")
    private fun candidateScore(input:String,candidate:String,distance:Int,
                               known:Boolean,contextual:Boolean,tier:Int?):Int {
        val prefix=candidate.startsWith(input)
        val priority=if(candidate in frequentEnglish)32 else 0
        // SCOWL size is a familiarity tier, not a per-word usage frequency.
        // Common tier-10 words win over obscure completions, while user
        // learning and sentence context can still refine the order.
        val familiarity=when(tier){10->75;20->43;35->17;else->0}
        val tailPenalty=(candidate.length-input.length).coerceAtLeast(0).coerceAtMost(16)*3
        return (if(prefix)205 else 125-distance*30) +
            (if(known)22 else 0)+(if(contextual)40 else 0)+priority+
            familiarity-tailPenalty
    }
    // Optimal-string-alignment edit distance counts an adjacent swapped pair
    // as one typo, unlike ordinary Levenshtein (e.g. "teh" → "the").
    fun keyboardDistance(a:String,b:String):Int {
        if(kotlin.math.abs(a.length-b.length)>2)return 3
        val d=Array(a.length+1){IntArray(b.length+1)}
        for(i in 0..a.length)d[i][0]=i
        for(j in 0..b.length)d[0][j]=j
        for(i in 1..a.length)for(j in 1..b.length) {
            val sub=if(a[i-1]==b[j-1])0 else 1
            d[i][j]=minOf(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+sub)
            if(i>1 && j>1 && a[i-1]==b[j-2] && a[i-2]==b[j-1])
                d[i][j]=minOf(d[i][j],d[i-2][j-2]+1)
        }
        return d[a.length][b.length].coerceAtMost(3)
    }
    fun suggestions(language:Int,fragment:String,previous:String,
                    learned:Set<String> = emptySet(),limit:Int=3,
                    dictionary:List<String> = emptyList(),
                    wordTiers:Map<String,Int> = emptyMap(),
                    beforeCursor:String = ""):List<String> {
        val prefix=normalized(fragment)
        if(prefix.length !in 1..32)return emptyList()
        val prior=normalized(previous)
        val contextual=follow[language]?.get(prior).orEmpty()
        val words=(list(language)+learned.map(::normalized)+contextual+dictionary).distinct()
        data class Candidate(val text:String,val score:Int)
        val candidates=ArrayList<Candidate>()
        val seen=HashSet<String>()
        val context=if(language==0 && beforeCursor.isNotBlank())
            KeyPopPredictionContext.parse(beforeCursor,fragment) else null
        // High-confidence correction must appear even when the original token
        // is only three letters long (tge -> the).
        if(language==0 && prefix in commonTypos){
            val fixed=commonTypos.getValue(prefix)
            candidates.add(Candidate(fixed,500))
            seen.add(fixed)
        }
        for(word in words) {
            if(!seen.add(word)||word==prefix)continue
            val starts=word.startsWith(prefix)
            val edit=if(!starts && prefix.length>=3)
                keyboardDistance(prefix,word.take(prefix.length))
                else 3
            if(!starts && edit>1)continue
            val grammar=if(context!=null)
                KeyPopPredictionContext.score(context,fragment,word) else 0
            // A bare base verb after a strong "are you" context is an
            // incompatible candidate, not merely a low-ranked suggestion.
            if(grammar<=-200)continue
            candidates.add(Candidate(word,candidateScore(prefix,word,edit,
                word in learned,word in contextual,wordTiers[word])+grammar))
        }
        return candidates.sortedWith(compareByDescending<Candidate>{it.score}
            .thenBy{it.text.length}.thenBy{it.text}).take(limit.coerceIn(1,8))
            .map{it.text}
    }
    /** Next-word predictions when the cursor follows a space; always available offline. */
    fun nextWords(language:Int,previous:String):List<String> {
        val last=normalized(previous)
        val contextual=follow[language]?.get(last).orEmpty()
        val defaults=when(language) {
            1 ->listOf("آپ","میں","براہ","جی","شکریہ","یہ")
            2 ->listOf("توهان","مان","مهرباني","ڇا","ٺيڪ","اسان")
            else ->listOf("I","The","Please","Thanks","We","Hello")
        }
        return (contextual+defaults).distinct().take(3)
    }
    /** Preserve valid vocabulary and plural forms. Fuzzy guesses are suggestions,
     * NOT forced replacement. Explicit high-confidence typos auto-correct. */
    fun correction(language:Int,typed:String,learned:Set<String> = emptySet()):String? {
        val word=normalized(typed)
        if(language!=0 || word.length !in 3..32)return null
        if(word in list(language))return null
        // A previously *passively learned* typo must not suppress a high-
        // confidence correction (an explicit personal dictionary entry will
        // override this earlier, in KeyPopLexicon).
        commonTypos[word]?.let{return it}
        if(word in learned)return null
        return null
    }
    fun contextualReplies(language:Int,text:String):List<String> {
        val source=normalized(text)
        return when(language){
            1 -> when {
                "شکریہ" in source -> listOf("خوش آمدید!", "کوئی بات نہیں۔", "بہت شکریہ۔")
                "؟" in source || "کیا" in source -> listOf("جی ضرور۔","میں دیکھ کر بتاتا ہوں۔","براہ کرم تفصیل بتائیں۔")
                else -> listOf("ٹھیک ہے۔","بہت شکریہ۔","میں جلد جواب دوں گا۔")
            }
            2 -> when {
                "مهرباني" in source -> listOf("ڀلي ڪري آيا!", "ڪا ڳالهه ناهي.", "توهان جي مهرباني.")
                "؟" in source || "ڇا" in source -> listOf("ها بلڪل.", "مان ڏسي ٻڌائيندس.", "مهرباني ڪري تفصيل ڏيو.")
                else -> listOf("ٺيڪ آهي.", "مهرباني.", "مان جلد جواب ڏيندس.")
            }
            else -> when {
                "thank" in source -> listOf("You're welcome!", "Happy to help.", "My pleasure.")
                "?" in source -> listOf("Yes, absolutely.", "I'll check and confirm.", "Could you share more details?")
                else -> listOf("Sounds good!", "Thanks for the update.", "I'll get back to you soon.")
            }
        }
    }
    /** Bounded edit distance; returns 3 if more than two edits are required. */
    fun distanceAtMost2(a:String,b:String):Int {
        if(abs(a.length-b.length)>2)return 3
        val previous=IntArray(b.length+1){it}
        val current=IntArray(b.length+1)
        for(i in 1..a.length){
            current[0]=i
            var rowMin=current[0]
            for(j in 1..b.length){
                current[j]=minOf(previous[j]+1,current[j-1]+1,
                    previous[j-1]+if(a[i-1]==b[j-1])0 else 1)
                rowMin=minOf(rowMin,current[j])
            }
            if(rowMin>2)return 3
            for(j in previous.indices)previous[j]=current[j]
        }
        return previous[b.length].coerceAtMost(3)
    }
}
