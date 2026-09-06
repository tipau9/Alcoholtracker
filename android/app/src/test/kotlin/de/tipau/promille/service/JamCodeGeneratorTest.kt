package de.tipau.promille.service

import org.junit.Assert.assertEquals
import org.junit.Test

class JamCodeGeneratorTest {

    @Test
    fun generate_isAlways6Chars() {
        repeat(20) {
            assertEquals(6, JamCodeGenerator.generate().length)
        }
    }

    @Test
    fun sanitize_filtersAndTruncatesTo6() {
        assertEquals("ABCDEF", JamCodeGenerator.sanitize("abc-defgh"))
        assertEquals("AB12CD", JamCodeGenerator.sanitize("ab 12cd"))
        assertEquals("", JamCodeGenerator.sanitize("---"))
    }
}
