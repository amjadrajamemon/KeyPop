package com.aetherkeys.keyboard

/**
 * Compact on-device n-gram backoff for common contexts. Static examples are
 * only a cold-start prior: private phrases learned by the user are combined
 * by the unified ranker and are never uploaded or bundled back into the app.
 */
object KeyPopContextPhraseModel {
    private val examples=mapOf(
        0 to listOf(
            "please check the latest version","please confirm the details",
            "please share the final report","please review the document",
            "please find the attached document","please provide more information",
            "please let me know","please let us know",
            "thank you for your help","thank you very much",
            "thanks for the update","thanks for your support",
            "i am working on this","i am going to check",
            "i am writing to confirm","i am happy to help",
            "i will check and confirm","i will send the report",
            "i will share the details","i will get back to you",
            "we are looking into this","we are working on it",
            "we have received your request","we have reviewed the report",
            "we will share an update","we need to confirm",
            "could you please check","could you please share",
            "can you help me","can you please confirm",
            "would you like to proceed","would you please review",
            "the attached document contains","the report is ready",
            "the latest version is available","the issue has been resolved",
            "the results are available","the application is under review",
            "this is a good idea","this is the latest update",
            "the next step is to","the final version is ready",
            "it is important to note","it is working properly",
            "there are several options","there is an issue",
            "the keyboard should suggest","the prediction should improve",
            "the system is running","the project is complete",
            "how are you doing","how can i help",
            "how does this work","how much does it cost",
            "what do you mean","what is the issue",
            "where can i find","when will it be ready",
            "i need to check","i need to improve",
            "i want to know","i want to write",
            "there is no need to","this should work correctly",
            "the corrections are accurate","it should not change",
            "it is not working","the document has been received",
            "the report will be submitted","the budget has been approved",
            "the payment is pending","the details have been reviewed",
            "i have reviewed the document","we are pleased to inform",
            "please proceed with the changes","please continue with the project",
            "we appreciate your cooperation","looking forward to your reply",
            "please let me know if","please contact us for details",
            "kind regards and best wishes","best regards and thanks",
            "a better way to do","this may be considered further"
        ),
        1 to listOf(
            "براہ کرم معلومات فراہم کریں","براہ کرم رپورٹ ارسال کریں",
            "براہ کرم فائل ملاحظہ کریں","آپ کا بہت شکریہ",
            "ہم نے درخواست وصول کی","ہم نے رپورٹ تیار کی",
            "یہ مسئلہ حل ہو گیا","یہ معلومات دستیاب ہیں",
            "براہ کرم تصدیق کریں","آپ کی درخواست منظور ہوئی",
            "اس معاملے پر غور کیا جائے","یہ کام مکمل ہو گیا",
            "ہم آپ کو اطلاع دیں گے","رپورٹ کے مطابق فیصلہ کیا گیا",
            "درخواست مزید کارروائی کے لیے بھیجی گئی"
        ),
        2 to listOf(
            "مهرباني ڪري معلومات ڏيو","مهرباني ڪري جواب ڏيو",
            "مهرباني ڪري رپورٽ موڪليو","توهان جي مهرباني",
            "اسان کي خبر ڏيو","اسان جي آفيس ۾",
            "اڄ جو ڪم مڪمل ٿيو","هن مسئلي جو حل",
            "اهي معلومات موجود آهن","مان توهان جي مدد ڪندس",
            "مهرباني ڪري تصديق ڪريو","سڀاڻي صبح جو وقت",
            "هن ڪم بابت ڄاڻ ڏيو"
        )
    )
    private data class Seen(val word:String,val count:Int)
    private val bank:Map<Int,Map<String,List<String>>> by lazy {
        examples.mapValues { (_,sentences)->
            val transitions=HashMap<String,MutableMap<String,Int>>()
            for(sentence in sentences) {
                val words=KeyPopUnicodeWords.tokens(sentence,60)
                for(i in 1 until words.size) {
                    for(depth in 1..minOf(i,3)) {
                        val key=words.subList(i-depth,i).joinToString("|")
                        val candidates=transitions.getOrPut(key){LinkedHashMap()}
                        candidates[words[i]]=(candidates[words[i]]?:0)+1
                    }
                }
            }
            transitions.mapValues { (_,weighted)->
                weighted.entries.sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}
                    .thenBy{it.key}).map{it.key}
            }
        }
    }
    fun suggest(language:Int,before:String,limit:Int=6):List<String> {
        val history=KeyPopUnicodeWords.tokens(before.takeLast(200),16).takeLast(3)
        if(history.isEmpty())return emptyList()
        val candidates=bank[language].orEmpty()
        val output=LinkedHashSet<String>()
        for(depth in minOf(history.size,3) downTo 1) {
            val key=history.takeLast(depth).joinToString("|")
            for(candidate in candidates[key].orEmpty())output.add(candidate)
            if(output.size>=limit)break
        }
        return output.take(limit)
    }
}
