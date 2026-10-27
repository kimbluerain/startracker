package local.skytrails.app.ui.renderer

import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ui.sky.GalacticCameraBasis
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GalaxyTilesTest {
 private val center=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,1.0,0.0))
 @Test fun zoomChoosesHighDetailButEveryViewportFitsTheByteBudget() {
  val zoom=GalaxyTiles.select(1080,2340,20.0,center)
  assertEquals(16384,zoom.level);assertTrue(zoom.tiles.isNotEmpty())
  for(fov in listOf(20.0,40.0,70.0,100.0)) {
   val plan=GalaxyTiles.select(1080,2340,fov,center)
   assertTrue(plan.tiles.size*GalaxyTiles.TILE_BYTES<=48L*1024*1024)
   assertEquals(plan.tiles.size,plan.tiles.toSet().size)
  }
 }
 @Test fun selectedTilesCoverDenseViewportSamplesThroughSeamsAndPoles() {
  for(l in listOf(0.0,179.9))for(b in listOf(-90.0,-65.0,0.0,65.0,90.0))for(fov in listOf(20.0,100.0)) {
   val lon=Math.toRadians(l);val lat=Math.toRadians(b)
   val basis=GalacticCameraBasis(CameraVector(sin(lon),-sin(lat)*cos(lon),cos(lat)*cos(lon)),CameraVector(-cos(lon),-sin(lat)*sin(lon),cos(lat)*sin(lon)),CameraVector(0.0,cos(lat),sin(lat)))
   val plan=GalaxyTiles.select(1080,2340,fov,basis);val keys=plan.tiles.toSet();val focal=1080/(2*tan(Math.toRadians(fov)/2))
   for(y in 0..30)for(x in 0..20) {
    val uv=basis.coordinate(CameraVector((x/20.0*1080-540)/focal,(y/30.0*2340-1170)/focal,1.0))
    val key=GalaxyTile(plan.level,(uv.u*plan.level/512).toInt().coerceIn(0,plan.level/512-1),(uv.v*plan.level/1024).toInt().coerceIn(0,plan.level/1024-1))
    assertTrue("Missing visible $key at $l,$b/$fov",key in keys)
   }
  }
 }
 @Test fun seamAndPoleSelectionsWrapAndFallBackRatherThanExceedBudget() {
  val seam=center.copy(x=CameraVector(0.0,0.0,-1.0),y=CameraVector(1.0,0.0,0.0))
  val edge=GalaxyTiles.select(1080,2340,20.0,seam)
  assertTrue(edge.tiles.any{it.x==0});assertTrue(edge.tiles.any{it.x==edge.level/512-1})
  val pole=GalacticCameraBasis(CameraVector(0.0,-1.0,0.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,0.0,1.0))
  val polar=GalaxyTiles.select(1080,2340,20.0,pole)
  assertEquals(polar.level/512,polar.tiles.filter{it.y==0}.map{it.x}.toSet().size)
  assertTrue(polar.tiles.size*GalaxyTiles.TILE_BYTES<=GalaxyTiles.BUDGET_BYTES)
  assertTrue(polar.tiles.all{it.x in 0 until it.level/512&&it.y in 0 until it.level/1024})
 }
}
