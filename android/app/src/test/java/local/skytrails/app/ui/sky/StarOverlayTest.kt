package local.skytrails.app.ui.sky

import local.skytrails.app.ar.Pixel
import local.skytrails.app.core.*
import local.skytrails.app.core.Target
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StarOverlayTest {
 private val site=Site(30.2741,120.1551,0.0,0.0,ZoneId.of("Asia/Shanghai"))
 private val at=Instant.parse("2026-10-26T12:00:00Z")
 private val sky=StarCatalog.sky(site,at)
 private val center=sky.centers.getValue("orion")
 private val projection=SkyProjection.virtual(1080,2340,70.0,center.azimuthDeg,center.altitudeDeg)
 @Test fun independentMarkerLineAndNameChoicesRenderTheSelectedPatternOnly() {
  val all=StarLayers(enabled=setOf("orion"))
  val full=StarOverlay.render(sky,all,projection)
  assertEquals(8,full.stars.size);assertTrue(full.lines.isNotEmpty());assertEquals(listOf("猎户座"),full.names.map{it.text})
  assertTrue(full.stars.all{it.hip in StarCatalog.constellation("orion")!!.members})
  val marksOff=StarOverlay.render(sky,all.copy(stars=false),projection)
  assertTrue(marksOff.stars.isEmpty());assertEquals(full.lines,marksOff.lines);assertEquals(full.names,marksOff.names)
  val linesOff=StarOverlay.render(sky,all.copy(lines=false),projection)
  assertTrue(linesOff.lines.isEmpty());assertEquals(full.stars,linesOff.stars)
  val namesOff=StarOverlay.render(sky,all.copy(names=false),projection)
  assertTrue(namesOff.names.isEmpty());assertEquals(full.lines,namesOff.lines)
  assertEquals(StarDrawing(emptyList(),emptyList(),emptyList()),StarOverlay.render(sky,all.copy(enabled=emptySet()),projection))
 }
 @Test fun masterHidesEveryRepresentationAndTwinkleCannotMoveCoordinates() {
  val layers=StarLayers(enabled=setOf("orion"))
  val base=StarOverlay.render(sky,layers,projection)
  assertEquals(base,StarOverlay.render(sky,layers.copy(twinkle=true),projection))
  assertEquals(StarDrawing(emptyList(),emptyList(),emptyList()),StarOverlay.render(sky,layers.copy(masterVisible=false,twinkle=true),projection))
 }
 @Test fun behindPatternNeverCreatesFrontViewportMarkersOrNames() {
  val opposite=SkyProjection.virtual(1080,2340,70.0,(center.azimuthDeg+180)%360,-center.altitudeDeg)
  val result=StarOverlay.render(sky,StarLayers(enabled=setOf("orion")),opposite)
  assertTrue(result.stars.isEmpty());assertTrue(result.names.isEmpty());assertTrue(result.lines.isEmpty())
 }
 @Test fun endpointTextMovesAwayFromCurrentMoonWithoutMovingAnchor() {
  val endpoint=SkyText("起 · 10/26 00:00",Pixel(500.0,500.0),"moon_start")
  val below=moonEndpointLabelPosition(endpoint,TargetIndicator.Visible(Pixel(510.0,520.0),false),2f)
  val above=moonEndpointLabelPosition(endpoint,TargetIndicator.Visible(Pixel(510.0,480.0),false),2f)
  assertEquals(Pixel(518.0,440.0),below);assertEquals(Pixel(518.0,524.0),above)
  assertEquals(Pixel(500.0,500.0),endpoint.pixel)
 }
 @Test fun rangeUsesActualCivilDayStartWhenMidnightIsSkipped() {
  val chile=Site(-33.45,-70.66,0.0,0.0,ZoneId.of("America/Santiago"))
  val date=Instant.parse("2026-09-06T12:00:00Z");val d=SkyDirection(0.0,0.0)
  val c=SkyCalculation(chile,date,d,d,GalacticSky(chile,date,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),listOf(d,d))
  assertEquals("当日月轨 · 01:00—次日00:00",moonRangeText(c))
  assertNull(moonRangeText(c.copy(moonPath=emptyList())))
 }
 @Test fun actualMoonDayRemainsOpenAndNamesBothDifferentDatesAtVisibleEndpoints() {
  val engine=AstronomyEngineAdapter();val day=dayWindow(LocalDate.of(2026,10,26),site.zone)
  val seconds=Duration.between(day.start,day.endExclusive).seconds
  val path=(0..144).map{engine.position(Target.MOON,site,day.start.plusSeconds(seconds*it/144),Atmosphere.AIRLESS)}
  val d=SkyDirection(0.0,0.0)
  val c=SkyCalculation(site,at,d,d,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),d),emptyList(),emptyList(),path)
  val view=SkyProjection.virtual(1080,2340,70.0,202.0,75.0)
  val labels=moonEndpointLabels(c,view)
  assertEquals(listOf("起 · 10/26 00:00","止 · 10/27 00:00"),labels.map{it.text})
  assertEquals(609.5924,labels[0].pixel.x,.01);assertEquals(1232.2334,labels[0].pixel.y,.01)
  assertEquals(475.8536,labels[1].pixel.x,.01);assertEquals(1104.0448,labels[1].pixel.y,.01)
  // With all consecutive samples front-facing, path() adds n-1 edges and never closes.
  val local=listOf(SkyDirection(0.0,10.0),SkyDirection(5.0,12.0),SkyDirection(10.0,10.0))
  assertEquals(2,GalacticOverlay.path(local,SkyProjection.virtual(1000,1000,70.0,5.0,10.0)).size)
 }
}
