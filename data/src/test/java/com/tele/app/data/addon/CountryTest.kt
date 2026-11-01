package com.tele.app.data.addon

import com.tele.app.data.addon.model.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryTest {

    @Test
    fun `coincidencia exacta y por lista con paises en ingles`() {
        assertTrue(Country.SPAIN.matches("Spain"))
        assertTrue(Country.SPAIN.matches("Mexico, Spain"))
        assertTrue(Country.SPAIN.matches("Chile, Spain, United Kingdom, France"))
        assertFalse(Country.SPAIN.matches("United States"))
    }

    @Test
    fun `coincidencia con nombres en espanol`() {
        assertTrue(Country.CHILE.matches("Chile"))
        assertTrue(Country.USA.matches("Estados Unidos"))
        assertTrue(Country.SOUTH_KOREA.matches("Corea del Sur"))
        assertTrue(Country.SOUTH_KOREA.matches("South Korea"))
    }

    @Test
    fun `traduccion de etiquetas al espanol`() {
        assertEquals("España", Country.displayLabel("Spain"))
        assertEquals("Estados Unidos", Country.displayLabel("United States"))
        assertEquals("México, España", Country.displayLabel("Mexico, Spain"))
        assertEquals("Chile, España, Reino Unido, Francia", Country.displayLabel("Chile, Spain, United Kingdom, France"))
        assertEquals("Corea del Sur", Country.displayLabel("South Korea"))
    }
}
