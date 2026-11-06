package com.aetherkeys.keyboard

/** Rule-based, private text transforms: intentionally not marketed as neural AI. */
object KeyPopWritingTools {
    private val whitespace=Regex("[\\t ]+")
    private val badSpace=Regex("\\s+([,;:.!?])")
    fun rewrite(text:String,mode:String,language:Int):String {
        if(text.isBlank()||text.length>5000)return text
        var value=text.trim().replace(whitespace," ").replace(badSpace,"$1")
        if(language==0){
            value=when(mode){
                "Formal" -> value.replace(Regex("\\bcan't\\b",RegexOption.IGNORE_CASE),"cannot")
                    .replace(Regex("\\bwon't\\b",RegexOption.IGNORE_CASE),"will not")
                    .replace(Regex("\\bgonna\\b",RegexOption.IGNORE_CASE),"going to")
                    .replace(Regex("\\bwanna\\b",RegexOption.IGNORE_CASE),"want to")
                    .let{ if(it.startsWith("hi ",ignoreCase=true))"Hello "+it.drop(3) else it }
                "Polite"->if(value.startsWith("please",ignoreCase=true))value
                    else "Please "+value.replaceFirstChar{it.lowercase()}
                "Concise"->value.replace(Regex("\\b(really|very|actually|basically|just)\\s+",RegexOption.IGNORE_CASE),"")
                    .replace(Regex("\\s+")," ")
                else->value
            }
            if(value.isNotEmpty())value=value.replaceFirstChar{it.uppercase()}
            if(mode=="Punctuate" && value.lastOrNull()?.isLetter()==true)value+="."
        } else {
            if(mode=="Polite" && !value.startsWith(if(language==1)"براہ کرم" else "مهرباني ڪري"))
                value=(if(language==1)"براہ کرم " else "مهرباني ڪري ")+value
            if(mode=="Punctuate" && value.lastOrNull()?.isLetter()==true)value+="۔"
        }
        return value
    }
}
