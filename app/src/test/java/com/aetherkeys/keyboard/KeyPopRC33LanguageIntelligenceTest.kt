package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

/** Strict RC33 regressions; NOT a claim of Gboard neural-model parity. */
class KeyPopRC33LanguageIntelligenceTest {
    @Test fun unicodeWordBoundariesPreserveContractionsAndIndicDiacritics() {
        assertEquals("don't",KeyPopUnicodeWords.trailingWord("Why don't"))
        assertEquals("can't",KeyPopUnicodeWords.trailingWord("I can't"))
        assertEquals("سنڌي",KeyPopUnicodeWords.trailingWord("اسان سنڌي"))
        assertEquals("حُسن",KeyPopUnicodeWords.trailingWord("اردو حُسن"))
        assertEquals("can't",KeyPopUnicodeWords.tokens("I can't stop").get(1))
        assertTrue(KeyPopUnicodeWords.isToken("حُسن"))
        assertTrue(KeyPopUnicodeWords.isToken("you're"))
        assertEquals("",KeyPopUnicodeWords.trailingWord("hello "))
        assertEquals("",KeyPopUnicodeWords.trailingWord("user@example.com:"))
    }
    @Test fun rankedPredictionsPrioritizeRealCompletionsWithoutOverwritingTypedText() {
        val exact=KeyPopUnifiedPrediction.rank(0,"likew","I likew ",
            emptyList(),listOf("liked","likes","likewise"),null,limit=3)
        assertEquals("likewise",exact.first())
        val context=KeyPopUnifiedPrediction.rank(0,"worri","Why are you worri",
            emptyList(),listOf("worry","worried","worrying"),null,limit=5)
        assertEquals("worried",context.first())
        assertTrue("worrying" in context)
        assertFalse("worry" in context)
        val spelling=KeyPopUnifiedPrediction.rank(0,"tge","I typed tge",
            emptyList(),listOf("tie","the"),"the",limit=3)
        assertEquals("the",spelling.first())
    }
    @Test fun personalPreferencesAndRejectionsAreAppliedToOneCandidateRanking() {
        val personalized=KeyPopUnifiedPrediction.rank(0,"","Please ",
            listOf("confirm"),listOf("check","send"),limit=3)
        assertEquals("confirm",personalized.first())
        val rejected=KeyPopUnifiedPrediction.rank(0,"tge","I typed tge",
            emptyList(),listOf("tie","the"),"the",
            rejected=mapOf("the" to 4),limit=3)
        assertEquals("tie",rejected.first())
    }
    @Test fun singletonFuzzyCandidateDoesNotAutomaticallyCountAsConfident() {
        assertNull(KeyPopFuzzyRanker.automatic(listOf(
            KeyPopFuzzyRanker.Match("seldom",1,320,10))))
        assertNull(KeyPopFuzzyRanker.automatic(listOf(
            KeyPopFuzzyRanker.Match("seldom",2,300,10))))
        assertNull(KeyPopFuzzyRanker.automatic(listOf(
            KeyPopFuzzyRanker.Match("worker",1,350,35))))
    }
    @Test fun coldStartNextWordContextWorksInAllThreeLanguages() {
        assertTrue(KeyPopContextPhraseModel.suggest(0,"please confirm the")
            .contains("details"))
        assertTrue(KeyPopContextPhraseModel.suggest(0,"thank you for")
            .contains("your"))
        assertTrue(KeyPopContextPhraseModel.suggest(1,"براہ کرم")
            .contains("معلومات"))
        assertTrue(KeyPopContextPhraseModel.suggest(2,"مهرباني ڪري")
            .isNotEmpty())
    }
    @Test fun nativeLanguageDictionaryHasMeaningfulAdditionalVocabulary() {
        assertTrue("معلومات" in KeyPopRegionalWordBank.words(1))
        assertTrue("منظوری" in KeyPopRegionalWordBank.words(1))
        assertTrue("مهرباني" in KeyPopRegionalWordBank.words(2))
        assertTrue("معلومات" in KeyPopRegionalWordBank.words(2))
    }
    @Test fun grammarDoesNotSilentlyApplyAmbiguousArticleAndDuplicateFixes() {
        val apple=KeyPopGrammarRules.suggest("a apple",0)
        assertEquals("an apple",apple?.replacement)
        assertFalse(apple?.automatic?:true)
        val repeated=KeyPopGrammarRules.suggest("very very",0)
        assertFalse(repeated?.automatic?:true)
        val agreement=KeyPopGrammarRules.suggest("she are",0)
        assertEquals("she is",agreement?.replacement)
        assertTrue(agreement?.automatic?:false)
    }
    @Test fun noSuggestionFlagsOrSensitiveInputTypesCannotEnterPredictionPipeline() {
        val regular=InputType.TYPE_CLASS_TEXT
        assertTrue(KeyPopTypingRules.textAllowsSuggestions(regular))
        assertFalse(KeyPopTypingRules.textAllowsSuggestions(
            regular or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(KeyPopTypingRules.textAllowsLearning(
            regular or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(KeyPopTypingRules.textAllowsSuggestions(
            regular or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(KeyPopTypingRules.textAllowsLearning(
            regular or InputType.TYPE_TEXT_VARIATION_URI))
    }
}
