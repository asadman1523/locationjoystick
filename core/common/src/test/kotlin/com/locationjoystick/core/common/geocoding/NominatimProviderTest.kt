package com.locationjoystick.core.common.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimProviderTest {
    @Test
    fun `search maps string lat lon and display name`() {
        val r = parseSearchResponse("""[{"lat":"48.85","lon":"2.35","display_name":"Paris"}]""")
        assertEquals(listOf(GeocodeResult(48.85, 2.35, "Paris")), r)
    }

    @Test
    fun `search skips malformed entry and keeps valid ones`() {
        val r = parseSearchResponse("""[{"lat":"x"},{"lat":"1","lon":"2","display_name":"A"}]""")
        assertEquals(listOf(GeocodeResult(1.0, 2.0, "A")), r)
    }

    @Test
    fun `search caps at five and empty array gives empty list`() {
        val item = """{"lat":"1","lon":"2","display_name":"A"}"""
        assertEquals(5, parseSearchResponse("[${List(8) { item }.joinToString(",")}]").size)
        assertEquals(emptyList<GeocodeResult>(), parseSearchResponse("[]"))
    }

    @Test
    fun `reverse locality falls back city town village municipality`() {
        assertEquals("C", parseReverseResponse("""{"address":{"city":"C","town":"T"}}""")?.locality)
        assertEquals("T", parseReverseResponse("""{"address":{"town":"T","village":"V"}}""")?.locality)
        assertEquals("V", parseReverseResponse("""{"address":{"village":"V","municipality":"M"}}""")?.locality)
        assertEquals("M", parseReverseResponse("""{"address":{"municipality":"M"}}""")?.locality)
    }

    @Test
    fun `reverse maps region and country and nulls blanks`() {
        val r = parseReverseResponse("""{"address":{"state":"S","country":"X","city":""}}""")
        assertEquals(ReverseGeocodeResult(null, "S", "X"), r)
    }

    @Test
    fun `reverse without address is null`() {
        assertNull(parseReverseResponse("""{"error":"Unable to geocode"}"""))
    }
}
