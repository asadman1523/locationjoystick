package com.locationjoystick.core.common.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonProviderTest {
    private fun feature(
        props: String,
        geometry: String = """"geometry":{"coordinates":[2.0,1.0]}""",
    ) = """{"type":"Feature",$geometry,"properties":$props}"""

    private fun collection(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test
    fun `chinese query maps lon lat order and dedupes state equal to city`() {
        val body =
            collection(
                feature(
                    """{"name":"天安门","street":"西长安街","housenumber":"1","city":"北京市","state":"北京市","country":"中国"}""",
                    """"geometry":{"coordinates":[116.391263,39.907359]}""",
                ),
            )
        assertEquals(
            listOf(GeocodeResult(39.907359, 116.391263, "天安门, 西长安街 1, 北京市, 中国")),
            parsePhotonSearchResponse(body),
        )
    }

    @Test
    fun `nameless feature uses remaining parts and empty one is skipped`() {
        val body = collection(feature("""{"street":"Main","city":"C","country":"X"}"""), feature("""{}"""))
        assertEquals(listOf(GeocodeResult(1.0, 2.0, "Main, C, X")), parsePhotonSearchResponse(body))
    }

    @Test
    fun `malformed feature skipped, empty gives empty list, capped at five`() {
        val good = feature("""{"name":"A"}""")
        assertEquals(1, parsePhotonSearchResponse(collection("""{"properties":{"name":"B"}}""", good)).size)
        assertEquals(emptyList<GeocodeResult>(), parsePhotonSearchResponse(collection()))
        assertEquals(5, parsePhotonSearchResponse(collection(*Array(8) { good })).size)
    }

    @Test
    fun `reverse locality falls back city locality district and maps region country`() {
        assertEquals("C", parsePhotonReverseResponse(collection(feature("""{"city":"C","locality":"L"}""")))?.locality)
        assertEquals("L", parsePhotonReverseResponse(collection(feature("""{"locality":"L","district":"D"}""")))?.locality)
        assertEquals("D", parsePhotonReverseResponse(collection(feature("""{"district":"D"}""")))?.locality)
        assertEquals(
            ReverseGeocodeResult(null, "S", "X"),
            parsePhotonReverseResponse(collection(feature("""{"state":"S","country":"X","city":""}"""))),
        )
    }

    @Test
    fun `reverse with no features is null`() {
        assertNull(parsePhotonReverseResponse(collection()))
    }
}
