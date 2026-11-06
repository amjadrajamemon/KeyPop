package com.aetherkeys.keyboard

/** Explicitly limited phrasebook, not general-purpose Sindhi machine translation. */
object KeyPopSindhiPhrasebook {
    private data class Phrase(val en:String,val sd:String,val ur:String)
    private val phrases=listOf(
        Phrase("Hello","سلام","سلام"),Phrase("Good morning","صبح جو سلام","صبح بخیر"),
        Phrase("Good evening","شام جو سلام","شام بخیر"),
        Phrase("How are you?","توهان ڪيئن آهيو؟","آپ کیسے ہیں؟"),
        Phrase("I am fine","مان ٺيڪ آهيان","میں ٹھیک ہوں"),
        Phrase("Thank you","مهرباني","شکریہ"),
        Phrase("Thank you very much","توهان جي تمام گهڻي مهرباني","آپ کا بہت شکریہ"),
        Phrase("Please","مهرباني ڪري","براہ کرم"),
        Phrase("Yes","ها","جی ہاں"),Phrase("No","نه","نہیں"),
        Phrase("Good night","شب بخير","شب بخیر"),
        Phrase("What is your name?","توهان جو نالو ڇا آهي؟","آپ کا نام کیا ہے؟"),
        Phrase("My name is","منهنجو نالو آهي","میرا نام ہے"),
        Phrase("Where are you?","توهان ڪٿي آهيو؟","آپ کہاں ہیں؟"),
        Phrase("Where is the hospital?","اسپتال ڪٿي آهي؟","ہسپتال کہاں ہے؟"),
        Phrase("Please help me","مهرباني ڪري منهنجي مدد ڪريو","براہ کرم میری مدد کریں"),
        Phrase("I need help","مون کي مدد گهرجي","مجھے مدد چاہیے"),
        Phrase("I understand","مان سمجهان ٿو","میں سمجھتا ہوں"),
        Phrase("I do not understand","مان نٿو سمجهان","میں نہیں سمجھتا"),
        Phrase("See you tomorrow","سڀاڻي ملنداسين","کل ملتے ہیں"),
        Phrase("Goodbye","خدا حافظ","خدا حافظ"),
        Phrase("Please call me","مهرباني ڪري مون کي فون ڪريو","براہ کرم مجھے فون کریں"),
        Phrase("Send me a message","مون کي پيغام موڪليو","مجھے پیغام بھیجیں"),
        Phrase("How much is it?","هي ڪيتري جو آهي؟","یہ کتنے کا ہے؟"),
        Phrase("I am coming","مان اچي رهيو آهيان","میں آ رہا ہوں"),
        Phrase("I am at home","مان گهر ۾ آهيان","میں گھر پر ہوں"),
        Phrase("I am busy","مان مصروف آهيان","میں مصروف ہوں"),
        Phrase("It is important","اهو ضروري آهي","یہ ضروری ہے"),
        Phrase("Please wait","مهرباني ڪري ترسو","براہ کرم انتظار کریں"),
        Phrase("Congratulations","مبارڪون","مبارک ہو")
    )
    private fun cleaned(s:String)=s.lowercase()
        .replace(Regex("\\s+([?.!؟۔،])"),"$1")
        .replace(Regex("[\\s.!?؟۔،]+$"),"")
        .trim().replace(Regex("\\s+")," ")
    fun translate(source:String,target:String,text:String):String? {
        if(source==target)return text
        if(source !in setOf("en","sd","ur") || target !in setOf("en","sd","ur"))return null
        // Also supports English ↔ Urdu for bundled common phrases.
        // Unknown sentences are never fabricated as translations.
        val normalized=cleaned(text)
        val phrase=phrases.firstOrNull{
            cleaned(when(source){"sd"->it.sd;"ur"->it.ur;else->it.en})==normalized
        }?:return null
        return when(target){"sd"->phrase.sd;"ur"->phrase.ur;else->phrase.en}
    }
    fun size()=phrases.size
}
