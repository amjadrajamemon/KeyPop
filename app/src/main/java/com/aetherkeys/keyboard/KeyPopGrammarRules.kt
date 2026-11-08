package com.aetherkeys.keyboard

/**
 * Explicit, deterministic offline corrections. This is a deliberately narrow
 * rule set; it never claims to be a comprehensive grammar model.
 */
data class KeyPopGrammarFix(val label:String,val remove:Int,val replacement:String)

object KeyPopGrammarRules {
    private val rules=listOf(
        Regex("(?i)\\bi\\s+is$") to "I am",
        Regex("(?i)\\bi\\s+are$") to "I am",
        Regex("(?i)\\bhe\\s+have$") to "he has",
        Regex("(?i)\\bshe\\s+have$") to "she has",
        Regex("(?i)\\bit\\s+have$") to "it has",
        Regex("(?i)\\bthey\\s+is$") to "they are",
        Regex("(?i)\\bwe\\s+is$") to "we are",
        Regex("(?i)\\byou\\s+is$") to "you are",
        Regex("(?i)\\bi\\s+has$") to "I have",
        Regex("(?i)\\bwe\\s+has$") to "we have",
        Regex("(?i)\\bthey\\s+has$") to "they have",
        Regex("(?i)\\byou\\s+has$") to "you have",
        Regex("(?i)\\bhe\\s+don't$") to "he doesn't",
        Regex("(?i)\\bshe\\s+don't$") to "she doesn't",
        Regex("(?i)\\bit\\s+don't$") to "it doesn't",
        Regex("(?i)\\byou\\s+was$") to "you were",
        Regex("(?i)\\bwe\\s+was$") to "we were",
        Regex("(?i)\\bthey\\s+was$") to "they were",
        Regex("(?i)\\bthere\\s+is\\s+many$") to "there are many",
        Regex("(?i)\\bdont$") to "don't",
        Regex("(?i)\\bdoesnt$") to "doesn't",
        Regex("(?i)\\bcant$") to "can't",
        Regex("(?i)\\bteh$") to "the",
        Regex("(?i)\\badn$") to "and",
        Regex("(?i)\\brecieve$") to "receive",
        Regex("(?i)\\bdefinately$") to "definitely",
        Regex("(?i)\\bseperate$") to "separate"
    )
    /** A fix only applies at the cursor and only after checking the text again. */
    fun suggest(before:String,language:Int):KeyPopGrammarFix? {
        if(language!=0 || before.isBlank())return null
        val text=before.takeLast(150)
        for((pattern,replacement) in rules) {
            val match=pattern.find(text) ?:continue
            if(match.range.last != text.lastIndex)continue
            val adjusted=if(match.value.firstOrNull()?.isUpperCase()==true)
                replacement.replaceFirstChar{it.uppercase()} else replacement
            return KeyPopGrammarFix("Fix: "+adjusted,match.value.length,adjusted)
        }
        val pair=Regex("(?i)\\b([a-z]{2,})\\s+\\1$").find(text)
        if(pair!=null && pair.range.last==text.lastIndex){
            val single=pair.groupValues[1]
            return KeyPopGrammarFix("Remove repeated "+single,
                pair.value.length,single)
        }
        return null
    }
}
