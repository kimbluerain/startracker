package local.skytrails.app.core

import com.google.gson.*
import java.time.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class MilkyWayReferenceTest {
    private val geometry = MilkyWayGeometry()
    private val site = Site(30.2741,120.1551,0.0,0.0,ZoneId.of("Asia/Shanghai"))
    private val at = Instant.parse("2026-10-23T12:00:00Z")

    // Detects swapped axes, radians/degrees, or a different Galactic reference frame.
    @Test fun fixedGalacticRotationMatchesEightIndependentErfaDirections() {
        val errors = rows("galactic-fixtures.json").map { row ->
            val actual=geometry.toEquatorial(GalacticCoordinate(row["galacticLongitudeDeg"].asDouble,row["galacticLatitudeDeg"].asDouble))
            val error=angle(actual.raDeg,actual.decDeg,row["icrsRaDeg"].asDouble,row["icrsDecDeg"].asDouble)
            assertTrue("ERFA $row error=$error",error<=0.05)
            error
        }
        assertEquals(8,errors.size)
        println("ERFA 8 fixed directions max error=${errors.max()} deg")
    }

    // An incorrect epoch, longitude sign, omitted precession, or geometric l0 as Sgr A* fails.
    @Test fun all45IndependentAstropyAirlessVectorsAcross2000To2050() {
        val errors=rows("galactic-altaz-fixtures.json").map { row ->
            val s=row.getAsJsonObject("site")
            val site=Site(s["latitude"].asDouble,s["longitude"].asDouble,0.0,0.0,ZoneId.of(s["zone"].asString))
            val at=Instant.parse(row["instantUtc"].asString)
            val c=row.getAsJsonObject("sourceCoordinate")
            val actual=if(c.has("galacticLongitudeDeg")) geometry.direction(GalacticCoordinate(c["galacticLongitudeDeg"].asDouble,c["galacticLatitudeDeg"].asDouble),site,at)
                else geometry.center(site,at)
            val error=angle(actual.azimuthDeg,actual.altitudeDeg,row["azimuthDeg"].asDouble,row["altitudeDeg"].asDouble)
            assertTrue("Astropy ${row["id"]} $at $site error=$error",error<=0.05)
            error
        }
        assertEquals(45,errors.size)
        println("Astropy 45 AIRLESS vectors max error=${errors.max()} deg (frozen EOP/leap seconds)")
    }

    @Test fun physicalRadioCenterIsNotGeometricLongitudeZero() {
        val origin=geometry.direction(GalacticCoordinate(0.0,0.0),site,at)
        val center=geometry.center(site,at)
        assertEquals(0.072393596,angle(origin.azimuthDeg,origin.altitudeDeg,center.azimuthDeg,center.altitudeDeg),0.00001)
    }

    @Test fun inverseClosureIncludesPolesAndLongitudeWrap() {
        for(l in listOf(-1.0,0.0,123.0,359.0,360.0)) for(b in listOf(-90.0,-45.0,0.0,67.0,90.0)) {
            val p=geometry.toGalactic(geometry.toEquatorial(GalacticCoordinate(l,b)))
            assertTrue(angle(l,b,p.longitudeDeg,p.latitudeDeg)<0.00001)
        }
    }

    @Test fun rotationDateAndSiteChangeDirectionButDisplayTimezoneDoesNot() {
        val original=geometry.center(site,at)
        fun distance(b:SkyDirection)=angle(original.azimuthDeg,original.altitudeDeg,b.azimuthDeg,b.altitudeDeg)
        assertTrue(distance(geometry.center(site,at.plusSeconds(6*3600)))>60)
        assertTrue(distance(geometry.center(site,at.plusSeconds(180*86400)))>100)
        assertTrue(distance(geometry.center(site.copy(latitude=-33.8688,longitude=151.2093),at))>10)
        assertEquals(original,geometry.center(site.copy(zone=ZoneId.of("Europe/London")),at))
    }

    @Test fun closedPlaneAndIllustrativeBandStayContinuousAndShareContext() {
        val sky=geometry.sky(site,at)
        assertEquals(site,sky.site); assertEquals(at,sky.at)
        for(line in listOf(sky.plane,sky.bandNorth,sky.bandSouth)) {
            assertEquals(361,line.size)
            assertEquals(line.first(),line.last())
            assertTrue(line.any { it.altitudeDeg<0 }); assertTrue(line.any { it.altitudeDeg>0 })
            for((a,b) in line.zipWithNext()) assertTrue(angle(a.azimuthDeg,a.altitudeDeg,b.azimuthDeg,b.altitudeDeg)<1.001)
        }
        assertEquals(10.0,angle(sky.bandNorth[0].azimuthDeg,sky.bandNorth[0].altitudeDeg,sky.bandSouth[0].azimuthDeg,sky.bandSouth[0].altitudeDeg),0.00001)
    }

    @Test fun defaultAstronomyServiceProvidesGalacticDirectionsAlongsideSunAndMoon() {
        val adapter=AstronomyEngineAdapter()
        val result=runCatching { adapter.galacticCenter(site,at) }
        assertTrue("Default service must configure Galaxy: ${result.exceptionOrNull()}",result.isSuccess)
        assertEquals(geometry.center(site,at),result.getOrNull())
        assertEquals(361,adapter.galacticPlane(site,at).size)
        assertTrue(adapter.position(Target.SUN,site,at,Atmosphere.AIRLESS).altitudeDeg.isFinite())
    }

    private fun rows(name:String)=JsonParser.parseReader(javaClass.getResourceAsStream("/reference/galactic/$name")!!.reader()).asJsonObject.getAsJsonArray("fixtures").map {it.asJsonObject}
    private fun angle(lon1:Double,lat1:Double,lon2:Double,lat2:Double):Double {
        fun vector(lon:Double,lat:Double):DoubleArray {
            val l=Math.toRadians(lon); val b=Math.toRadians(lat)
            return doubleArrayOf(cos(b)*cos(l),cos(b)*sin(l),sin(b))
        }
        val a=vector(lon1,lat1); val b=vector(lon2,lat2)
        val cross=doubleArrayOf(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
        return Math.toDegrees(atan2(sqrt(cross.sumOf {it*it}),a.indices.sumOf {a[it]*b[it]}))
    }
}
