package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopRC21JoinedWordsTest {
    private val known=setOf("to","predict","the","keyboard","into",
        "night","tonight","together","in","report","my","phone",
        "and","draft","for","review","with","account","can","write")
    @Test fun missingSpaceIsDetectedAndKnownWordsRemainWhole() {
        val isKnown:(String)->Boolean={it in known}
        assertEquals("to predict",KeyPopWordJoinRules.split("topredict",isKnown))
        assertEquals("the keyboard",KeyPopWordJoinRules.split("thekeyboard",isKnown))
        assertEquals("in report",KeyPopWordJoinRules.split("inreport",isKnown))
        assertNull(KeyPopWordJoinRules.split("together",isKnown))
        assertNull(KeyPopWordJoinRules.split("tonight",isKnown))
        assertNull(KeyPopWordJoinRules.split("myphone123",isKnown))
        assertNull(KeyPopWordJoinRules.split("a",isKnown))
    }
    @Test fun unknownAndAmbiguousJoinedWordsAreNotForced() {
        val known=setOf("to","the","and","predict","keyboard","anything")
        assertNull(KeyPopWordJoinRules.split("randomnonsense"){it in known})
        assertNull(KeyPopWordJoinRules.split("topredict"){it=="topredict"||it in known})
    }
}
