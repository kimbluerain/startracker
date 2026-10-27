package local.skytrails.app.ui.sky

import local.skytrails.app.ar.CameraVector
import local.skytrails.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

class GalacticTextureProjectionTest {
 // Synthetic diagnostic panorama: Galactic axes use independently assigned colored quadrants.
 private val identity=GalacticCameraBasis(CameraVector(1.0,0.0,0.0),CameraVector(0.0,1.0,0.0),CameraVector(0.0,0.0,1.0))
 private fun direction(l:Double,b:Double):CameraVector {val longitude=Math.toRadians(l);val latitude=Math.toRadians(b);return CameraVector(cos(latitude)*cos(longitude),cos(latitude)*sin(longitude),sin(latitude))}
 @Test fun nasaLongitudeIncreasesLeftAndNorthIsAtTopIncludingNoncentralSamples() {
  listOf(Triple(0.0,0.0,TextureCoordinate(.5,.5)),Triple(90.0,0.0,TextureCoordinate(.25,.5)),Triple(270.0,0.0,TextureCoordinate(.75,.5)),Triple(45.0,30.0,TextureCoordinate(.375,1.0/3)),Triple(315.0,-45.0,TextureCoordinate(.625,.75))).forEach{(l,b,expected)->
   val actual=identity.coordinate(direction(l,b));assertEquals(expected.u,actual.u,1e-12);assertEquals(expected.v,actual.v,1e-12)
  }
 }
 @Test fun asymmetricDiagnosticTextureWouldNotMirrorAcrossCameraViewport() {
  // Camera faces Galactic l=0, with l=90 to its LEFT and Galactic north UP.
  val basis=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,1.0,0.0))
  fun tile(ray:CameraVector):String {val p=basis.coordinate(ray);return (if(p.u<.5)"L" else "R")+(if(p.v<.5)"N" else "S")}
  assertEquals("LN",tile(CameraVector(-.5,.3,1.0)))
  assertEquals("RN",tile(CameraVector(.5,.3,1.0)))
  assertEquals("LS",tile(CameraVector(-.5,-.3,1.0)))
  assertEquals("RS",tile(CameraVector(.5,-.3,1.0)))
 }
 @Test fun galacticTextureAtAnOffAxisLandmarkAgreesWithIndependentFixtureDirection() {
  // Existing ERFA/Astropy data bound MilkyWayGeometry; here verify projection orientation,
  // not a sample->inverse roundtrip. NGP must remain north of the Galactic equator.
  val site=Site(30.25,120.16,0.0,0.0,ZoneId.of("Asia/Shanghai"));val time=Instant.parse("2026-10-24T13:00:00Z")
  val geometry=MilkyWayGeometry();val axes=listOf(GalacticCoordinate(0.0,0.0),GalacticCoordinate(90.0,0.0),GalacticCoordinate(0.0,90.0)).map{geometry.direction(it,site,time)}
  val center=geometry.direction(GalacticCoordinate(45.0,30.0),site,time)
  val projection=SkyProjection.virtual(800,1600,70.0,center.azimuthDeg,center.altitudeDeg)
  val basis=GalacticCameraBasis.from(projection,axes)!!
  val pixelCenter=basis.coordinate(CameraVector(0.0,0.0,1.0))
  assertEquals(.375,pixelCenter.u,1e-8);assertEquals(1.0/3,pixelCenter.v,1e-8)
 }
 @Test fun seamRemainsWrappedAndPolesStayInsideImage() {
  for(l in listOf(-180.0001,-180.0,179.9999,180.0,360.0))for(b in listOf(-90.0,0.0,90.0)) {
   val uv=identity.coordinate(direction(l,b));assertTrue(uv.u in 0.0..<1.0);assertTrue(uv.v in 0.0..1.0)
  }
 }
 @Test fun disablingGalaxyExcludesAllDiffusePixelsWhileRetainingStars() {
  val star=0xff802010.toInt()
  val dark=composeSkyPixel(star,0xff000000.toInt(),false,1.0,false,1.0)
  val bright=composeSkyPixel(star,0xffffffff.toInt(),false,1.0,false,1.0)
  assertEquals(dark,bright)
  assertNotEquals(bright,composeSkyPixel(star,0xffffffff.toInt(),true,1.0,false,1.0))
  assertNotEquals(dark,composeSkyPixel(0xff000000.toInt(),0xffffffff.toInt(),false,1.0,false,1.0))
 }

}
