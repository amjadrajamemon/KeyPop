package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopGboardGeometryTest {
    @Test fun materialFooterMatchesReferenceProportions() {
        val footer=KeyboardPresetCatalog.foot(KeyboardPresetCatalog.MATERIAL,false)
        assertEquals(listOf("?123","☺,","🌐","SPACE",".","↵"),footer)
        val widths=footer.map {KeyboardPresetCatalog.keyWeight(KeyboardPresetCatalog.MATERIAL,it)}
        val expected=listOf(94f,60f,60f,266f,60f,95f)
        val actual=widths.map {it/widths.sum()}
        val observed=expected.map {it/expected.sum()}
        actual.zip(observed).forEach { (a,b)-> assertEquals(b,a,0.012f) }
    }
}
