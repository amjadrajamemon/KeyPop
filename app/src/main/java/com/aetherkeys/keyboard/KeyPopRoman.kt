package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Offline best-effort Roman Urdu / Roman Sindhi conversion.
 * Frequent words use explicit mappings; unknown words use phonetic rules.
 * This is not a high-accuracy neural transliteration model.
 */
object KeyPopRoman {
    private val urdu = mapOf(
        "salam" to "سلام","salaam" to "سلام","assalam" to "السلام",
        "alaikum" to "علیکم","shukriya" to "شکریہ","mehrbani" to "مہربانی",
        "aap" to "آپ","ap" to "آپ","tum" to "تم","hum" to "ہم",
        "main" to "میں","mein" to "میں","mai" to "میں","mujhe" to "مجھے",
        "mera" to "میرا","meri" to "میری","mere" to "میرے",
        "kaise" to "کیسے","kaisay" to "کیسے","kaisa" to "کیسا",
        "hai" to "ہے","hain" to "ہیں","ho" to "ہو","hoon" to "ہوں",
        "hun" to "ہوں","tha" to "تھا","thi" to "تھی","the" to "تھے",
        "haan" to "ہاں","han" to "ہاں","nahi" to "نہیں",
        "nahin" to "نہیں","ji" to "جی","bilkul" to "بالکل",
        "kya" to "کیا","kiya" to "کیا","kab" to "کب",
        "kahan" to "کہاں","kyun" to "کیوں",
        "kaun" to "کون","kyon" to "کیوں","khuda" to "خدا",
        "hafiz" to "حافظ","kal" to "کل","aaj" to "آج",
        "abhi" to "ابھی","subah" to "صبح","shaam" to "شام",
        "raat" to "رات","achha" to "اچھا","acha" to "اچھا",
        "theek" to "ٹھیک","thik" to "ٹھیک","kaam" to "کام",
        "ghar" to "گھر","dost" to "دوست","khushi" to "خوشی",
        "pyaar" to "پیار","pyar" to "پیار","kar" to "کر",
        "karo" to "کرو","karen" to "کریں","raha" to "رہا",
        "rahi" to "رہی","gaya" to "گیا","gaye" to "گئے",
        "aur" to "اور","lekin" to "لیکن")
    private val sindhi=mapOf(
        "salam" to "سلام","salaam" to "سلام","asan" to "اسان",
        "tawhan" to "توهان","tuhan" to "توهان","tawhaan" to "توهان",
        "man" to "مان","maa" to "مان","ahe" to "آهي",
        "aahe" to "آهي","ahyo" to "آهيو","ahyan" to "آهيان",
        "ahin" to "آهن","haan" to "ها","han" to "ها",
        "na" to "نه","naahi" to "نه","cha" to "ڇا",
        "chha" to "ڇا","sha" to "ڇا","kayen" to "ڪيئن",
        "kyan" to "ڪيئن","kithay" to "ڪٿي","kathe" to "ڪٿي",
        "sabhaane" to "سڀاڻي","aaj" to "اڄ","hun" to "هن",
        "hane" to "هاڻي","khay" to "کي","hik" to "هڪ",
        "jo" to "جو","ji" to "جي","meherbani" to "مهرباني",
        "mehrbani" to "مهرباني","shukriya" to "مهرباني",
        "sutho" to "سٺو","suthi" to "سٺي","theek" to "ٺيڪ",
        "thik" to "ٺيڪ","ghar" to "گهر","khushi" to "خوشي",
        "dost" to "دوست","raat" to "رات","sabah" to "صبح",
        "piyar" to "پيار","pyar" to "پيار")
    private val base=listOf(
        "sh" to "ش","kh" to "خ","gh" to "غ","ch" to "چ",
        "ph" to "پھ","th" to "تھ","dh" to "دھ","bh" to "بھ",
        "zh" to "ژ","aa" to "ا","ee" to "ی","oo" to "و",
        "ai" to "ے","au" to "او","rr" to "ڑ","tt" to "ٹ",
        "a" to "ا","b" to "ب","c" to "ک","d" to "د","e" to "ے",
        "f" to "ف","g" to "گ","h" to "ہ","i" to "ی",
        "j" to "ج","k" to "ک","l" to "ل","m" to "م",
        "n" to "ن","o" to "و","p" to "پ","q" to "ق",
        "r" to "ر","s" to "س","t" to "ت","u" to "و",
        "v" to "و","w" to "و","x" to "کس","y" to "ی","z" to "ز")
    private val sindhiRules=listOf(
        "chh" to "ڇ","jj" to "ڄ","dd" to "ڊ","dh" to "ڌ",
        "th" to "ٿ","rr" to "ڙ","gh" to "ڳ","kh" to "ک",
        "tt" to "ٽ","bh" to "ڀ","ph" to "ڦ",
        "b" to "ب","j" to "ج","k" to "ڪ","g" to "گ",
        "h" to "ه","n" to "ن")+base.filterNot{it.first in setOf("th","rr","gh","kh","tt","bh","ph","b","j","k","g","h","n")}

    fun convert(language:Int,word:String):String {
        if(language !in 1..2 || word.length !in 1..48 ||
            word.any{it !in 'A'..'Z' && it !in 'a'..'z'})return word
        val normalized=word.lowercase(Locale.ROOT)
        val fixed=(if(language==1)urdu else sindhi)[normalized]
        if(fixed!=null)return fixed
        val rules=if(language==1)base else sindhiRules
        val result=StringBuilder()
        var index=0
        while(index<normalized.length) {
            val matched=rules.firstOrNull{normalized.startsWith(it.first,index)}
            if(matched==null)return word
            result.append(matched.second)
            index+=matched.first.length
        }
        return result.toString()
    }
}
