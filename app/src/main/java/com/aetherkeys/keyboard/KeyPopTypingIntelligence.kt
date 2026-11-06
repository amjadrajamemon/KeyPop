package com.aetherkeys.keyboard

import java.util.Locale
import kotlin.math.abs

/**
 * Offline assistance for three languages. Small curated lexicons and conversational
 * context; never accesses the network or analyzes password fields.
 * Not a replacement for neural predictions and language-level grammars.
 */
object KeyPopTypingIntelligence {
    private val english=("about above accept account across action address after again against agree all almost already also always amazing amount another answer anyone anything application approve approved around available aware back bank banking because become before begin being below best better between birthday both build business calendar call camera cannot change check child choose clear click close code color common company complete confirm contact continue copy correct could create current custom customer data date day decision default delete design detail device different direct document done download easy edit email enable enough enter error example excellent family favorite feature feel file find finish first follow forgot form friend from full function future general get give good great group happen happy have hello help here history home hour important improve include information inside install issue just keep keyboard know language latest learn leave letter like link list login look make manage many maybe meeting message minimum minutes modern money month more most move much name near need never next normal note number office only open option order other output page password people personal phone photo please possible prepare present pretty previous privacy project properly quality question quick ready really receive recent recommend remember remove reply report request require result review right save screen search secure select send service setting share show simple some source space special speed start status still storage store style support system team thank thanks their them there these thing think this time today tomorrow tool top touch translate typing update upload user using version video view wait want week welcome what when where which while will window with work world write yes your").split(' ').filter{it.isNotBlank()}.distinct()
    private val urdu=("آپ آپکا آپکی آپکے آپکو آج ابھی اچھا اچھی اچھے اگر اطلاع ان اندر اور ایک بار بہت بہترین براہ بات بعد پہلے پھر تمام تم تھوڑا تیار تیز جواب جانب جان جانچ جاری جگہ جہاں جتنا جب جس جسے جن جی درخواست دیر دن دوبارہ دوست دین دنیا دیکھ دکھائی رہا رہی رہے رکھ رہا سارا ساتھ سلام سال سب سبق سچ سوال سے صبح صرف شکریہ شامل شام شاندار شروع شخص ضروری فیصلہ فی الحال قبل قلم کل کام کرنے کرتا کرتے کرنا کرم کچھ کئی کس کیسے کیوں کیا کہاں کون کوئی کہ کے کی کیونکہ کو گئی گا گی گئے گزر گیا گھر گیا ہے ہیں ہوتا ہونے ہوئی ہوں ہو ہم ہمارا ہماری ہمارے ہمیں یہاں یہ یہی یاد یقینی واقعی وقت والے والا والی وقت محفوظ معلومات مکمل میرا میری میرے میں نہیں نہ نیا نئے نئی نام نوٹ نرم نرم نہایت درست دستاویز دفتر ماہ مہینہ ملاقات مدد محبت مثال مناسب مشورہ مریض مرحلہ مسئلہ ضروریات طلب استعمال رابطہ رپورٹ کام کاغذ ترجمہ تحریر زبان سندھی اردو انگریزی الفاظ قبول بھی کچھ خریداری مکمل کریں کردی دیجئے بھیجیں پیش معلومات فرما دیجیے").split(' ').filter{it.isNotBlank()}.distinct()
    private val sindhi=("اڄ اڃا اوهان توهان توھان تون مان منهنجو منهنجي منهنجا اسان اسانجو اسانجي اسانجا آهي آھن آهن آھيان آهيان آھيو آيو اچي اچو اچي ٿو اچن اڳ بعد پوءِ جيڪڏھن جيڪڏهن پر ۽ يا به بلڪل تمام سلام مهرباني سٺو سٺي سٺا ٺيڪ ٺيڪ آهي ڪيئن ڪڏهن ڪٿي ڇو ڇا ڪير ڪيترو ڪيتري جڏهن جتي گهر ڪم ڏينھن ڏينهن وقت سڀاڻي اڄ شام رات صبح پوءِ دوست خاندان معلومات ڄاڻ وقت درخواست رپوٽ رپورٽ آفيس دفتر ڪاغذ ترجمو ٻولي انگريزي سنڌي اردو مهرباني ڪري ٻڌايو ٻڌائي ٻڌاءِ ڏيکاريو سمجهيو صحيح غلط نالو فون رابطو نمبر پيغام جواب تيار منهنجو اڪائونٽ محفوظ حفاظتي جائزو پوء ڪيو ڪري ڪرڻ لاءِ کان ۾ کي سان جو جي جا هڪ ٻه ٽي چار پنج ڇهه ست اٺ نو ڏهه نئون نئين سٺو سٺي حاصل ڪرڻ چاهيان ٿو ٿي سگهندو مدد ڪندؤ مهرباني مبارڪ خوش ٿي حاضر آهي").split(' ').filter{it.isNotBlank()}.distinct()
    private val follow=mapOf(
        0 to mapOf(
            "thank" to listOf("you"), "good" to listOf("morning","evening","night"),
            "see" to listOf("you"),"please" to listOf("confirm","review","send"),
            "can" to listOf("you","we"),"happy" to listOf("birthday"),
            "looking" to listOf("forward"),"best" to listOf("regards"),
            "kind" to listOf("regards"),"how" to listOf("are","much"),
            "let" to listOf("me"),"i" to listOf("am","will","have")),
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
    fun suggestions(language:Int,fragment:String,previous:String,
                    learned:Set<String> = emptySet(),limit:Int=3):List<String> {
        val prefix=normalized(fragment)
        if(prefix.length !in 1..32)return emptyList()
        val prior=normalized(previous)
        val contextual=follow[language]?.get(prior).orEmpty()
        val vocabulary=(list(language)+learned.map(::normalized)).distinct()
        data class Candidate(val text:String,val score:Int)
        val matches=ArrayList<Candidate>()
        val seen=HashSet<String>()
        for(word in contextual+vocabulary) {
            if(!seen.add(word)||word==prefix)continue
            val starts=word.startsWith(prefix)
            val distance=if(!starts && prefix.length>=4 && abs(word.length-prefix.length)<=1)
                distanceAtMost2(prefix,word) else 3
            if(!starts && distance>1)continue
            val contextualBonus=if(word in contextual)45 else 0
            val learnedBonus=if(word in learned)8 else 0
            val score=(if(starts)70 else 10)+contextualBonus+learnedBonus-
                abs(word.length-prefix.length)
            matches.add(Candidate(word,score))
        }
        return matches.sortedWith(compareByDescending<Candidate>{it.score}
            .thenBy{it.text.length}.thenBy{it.text}).take(limit.coerceIn(1,8)).map{it.text}
    }
    fun correction(language:Int,typed:String,learned:Set<String> = emptySet()):String? {
        val word=normalized(typed)
        if(word.length<4 || word in learned || word in list(language))return null
        return (list(language)+learned).asSequence().filter{
            abs(it.length-word.length)<=1
        }.filter{distanceAtMost2(word,it)==1}.take(8)
            .minByOrNull{abs(it.length-word.length)}
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
