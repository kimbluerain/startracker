package local.skytrails.app.core

import com.google.gson.*
import java.time.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class EphemerisReferenceTest {
    private val engine = AstronomyEngineAdapter()
    private val hangzhou = Site(30.2741, 120.1551, 0.0, 0.0, ZoneId.of("Asia/Shanghai"))

    @Test fun airlessTopocentricDirectionsMatchIndependentJplVectors() {
        val errors = fixture("horizons-fixtures.json").map { row ->
            val at = Instant.parse(row["instantUtc"].asString)
            val target = Target.valueOf(row["target"].asString)
            val result = runCatching { engine.position(target, site(row.getAsJsonObject("site")), at, Atmosphere.AIRLESS) }
            assertTrue("Topocentric solver must succeed: ${result.exceptionOrNull()}", result.isSuccess)
            val actual = result.getOrThrow()
            val expected = SkyDirection(row["azimuthDeg"].asDouble, row["altitudeDeg"].asDouble)
            val error = angle(actual, expected)
            assertTrue("$target $at ${row["site"]}: vector error $error deg", error <= 0.05)
            error
        }
        println("JPL ${errors.size} independent vectors: max angular error=${errors.max()} deg")
    }

    @Test fun supplementalIndependentDirectionsCoverZenithSeasonsDateLineAndElevatedObserver() {
        val errors = fixture("supplement-fixtures.json").filter { !it.has("kind") }.map { row ->
            val site = site(row.getAsJsonObject("site"))
            val actual = engine.position(Target.valueOf(row["target"].asString), site, Instant.parse(row["instantUtc"].asString), Atmosphere.AIRLESS)
            val error = angle(actual, SkyDirection(row["azimuthDeg"].asDouble, row["altitudeDeg"].asDouble))
            assertTrue("Supplement $row: vector error=$error", error <= 0.05)
            error
        }
        val zenith = fixture("supplement-fixtures.json").filter { !it.has("kind") }.maxOf { it["altitudeDeg"].asDouble }
        assertTrue("Fixture must exercise a real near-zenith direction", zenith > 89.5)
        println("JPL supplemental ${errors.size} vectors: max=${errors.max()} deg, maximum reference altitude=$zenith deg")
    }

    @Test fun eventAzimuthMatchesIndependentJplAtExactlyTheSameInstant() {
        val errors = fixture("supplement-fixtures.json").filter { it.has("kind") }.map { row ->
            val site = site(row.getAsJsonObject("site"))
            val at = Instant.parse(row["instantUtc"].asString)
            val target = Target.valueOf(row["target"].asString)
            val actual = engine.events(target,site,at.atZone(site.zone).toLocalDate()).single { it.kind.name == row["kind"].asString }
            // Fixture timestamps are solver-selected query times, not independent time oracles.
            assertEquals(at, actual.instant)
            val delta = abs(((actual.direction.azimuthDeg - row["azimuthDeg"].asDouble + 540.0) % 360.0) - 180.0)
            assertTrue("$target $at event azimuth error=$delta deg", delta <= 0.1)
            delta
        }
        println("JPL ${errors.size} exact-event azimuths: max=${errors.max()} deg")
    }

    @Test fun eyeHeightAndGroundElevationHaveDistinctHorizonDipButSameObserverPosition() {
        val atop = hangzhou.copy(elevationMeters=500.0,eyeHeightMeters=1000.0)
        val ground = hangzhou.copy(elevationMeters=1500.0,eyeHeightMeters=0.0)
        val at = Instant.parse("2026-10-23T00:00:00Z")
        assertEquals(engine.position(Target.MOON,atop,at,Atmosphere.AIRLESS),engine.position(Target.MOON,ground,at,Atmosphere.AIRLESS))
        val date = LocalDate.parse("2026-10-23")
        val highRise = engine.events(Target.SUN,atop,date).single { it.kind == EventKind.RISE }.instant
        val groundRise = engine.events(Target.SUN,ground,date).single { it.kind == EventKind.RISE }.instant
        assertTrue("Elevated observer sees earlier sunrise due to horizon dip",Duration.between(highRise,groundRise).seconds > 120)
    }

    @Test fun independentUsnoEventsBelongToLocalDayAndMatchWithinTwoMinutes() {
        val errors = mutableListOf<Double>()
        fixture("usno-fixtures.json").forEach { row ->
            val site = site(row.getAsJsonObject("site"))
            val date = LocalDate.parse(row["localDate"].asString)
            for (target in Target.values()) {
                val result = runCatching { engine.events(target, site, date) }
                assertTrue("Local-day event search must succeed: ${result.exceptionOrNull()}", result.isSuccess)
                val actual = result.getOrThrow()
                val expected = row.getAsJsonArray("events").map { it.asJsonObject }.filter { it["target"].asString == target.name }
                assertEquals("$target $date", expected.size, actual.size)
                assertEquals(actual.sortedBy { it.instant }, actual)
                expected.forEach { event ->
                    val match = actual.single { it.kind.name == event["kind"].asString }
                    assertEquals(date, match.instant.atZone(site.zone).toLocalDate())
                    val delta = abs(Duration.between(Instant.parse(event["nominalInstantUtc"].asString), match.instant).toMillis()) / 1000.0
                    assertTrue("$target $date ${match.kind}: $delta seconds", delta <= 120.0)
                    errors.add(delta)
                }
            }
        }
        println("USNO ${errors.size} independent events: max time error=${errors.max()} seconds")
    }

    @Test fun changingDisplayZonePreservesDirectionAtSameInstant() {
        val at = Instant.parse("2026-10-23T01:23:45Z")
        assertEquals(engine.position(Target.MOON, hangzhou, at, Atmosphere.AIRLESS),
            engine.position(Target.MOON, hangzhou.copy(zone = ZoneId.of("America/Los_Angeles")), at, Atmosphere.AIRLESS))
    }

    @Test fun polarSummerAndWinterHaveNoSunEvents() {
        val tromso = Site(69.6492, 18.9553, 0.0, 0.0, ZoneId.of("Europe/Oslo"))
        for (date in listOf("2026-06-21", "2026-12-21")) assertTrue(engine.events(Target.SUN, tromso, LocalDate.parse(date)).isEmpty())
    }

    @Test fun moonSetBeforeRiseIsStillOwnedByCurrentDay() {
        val events = engine.events(Target.MOON, hangzhou, LocalDate.parse("2026-10-23"))
        assertEquals(listOf(EventKind.SET, EventKind.RISE), events.map { it.kind })
        assertEquals("2026-10-22", events.first().instant.atZone(ZoneOffset.UTC).toLocalDate().toString())
    }

    @Test fun dateLineAndDstWindowsNeverBorrowNeighbouringDays() {
        val sites = listOf(hangzhou.copy(longitude = -157.4, latitude = 1.87, zone = ZoneId.of("Pacific/Kiritimati")),
            hangzhou.copy(longitude = -0.1278, latitude = 51.5074, zone = ZoneId.of("Europe/London")))
        for (site in sites) for (date in listOf("2026-03-29", "2026-10-25", "2049-12-31")) {
            val localDate = LocalDate.parse(date)
            for (event in engine.events(Target.MOON, site, localDate)) assertEquals(localDate, event.instant.atZone(site.zone).toLocalDate())
        }
    }

    @Test fun singleRiseOrSetDayIsNotDroppedOrPairedAcrossMidnight() {
        val setDay = engine.events(Target.MOON,hangzhou,LocalDate.parse("2026-10-04"))
        val riseDay = engine.events(Target.MOON,hangzhou,LocalDate.parse("2026-10-20"))
        assertEquals(listOf(EventKind.SET),setDay.map { it.kind })
        assertEquals(listOf(EventKind.RISE),riseDay.map { it.kind })
    }

    @Test fun noEventsForSkippedCivilDay() {
        assertTrue(engine.events(Target.SUN, hangzhou.copy(zone = ZoneId.of("Pacific/Apia")), LocalDate.parse("2011-12-30")).isEmpty())
    }

    @Test fun standardRefractionRaisesNearHorizonDirectionWithoutChangingAzimuth() {
        val at = Instant.parse("2026-10-22T22:10:00Z")
        val airless = engine.position(Target.SUN, hangzhou, at, Atmosphere.AIRLESS)
        val standard = engine.position(Target.SUN, hangzhou, at, Atmosphere.STANDARD)
        assertEquals(airless.azimuthDeg, standard.azimuthDeg, 1e-9)
        assertTrue(standard.altitudeDeg > airless.altitudeDeg + 0.3)
    }

    private fun fixture(name: String) = JsonParser.parseReader(javaClass.getResourceAsStream("/reference/$name")!!.reader()).asJsonObject.getAsJsonArray("fixtures").map { it.asJsonObject }
    private fun site(row: JsonObject) = Site(row["latitude"].asDouble, row["longitude"].asDouble, row["elevationMeters"].asDouble, row.get("eyeHeightMeters")?.asDouble ?: 0.0, ZoneId.of(row["zone"].asString))

    companion object {
        // Independent ENU vector comparison remains stable at zenith and across azimuth 0/360.
        fun angle(a: SkyDirection, b: SkyDirection): Double {
            fun vector(d: SkyDirection): DoubleArray {
                val az = Math.toRadians(d.azimuthDeg); val alt = Math.toRadians(d.altitudeDeg)
                return doubleArrayOf(cos(alt)*sin(az), cos(alt)*cos(az), sin(alt))
            }
            val u=vector(a); val v=vector(b)
            val cross=doubleArrayOf(u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
            return Math.toDegrees(atan2(sqrt(cross.sumOf { it*it }), u.indices.sumOf { u[it]*v[it] }))
        }
    }
}
