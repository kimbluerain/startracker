package local.skytrails.app

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14.*
import android.opengl.GLES20.*
import android.opengl.GLUtils
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ui.sky.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

class FieldStarsPixelTest {
 private val rigel=StarCatalog.stars.first{it.hip==24436}
 private val entry=rigel.let{FieldStar(it.hip,it.raDeg.toFloat(),it.decDeg.toFloat(),it.pmRaMasYear.toFloat(),it.pmDecMasYear.toFloat(),it.magnitude.toFloat(),-.03f)}
 private val site=Site(30.2741,120.1551,0.0,0.0,ZoneId.of("Asia/Shanghai"))
 private fun frame(at:Instant,fov:Double,stars:StarLayers=StarLayers(false,false,false,setOf("orion")),night:Boolean=false):SkyFrameSnapshot {
  val sky=StarCatalog.sky(site,at);val target=sky.positions.getValue(24436)
  val projection=SkyProjection.virtual(W,H,fov,target.azimuthDeg-2,target.altitudeDeg-1)
  val geo=MilkyWayGeometry()
  val basis=GalacticCameraBasis.from(projection,listOf(GalacticCoordinate(0.0,0.0),GalacticCoordinate(90.0,0.0),GalacticCoordinate(0.0,90.0)).map{geo.direction(it,site,at)})!!
  val calc=SkyCalculation(site,at,target,target,geo.sky(site,at),emptyList(),emptyList(),emptyList(),sky)
  return SkyFrameSnapshot(projection,basis,null,false,night,calc,SkyLayers(false,false,false),stars,horizontalFov=fov)
 }
 @Test fun exactMinuteBoundaryCoordinatesShareTheOverlayProjectionAtBothZooms()=egl {
  val black=black();val renderer=SkyPipeline(fieldStars=listOf(entry));renderer.resize(W,H)
  try {
   for(second in listOf(0L,59L,60L))for(fov in listOf(20.0,70.0)) {
    val f=frame(Instant.parse("2026-10-26T20:00:00Z").plusSeconds(second),fov)
    val pixels=render(renderer,black,f,0f)
    val p=(GalacticOverlay.target(f.calculation!!.stars!!.positions.getValue(24436),f.projection) as TargetIndicator.Visible).pixel
    assertTrue("Field star aligns with precise HIP at :$second, FOV $fov",pixels.peak(p.x.roundToInt(),p.y.roundToInt())>140)
   }
  }finally{renderer.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 @Test fun locatedSkyShowsTheSameStarOnBothSidesOfTheHorizon()=egl {
  val black=black();val renderer=SkyPipeline(fieldStars=listOf(entry));renderer.resize(W,H)
  val f=frame(Instant.parse("2026-10-26T20:00:59Z"),20.0)
  try {
   val above=render(renderer,black,f.copy(up=CameraVector(0.0,0.0,1.0)),0f)
   val below=render(renderer,black,f.copy(up=CameraVector(0.0,0.0,-1.0)),0f)
   val point=(GalacticOverlay.target(f.calculation!!.stars!!.positions.getValue(24436),f.projection) as TargetIndicator.Visible).pixel
   val x=point.x.roundToInt();val y=point.y.roundToInt()
   assertTrue("Fixture contains a visible real star",above.peak(x,y)>140)
   assertEquals("Full sky map must retain below-horizon star brightness",above.peak(x,y),below.peak(x,y))
   assertArrayEquals(above.bytes,below.bytes)
  }finally{renderer.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 @Test fun fieldStarsAreStaticWhenDisabledAndNeverDoubleDrawPreciseMarkers()=egl {
  val black=black();val renderer=SkyPipeline(fieldStars=listOf(entry));val overlayOnly=SkyPipeline()
  renderer.resize(W,H);overlayOnly.resize(W,H)
  val f=frame(Instant.parse("2026-10-26T20:00:59Z"),20.0)
  try {
   val still=render(renderer,black,f,0f);assertArrayEquals(still.bytes,render(renderer,black,f,17f).bytes)
   val hidden=f.copy(starLayers=f.starLayers.copy(masterVisible=false))
   assertArrayEquals(render(overlayOnly,black,hidden,0f).bytes,render(renderer,black,hidden,17f).bytes)
   val marked=f.copy(starLayers=f.starLayers.copy(stars=true))
   assertArrayEquals("Precisely marked HIP is suppressed in the background VBO draw",render(overlayOnly,black,marked,0f).bytes,render(renderer,black,marked,0f).bytes)
   val blinking=f.copy(starLayers=f.starLayers.copy(twinkle=true));val a=render(renderer,black,blinking,1f);val b=render(renderer,black,blinking,13f)
   val point=(GalacticOverlay.target(f.calculation!!.stars!!.positions.getValue(24436),f.projection) as TargetIndicator.Visible).pixel
   val x=point.x.roundToInt();val y=point.y.roundToInt()
   assertTrue(abs(a.peak(x,y)-b.peak(x,y))>3)
   val ca=a.centroid(x,y);val cb=b.centroid(x,y);assertEquals(ca.first,cb.first,.12);assertEquals(ca.second,cb.second,.12)
   val night=render(renderer,black,blinking.copy(night=true),1f)
   assertTrue(night.peak(x,y)>night.peak(x,y,1)*4);assertTrue(night.peak(x,y)>night.peak(x,y,2)*6)
  }finally{renderer.release();overlayOnly.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 private fun render(renderer:SkyPipeline,black:Int,f:SkyFrameSnapshot,time:Float):Pixels {
  renderer.draw(black,black,f.basis,f.up,f.galaxy,f.night,f.horizontalFov,blur=false,frame=f,twinkleSeconds=time)
  val data=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);assertEquals(GL_NO_ERROR,glGetError())
  return Pixels(ByteArray(data.capacity()).also{data.get(it)})
 }
 private class Pixels(val bytes:ByteArray) {
  fun at(x:Int,y:Int,c:Int=0)=bytes[((H-1-y)*W+x)*4+c].toInt()and 255
  fun peak(x:Int,y:Int,c:Int=0)=(-2..2).flatMap{dy->(-2..2).map{dx->at(x+dx,y+dy,c)}}.max()
  fun centroid(x:Int,y:Int):Pair<Double,Double>{var sum=0.0;var sx=0.0;var sy=0.0;for(py in y-10..y+10)for(px in x-10..x+10){val weight=max(0,at(px,py)-5).toDouble();sum+=weight;sx+=px*weight;sy+=py*weight};return sx/sum to sy/sum}
 }
 private fun black():Int {
  val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLACK)}
  val ids=IntArray(1);glGenTextures(1,ids,0);glBindTexture(GL_TEXTURE_2D,ids[0]);GlassPipeline.parameters(GL_TEXTURE_2D);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();return ids[0]
 }
 private fun egl(block:()->Unit) {
  val d=eglGetDisplay(EGL_DEFAULT_DISPLAY);val version=IntArray(2);assertTrue(eglInitialize(d,version,0,version,1))
  val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1)
  assertTrue(eglChooseConfig(d,intArrayOf(EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,n,0))
  val c=eglCreateContext(d,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0)
  val s=eglCreatePbufferSurface(d,configs[0],intArrayOf(EGL_WIDTH,W,EGL_HEIGHT,H,EGL_NONE),0)
  try{assertTrue(eglMakeCurrent(d,s,s,c));block()}finally{eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(d,s);eglDestroyContext(d,c);eglTerminate(d)}
 }
 companion object{private const val W=256;private const val H=192}
}
