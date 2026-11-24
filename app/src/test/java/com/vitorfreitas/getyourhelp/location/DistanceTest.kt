package com.vitorfreitas.getyourhelp.location

import com.vitorfreitas.getyourhelp.data.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

class DistanceTest {
    private val dublin = Coordinates(53.3498, -6.2603)
    private val cork = Coordinates(51.8985, -8.4756)

    @Test
    fun distanceBetweenDublinAndCorkIsAbout220Km() {
        val km = distanceKm(dublin, cork)
        assertTrue("was $km", km in 215.0..225.0)
    }

    @Test
    fun distanceIsTheSameInBothDirectionsAndZeroToItself() {
        assertEquals(distanceKm(dublin, cork), distanceKm(cork, dublin), 1e-9)
        assertEquals(0.0, distanceKm(dublin, dublin), 1e-9)
    }

    @Test
    fun unknownPositionGivesNoDistance() {
        assertNull(distanceKmOrNull(null, dublin))
        assertNull(distanceKmOrNull(dublin, null))
    }

    @Test
    fun distancesAreRoundedForDisplay() {
        val uk = Locale.UK
        assertEquals("Under 100 m", formatDistance(0.04, uk))
        assertEquals("300 m", formatDistance(0.34, uk))
        assertEquals("1.0 km", formatDistance(0.97, uk))
        assertEquals("1.2 km", formatDistance(1.234, uk))
        assertEquals("10 km", formatDistance(9.97, uk))
        assertEquals("12 km", formatDistance(12.4, uk))
    }

    @Test
    fun savedPositionIsRoundedToAbout100Metres() {
        val approximate = Coordinates(53.349805, -6.260310).approximate()
        assertEquals(53.350, approximate.latitude, 1e-9)
        assertEquals(-6.260, approximate.longitude, 1e-9)
        // Rounding moves the point by less than 100 m
        assertTrue(distanceKm(Coordinates(53.349805, -6.260310), approximate) < 0.1)
    }

    @Test
    fun areaNameUsesNeighbourhoodAndTown() {
        assertEquals("Ranelagh, Dublin", areaLabel("Ranelagh", "Dublin", "Dublin City", "County Dublin"))
        assertEquals("Mountain View, California", areaLabel(null, "Mountain View", "Santa Clara County", "California"))
        assertEquals("Cork", areaLabel(" ", null, "Cork", "Cork"))
        assertNull(areaLabel(null, null, null, null))
    }
}
