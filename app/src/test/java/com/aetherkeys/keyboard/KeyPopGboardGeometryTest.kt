package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopGboardGeometryTest {
    @Test fun materialFooterMatchesReferenceProportions() {
        val footer=KeyboardPresetCatalog.foot(KeyboardPresetCatalog.MATERIAL,false)
        assertEquals(listOf("?123",",","☺","🌐","SPACE",".","↵"),footer)
        val widths=footer.map {KeyboardPresetCatalog.keyWeight(KeyboardPresetCatalog.MATERIAL,it)}
        // Updated RC26 ergonomic default intentionally makes punctuation
        // and Enter roomier than the earlier compact reference proportions.
        val expected=listOf(1.57f,1.85f,0.85f,1f,4.43f,1.85f,2.35f)
        val actual=widths.map {it/widths.sum()}
        val observed=expected.map {it/expected.sum()}
        actual.zip(observed).forEach { (a,b)-> assertEquals(b,a,0.012f) }
    }
}
