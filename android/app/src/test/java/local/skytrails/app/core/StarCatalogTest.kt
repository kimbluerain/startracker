package local.skytrails.app.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StarCatalogTest {
 private val site=Site(30.2741,120.1551,0.0,1.6,ZoneId.of("Asia/Shanghai"))
 @Test fun completeFourPatternsReferenceOnlyRealUniqueStars() {
  assertEquals(25,StarCatalog.stars.size)
  assertEquals(25,StarCatalog.stars.map{it.hip}.distinct().size)
  assertEquals(setOf("dipper","orion","cassiopeia","crux"),StarCatalog.constellations.map{it.id}.toSet())
  val ids=StarCatalog.stars.map{it.hip}.toSet()
  StarCatalog.constellations.forEach{c->assertTrue(c.members.all{it in ids});assertTrue(c.edges.none{it.first==it.second})}
  val dipper=StarCatalog.constellations.single{it.id=="dipper"}
  assertEquals(setOf(54061,53910,58001,59774,62956,65378,67301),dipper.members)
 }
 @Test fun datesAndPlacesAffectDirectionsWithoutChangingCatalog() {
  val at=Instant.parse("2026-10-26T12:00:00Z")
  val a=StarCatalog.sky(site,at);val later=StarCatalog.sky(site,at.plusSeconds(6*3600))
  assertEquals(25,a.positions.size);assertEquals(4,a.centers.size)
  assertNotEquals(a.positions[27989],later.positions[27989])
  val south=StarCatalog.sky(Site(-33.86,151.21,0.0,1.6,ZoneId.of("Australia/Sydney")),at)
  assertNotEquals(a.positions[60718],south.positions[60718])
  assertTrue(a.positions.values.all{it.azimuthDeg.isFinite()&&it.altitudeDeg in -90.0..90.0})
 }
}
