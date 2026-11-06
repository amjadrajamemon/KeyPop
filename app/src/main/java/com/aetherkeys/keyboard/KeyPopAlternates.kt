package com.aetherkeys.keyboard

/**
 * Long-press alternates for English, Urdu, Sindhi, symbols and numerals.
 * Available independently of optional numeric hint labels and glide.
 */
object KeyPopAlternates {
    private val en=mapOf(
        "q" to listOf("1"),"w" to listOf("2"),"e" to listOf("3","é","è","ê","ë","ē"),
        "r" to listOf("4"),"t" to listOf("5"),"y" to listOf("6"),
        "u" to listOf("7","ú","ù","û","ü"),"i" to listOf("8","í","ì","î","ï"),
        "o" to listOf("9","ó","ò","ô","ö","õ"),"p" to listOf("0"),
        "a" to listOf("@","á","à","â","ä","ã","å","æ"),
        "s" to listOf("#","ß","ś","š"),"d" to listOf("$","ð"),
        "f" to listOf("%"),"g" to listOf("&"),"h" to listOf("-"),
        "j" to listOf("+"),"k" to listOf("("),"l" to listOf(")"),
        "z" to listOf("*","ž"),"x" to listOf("×"),
        "c" to listOf("ç","ć","č"),"v" to listOf("'"),"b" to listOf("\""),
        "n" to listOf("ñ","ń"),"m" to listOf("?"),
        "." to listOf("…","!","?",":",";","/","@"),
        "," to listOf(";","،","؟"),"?" to listOf("¿","؟"),
        "!" to listOf("¡"),"-" to listOf("–","—","−"),
        "'" to listOf("’","‘"),"\"" to listOf("“","”","«","»"),
        "/" to listOf("\\","÷"),"@" to listOf("©","®","™")
    )
    private val script=mapOf(
        "ا" to listOf("آ","أ","إ","ٱ"),
        "ب" to listOf("پ","ٻ","ڀ"),
        "ت" to listOf("ٹ","ث","ٿ","ٽ","ٺ"),
        "د" to listOf("ڈ","ذ","ڌ","ڏ","ڊ","ڍ"),
        "ر" to listOf("ڑ","ز","ژ","ڙ"),
        "ن" to listOf("ں","ڻ"),
        "ک" to listOf("خ","ڪ"),
        "ڪ" to listOf("ک","ڳ"),
        "ج" to listOf("چ","ڄ","جھ","ڃ"),
        "چ" to listOf("ڇ"),
        "ف" to listOf("ڦ"),
        "گ" to listOf("غ","ڳ","گھ"),
        "س" to listOf("ص","ث"),
        "ح" to listOf("ہ","ھ","ۂ"),
        "ہ" to listOf("ھ","ۃ"),
        "ه" to listOf("ھ"),
        "ی" to listOf("ئ","ے","ۓ"),
        "ي" to listOf("ئ","ى"),
        "و" to listOf("ؤ","ۆ"),
        "ع" to listOf("ء"),
        "ے" to listOf("ۓ","ی"),
        "ء" to listOf("ئ","ؤ"),
        "." to listOf("۔","؟","،","؛"),
        "," to listOf("،","؛","؟")
    )
    private val nums=mapOf(
        "1" to listOf("¹","½","⅓","¼","⅛"),
        "2" to listOf("²","⅔"),
        "3" to listOf("³","¾","⅜"),
        "4" to listOf("⁴"),"5" to listOf("⁵","⅝"),
        "6" to listOf("⁶"),"7" to listOf("⁷","⅞"),
        "8" to listOf("⁸"),"9" to listOf("⁹"),"0" to listOf("⁰","°")
    )
    fun forKey(label:String,language:Int):List<String> {
        if(label in nums)return nums.getValue(label)
        if(label.length>2)return emptyList()
        return if(language==1 || language==2)
            (script[label].orEmpty()+en[label].orEmpty()).distinct()
        else en[label].orEmpty()
    }
    fun hint(label:String,language:Int):String? =
        forKey(label,language).firstOrNull()
}
