package com.aetherkeys.keyboard
import org.junit.Assert.*
import org.junit.Test
class KeyPopRomanTest {
    @Test fun commonUrduWordsConvert() {
        assertEquals("سلام",KeyPopRoman.convert(1,"salam"))
        assertEquals("آپ",KeyPopRoman.convert(1,"aap"))
        assertEquals("شکریہ",KeyPopRoman.convert(1,"shukriya"))
    }
    @Test fun commonSindhiWordsConvert() {
        assertEquals("توهان",KeyPopRoman.convert(2,"tawhan"))
        assertEquals("آهي",KeyPopRoman.convert(2,"ahe"))
        assertEquals("ڇا",KeyPopRoman.convert(2,"chha"))
    }
    @Test fun ignoredForEnglishAndNonLatinWords() {
        assertEquals("hello",KeyPopRoman.convert(0,"hello"))
        assertEquals("سنڌي",KeyPopRoman.convert(2,"سنڌي"))
        assertEquals("hello123",KeyPopRoman.convert(1,"hello123"))
    }
}
