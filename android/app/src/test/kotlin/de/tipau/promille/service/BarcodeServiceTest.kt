package de.tipau.promille.service

import de.tipau.promille.bac.DrinkCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeServiceTest {

    @Test
    fun testParseVolumeML() {
        assertEquals(330.0, BarcodeService.parseVolumeML("330 ml") ?: 0.0, 0.001)
        assertEquals(500.0, BarcodeService.parseVolumeML("0.5 l") ?: 0.0, 0.001)
        assertEquals(500.0, BarcodeService.parseVolumeML("0,5 Liter") ?: 0.0, 0.001)
        assertEquals(40.0, BarcodeService.parseVolumeML("4 cl") ?: 0.0, 0.001)
        assertEquals(330.0, BarcodeService.parseVolumeML("6 x 330 ml") ?: 0.0, 0.001)
        assertEquals(500.0, BarcodeService.parseVolumeML("Dose 0.5l") ?: 0.0, 0.001)
        assertNull(BarcodeService.parseVolumeML("Keine Angabe"))
    }

    @Test
    fun testSanitizers() {
        assertEquals(0.0, BarcodeService.sanitizedABV(-5.0), 0.001)
        assertEquals(80.0, BarcodeService.sanitizedABV(95.0), 0.001)
        assertEquals(5.5, BarcodeService.sanitizedABV(5.5), 0.001)

        assertEquals(5.0, BarcodeService.sanitizedVolumeML(2.0), 0.001)
        assertEquals(3000.0, BarcodeService.sanitizedVolumeML(5000.0), 0.001)
        assertEquals(500.0, BarcodeService.sanitizedVolumeML(500.0), 0.001)

        // Non-alcoholic category with alcohol becomes MIXED
        assertEquals(DrinkCategory.MIXED, BarcodeService.sanitizedCategory(DrinkCategory.SOFT_DRINK, 4.5))
        assertEquals(DrinkCategory.SOFT_DRINK, BarcodeService.sanitizedCategory(DrinkCategory.SOFT_DRINK, 0.0))
        assertEquals(DrinkCategory.BEER, BarcodeService.sanitizedCategory(DrinkCategory.BEER, 5.0))
    }
}
