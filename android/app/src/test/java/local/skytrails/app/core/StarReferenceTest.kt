package local.skytrails.app.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.*

class StarReferenceTest {
 @Test fun properMotionMatchesIndependentIcrsPositionsWithinOneMicrodegree() {
  val rows=JsonParser.parseReader(javaClass.getResourceAsStream("/reference/stars/stars-altaz-fixtures.json")!!.reader()).asJsonObject.getAsJsonArray("fixtures")
  rows.forEach{element->val row=element.asJsonObject
   val star=StarCatalog.stars.single{it.hip==row["hip"].asInt}
   val actual=StarCatalog.equatorial(star,Instant.parse(row["instantUtc"].asString))
   val error=angle(SkyDirection(actual.raDeg,actual.decDeg),SkyDirection(row["propagatedIcrsRaDeg"].asDouble,row["propagatedIcrsDecDeg"].asDouble))
   assertTrue("Proper motion/cos(dec) HIP${star.hip} ${row["instantUtc"]}: $error",error<.000001)
  }
 }
 @Test fun independentAstropy150AirlessDirections2000To2050BothHemispheres() {
  val rows=JsonParser.parseReader(javaClass.getResourceAsStream("/reference/stars/stars-altaz-fixtures.json")!!.reader()).asJsonObject.getAsJsonArray("fixtures")
  val cache=mutableMapOf<Pair<Site,Instant>,StarSky>()
  var maxError=0.0
  rows.forEach{element->
   val row=element.asJsonObject;val p=row.getAsJsonObject("site")
   val site=Site(p["latitude"].asDouble,p["longitude"].asDouble,p["elevationMeters"].asDouble,0.0,ZoneId.of("UTC"))
   val at=Instant.parse(row["instantUtc"].asString)
   val actual=cache.getOrPut(site to at){StarCatalog.sky(site,at)}.positions.getValue(row["hip"].asInt)
   val error=angle(actual,SkyDirection(row["azimuthDeg"].asDouble,row["altitudeDeg"].asDouble))
   maxError=max(maxError,error)
   assertTrue("HIP${row["hip"]} $at ${p["id"]} error=$error deg",error<.05)
  }
  assertEquals(150,rows.size());println("Independent Astropy 150 star directions max error=$maxError deg")
 }
 private fun angle(a:SkyDirection,b:SkyDirection):Double {
  fun v(d:SkyDirection):DoubleArray {val z=Math.toRadians(d.azimuthDeg);val h=Math.toRadians(d.altitudeDeg);return doubleArrayOf(cos(h)*cos(z),cos(h)*sin(z),sin(h))}
  val x=v(a);val y=v(b);val cross=doubleArrayOf(x[1]*y[2]-x[2]*y[1],x[2]*y[0]-x[0]*y[2],x[0]*y[1]-x[1]*y[0])
  return Math.toDegrees(atan2(sqrt(cross.sumOf{it*it}),x.indices.sumOf{x[it]*y[it]}))
 }
}
