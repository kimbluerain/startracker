package local.skytrails.app.location

import local.skytrails.app.core.Site
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LocationStateTest {
    private fun input(lat: String = "30.27", lon: String = "120.15", zone: String = "Asia/Shanghai") = ManualLocationInput("杭州", lat, lon, zone)

    @Test fun missingLocationAndDeniedPermissionNeverInventCoordinates() {
        val repo = LocationRepository()
        assertNull(repo.state.value.selected)
        repo.beginDeviceRefresh()
        repo.fail("Permission denied", true)
        assertEquals(LocationStatus.PERMISSION_DENIED, repo.state.value.status)
        assertNull(repo.state.value.selected)
        assertTrue(repo.setManual(input()).isSuccess)
        assertEquals(30.27, repo.state.value.selected!!.site.latitude, 0.0)
    }

    @Test fun rejectsInvalidCoordinatesWithoutReplacingPreviousSite() {
        val repo = LocationRepository()
        repo.setManual(input()).getOrThrow()
        val original = repo.state.value.selected
        for (bad in listOf(input("91"), input("NaN"), input("Infinity"), input(lon="181"), input("abc"), input(zone=""), input(zone="Beijing"))) {
            assertTrue("Invalid input $bad", repo.setManual(bad).isFailure)
            assertEquals(original, repo.state.value.selected)
            assertNotNull(repo.state.value.error)
        }
    }

    @Test fun coreSiteAlsoRejectsInvalidNumbersForNonUiConsumers() {
        for ((lat, lon) in listOf(Double.NaN to 0.0, 91.0 to 0.0, 0.0 to Double.POSITIVE_INFINITY, 0.0 to -181.0)) {
            assertTrue(runCatching { Site(lat, lon, 0.0, 0.0, ZoneOffset.UTC) }.isFailure)
        }
        assertTrue(runCatching { Site(0.0, 0.0, 0.0, -1.0, ZoneOffset.UTC) }.isFailure)
        Site(-90.0, -180.0, 0.0, 0.0, ZoneOffset.UTC)
        Site(90.0, 180.0, 0.0, 0.0, ZoneOffset.UTC)
    }

    @Test fun pastedCoordinatesAreLatitudeThenLongitudeWithoutGuessing() {
        assertEquals(30.27 to 120.15, ManualLocationInput.parseCoordinatePair("30.27，120.15"))
        assertTrue(runCatching { ManualLocationInput.parseCoordinatePair("120.15,30.27") }.isFailure)
        assertTrue(runCatching { ManualLocationInput.parseCoordinatePair("30,120,5") }.isFailure)
    }

    @Test fun gpsHasExplicitDeviceZoneAccuracyAgeAndAssumedSeaLevel() {
        val repo = LocationRepository()
        val fixTime = Instant.parse("2026-10-23T00:00:00Z")
        repo.acceptDeviceFix(DeviceFix(30.27,120.15,fixTime,150.0,"network"), ZoneId.of("Asia/Shanghai")).getOrThrow()
        val selection = repo.state.value.selected!!
        assertEquals(ElevationSource.ASSUMED_ZERO, selection.elevationSource)
        assertEquals(0.0, selection.site.elevationMeters, 0.0)
        assertEquals(ZoneSource.DEVICE, selection.zoneSource)
        assertEquals(150.0, selection.accuracyMeters!!, 0.0)
        assertEquals(Duration.ofHours(2), selection.ageAt(fixTime.plusSeconds(7200)))
        repo.fail("GPS unavailable")
        assertEquals(selection, repo.state.value.selected)
        assertTrue(repo.setManual(input()).isSuccess)
    }

    @Test fun olderCachedDeviceFixDoesNotReplaceNewerFix() {
        val repo = LocationRepository()
        val now = Instant.parse("2026-10-23T00:00:00Z")
        repo.acceptDeviceFix(DeviceFix(30.0,120.0,now,5.0,"gps"), ZoneOffset.UTC).getOrThrow()
        repo.acceptDeviceFix(DeviceFix(20.0,110.0,now.minusSeconds(3600),500.0,"network"), ZoneOffset.UTC).getOrThrow()
        assertEquals(30.0, repo.state.value.selected!!.site.latitude, 0.0)
    }

    @Test fun delayedGpsCallbackCannotOverwriteUserManualSelection() {
        val repo = LocationRepository()
        val request = repo.beginDeviceRefresh()
        repo.setManual(input()).getOrThrow()
        repo.acceptDeviceFix(DeviceFix(10.0,20.0,Instant.parse("2026-10-23T00:00:00Z"),3.0,"gps"),ZoneOffset.UTC,request).getOrThrow()
        assertEquals(LocationSource.MANUAL,repo.state.value.selected!!.source)
        assertEquals(30.27,repo.state.value.selected!!.site.latitude,0.0)
    }

    @Test fun endingForegroundRefreshClearsSpinnerWithoutInventingSite() {
        val repo=LocationRepository()
        repo.beginDeviceRefresh()
        repo.endDeviceRefresh()
        assertEquals(LocationStatus.UNSET,repo.state.value.status)
        assertNull(repo.state.value.selected)
    }

    @Test fun manualElevationAndOnSiteConfirmationAreKeptDistinct() {
        val value = input().copy(elevationMeters="108", eyeHeightMeters="2", confirmedOnSite=true).selection()
        assertEquals(108.0, value.site.elevationMeters, 0.0)
        assertEquals(2.0, value.site.eyeHeightMeters, 0.0)
        assertEquals(ElevationSource.MANUAL, value.elevationSource)
        assertTrue(value.confirmedManualOnSite)
        assertFalse(input().selection().confirmedManualOnSite)
    }

    @Test fun dstGapRequiresUserAdjustmentAndOverlapRequiresOffsetChoice() {
        val zone = ZoneId.of("Europe/London")
        assertEquals(LocalTimeResolution.Gap(LocalDateTime.parse("2026-03-29T02:00:00")), resolveLocalTime(LocalDateTime.parse("2026-03-29T01:30:00"), zone))
        val fall = LocalDateTime.parse("2026-10-25T01:30:00")
        assertEquals(LocalTimeResolution.Overlap(listOf(ZoneOffset.ofHours(1), ZoneOffset.UTC)), resolveLocalTime(fall, zone))
        assertEquals(LocalTimeResolution.Resolved(Instant.parse("2026-10-25T00:30:00Z"),ZoneOffset.ofHours(1)), resolveLocalTime(fall,zone,ZoneOffset.ofHours(1)))
        assertEquals(LocalTimeResolution.Resolved(Instant.parse("2026-10-25T01:30:00Z"),ZoneOffset.UTC), resolveLocalTime(fall,zone,ZoneOffset.UTC))
        assertTrue(runCatching { resolveLocalTime(fall,zone,ZoneOffset.ofHours(8)) }.isFailure)
    }
}
