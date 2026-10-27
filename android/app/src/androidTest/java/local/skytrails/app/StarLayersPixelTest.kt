package local.skytrails.app

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14.*
import android.opengl.GLES20.*
import android.opengl.GLUtils
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.sky.SkyProjection
import java.time.Instant
import java.time.ZoneId
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ui.sky.GalacticCameraBasis
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.*

/** Actual framebuffer evidence for the three independently controlled star layers. */
class StarLayersPixelTest {
 @Test fun twinkleChangesCatalogBrightnessWithoutMovingItsPixelFootprint()=egl {
  val black=solid(Color.BLACK);val renderer=SkyPipeline();renderer.resize(W,H)
  val at=Instant.EPOCH;val site=Site(0.0,0.0,0.0,0.0,ZoneId.of("UTC"));val direction=SkyDirection(12.0,18.0)
  val sky=StarSky(site,at,mapOf(24436 to direction),mapOf("orion" to direction))
  val c=SkyCalculation(site,at,direction,direction,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),direction),emptyList(),emptyList(),emptyList(),sky)
  val projection=SkyProjection.virtual(W,H,90.0,0.0,0.0)
  val frame=SkyFrameSnapshot(projection,NORTH,null,false,false,c,SkyLayers(false,false,false),StarLayers(true,false,false,setOf("orion"),twinkle=true))
  fun render(time:Float,layers:StarLayers=frame.starLayers):Pixels {renderer.draw(black,black,NORTH,null,false,false,90.0,blur=false,frame=frame.copy(starLayers=layers),twinkleSeconds=time);return read()}
  try {
   val a=render(0f);val b=render(1f)
   val x=(W/2.0+W/2.0*tan(Math.toRadians(12.0))).roundToInt();val y=(H/2.0-W/2.0*tan(Math.toRadians(18.0))/cos(Math.toRadians(12.0))).roundToInt()
   assertTrue("Catalog marker brightness must change",abs(a.at(x,y,0)-b.at(x,y,0))>5)
   fun center(p:Pixels):Pair<Double,Double> {var total=0.0;var sx=0.0;var sy=0.0
    for(py in y-8..y+8)for(px in x-8..x+8){val weight=max(0,p.at(px,py,0)-5).toDouble();total+=weight;sx+=px*weight;sy+=py*weight};return sx/total to sy/total}
   val ca=center(a);val cb=center(b);assertEquals(ca.first,cb.first,.08);assertEquals(ca.second,cb.second,.08)
   val steady=frame.starLayers.copy(twinkle=false);assertArrayEquals(render(0f,steady).bytes,render(1f,steady).bytes)
   val hidden=frame.starLayers.copy(masterVisible=false);assertArrayEquals(render(0f,hidden).bytes,render(1f,hidden).bytes)
   assertTrue(render(0f,hidden).at(x,y,0)<10)
  }finally{renderer.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 @Test fun crossSpikesExtendAlongFourAxesFadeAndRespectMasksNightAndVisibility()=egl {
  val black=solid(Color.BLACK);val renderer=SkyPipeline();renderer.resize(W,H)
  val at=Instant.EPOCH;val site=Site(0.0,0.0,0.0,0.0,ZoneId.of("UTC"))
  val active=StarLayers(true,false,false,setOf("orion"),twinkle=true)
  fun render(time:Float=3f,layers:StarLayers=active,night:Boolean=false,below:Boolean=false,masks:List<GlassMask> = emptyList()):Pixels {
   val direction=SkyDirection(0.0,if(below)-18.0 else 18.0)
   val stars=StarSky(site,at,mapOf(24436 to direction),mapOf("orion" to direction))
   val c=SkyCalculation(site,at,direction,direction,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),direction),emptyList(),emptyList(),emptyList(),stars)
   val projection=SkyProjection.virtual(W,H,90.0,0.0,direction.altitudeDeg)
   val frame=SkyFrameSnapshot(projection,NORTH,null,false,night,c,SkyLayers(false,false,false),layers)
   renderer.draw(black,black,NORTH,null,false,night,90.0,masks=masks,blur=false,frame=frame,twinkleSeconds=time)
   return read()
  }
  val x=W/2;val y=H/2
  fun peak(p:Pixels,dx:Int,dy:Int,channel:Int=0)=(-1..1).flatMap{oy->(-1..1).map{ox->p.at(x+dx+ox,y+dy+oy,channel)}}.max()
  fun centroid(p:Pixels):Pair<Double,Double> {var sum=0.0;var sx=0.0;var sy=0.0
   for(py in y-16..y+15)for(px in x-16..x+15){val weight=p.at(px,py,0).toDouble();sum+=weight;sx+=px*weight;sy+=py*weight};return sx/sum to sy/sum}
  try {
   val off=render(layers=active.copy(twinkle=false));val on=render();val blank=render(layers=active.copy(stars=false))
   val arms=listOf(8 to 0,-8 to 0,0 to 8,0 to -8)
   val diagonal=peak(on,8,8)-peak(off,8,8)
   arms.forEach{(dx,dy)->assertTrue("Four-axis spike at $dx,$dy must extend beyond static halo",peak(on,dx,dy)-peak(off,dx,dy)>diagonal+3)}
   assertTrue("The outer tip must fade",peak(on,8,0)>peak(on,14,0)+3)
   val a=centroid(on);val b=centroid(render(time=1f))
   assertEquals("Visual time must not move x",a.first,b.first,.08);assertEquals("Visual time must not move y",a.second,b.second,.08)
   assertEquals(x-.5,a.first,.08);assertEquals(y-.5,a.second,.08)
   assertArrayEquals(off.bytes,render(time=1f,layers=active.copy(twinkle=false)).bytes)
   for(hidden in listOf(active.copy(masterVisible=false),active.copy(stars=false),active.copy(enabled=emptySet()))) {
    val image=render(layers=hidden);assertEquals(peak(blank,0,0),peak(image,0,0));arms.forEach{(dx,dy)->assertEquals(peak(blank,dx,dy),peak(image,dx,dy))}
   }
   val masked=render(masks=listOf(GlassMask((x+7).toFloat(),(y-3).toFloat(),(x+17).toFloat(),(y+3).toFloat(),0f)))
   assertEquals("Mask clips an outer arm even when its core is outside",peak(blank,8,0),peak(masked,8,0));assertEquals(peak(on,0,0),peak(masked,0,0))
   val red=render(night=true);assertTrue(peak(red,8,0)>peak(red,8,0,1)*1.8);assertTrue(peak(red,8,0)>peak(red,8,0,2)*2)
   assertTrue("Below-horizon spikes must dim",peak(render(below=true),8,0)<peak(on,8,0))
  }finally{renderer.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 @Test fun markersLinesAndNamesChangeOnlyTheirOwnGeometryAndShareMovingFrame()=egl {
  val black=solid(Color.BLACK);val renderer=SkyPipeline();renderer.resize(W,H)
  val at=Instant.parse("2026-10-26T12:00:00Z");val site=Site(30.0,120.0,0.0,0.0,ZoneId.of("UTC"))
  val right=SkyDirection(12.0,8.0);val left=SkyDirection(-12.0,8.0);val center=SkyDirection(0.0,25.0)
  val stars=StarSky(site,at,mapOf(24436 to right,27366 to left),mapOf("orion" to center))
  val c=SkyCalculation(site,at,right,right,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),right),emptyList(),emptyList(),emptyList(),stars)
  val off=StarLayers(false,false,false,setOf("orion"))
  fun render(heading:Double,layers:StarLayers,night:Boolean=false,masks:List<GlassMask> = emptyList()):Pixels {
   val projection=SkyProjection.virtual(W,H,90.0,heading,0.0)
   val frame=SkyFrameSnapshot(projection,NORTH,null,false,night,c,SkyLayers(false,false,false),layers)
   renderer.draw(black,black,NORTH,null,false,night,90.0,masks=masks,blur=false,frame=frame)
   return read()
  }
  fun peak(image:Pixels,x:Int,y:Int)=(-2..2).flatMap{dy->(-2..2).map{dx->image.at(x+dx,y+dy,0)}}.max()
  try {
   repeat(12){i->
    val heading=if(i%2==0)-7.0 else 4.0
    val d=Math.toRadians(12.0-heading);val h=Math.toRadians(8.0)
    val x=(W/2.0+W/2.0*tan(d)).roundToInt();val y=(H/2.0-W/2.0*tan(h)/cos(d)).roundToInt()
    val blank=render(heading,off);val on=render(heading,off.copy(stars=true))
    assertTrue("Star marker must follow current frame $i",peak(on,x,y)>peak(blank,x,y)+100)
    val masked=render(heading,off.copy(stars=true),masks=listOf(GlassMask((x-8).toFloat(),(y-8).toFloat(),(x+8).toFloat(),(y+8).toFloat(),2f)))
    assertEquals(peak(blank,x,y),peak(masked,x,y))
   }
   val blank=render(0.0,off);val lines=render(0.0,off.copy(lines=true))
   val lineY=(H/2.0-W/2.0*tan(Math.toRadians(8.0))/cos(Math.toRadians(12.0))).roundToInt()
   assertTrue("Connection visible with star markers disabled",peak(lines,W/2,lineY)>peak(blank,W/2,lineY)+25)
   val names=render(0.0,off.copy(names=true));var changed=0
   for(y in 50..70)for(x in 140..188)if(names.at(x,y,0)>blank.at(x,y,0)+20)changed++
   assertTrue("Real cached Chinese label pixels with lines/markers disabled: $changed",changed>30)
   val hidden=render(0.0,off.copy(stars=true,lines=true,names=true,enabled=emptySet()))
   assertArrayEquals(blank.bytes,hidden.bytes)
   val red=render(0.0,off.copy(stars=true),night=true)
   val x=(W/2.0+W/2.0*tan(Math.toRadians(12.0))).roundToInt();val y=(H/2.0-W/2.0*tan(Math.toRadians(8.0))/cos(Math.toRadians(12.0))).roundToInt()
   assertTrue(red.at(x,y,0)>red.at(x,y,1)*1.8);assertTrue(red.at(x,y,0)>red.at(x,y,2)*2)
  } finally {renderer.release();glDeleteTextures(1,intArrayOf(black),0)}
 }
 private fun solid(color:Int)=upload(Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888).apply{eraseColor(color)})
 private fun upload(bitmap:Bitmap):Int {val ids=IntArray(1);glGenTextures(1,ids,0);glBindTexture(GL_TEXTURE_2D,ids[0]);GlassPipeline.parameters(GL_TEXTURE_2D);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();return ids[0]}
 private class Pixels(val bytes:ByteArray){fun at(x:Int,y:Int,c:Int)=bytes[((H-1-y)*W+x)*4+c].toInt()and 255;fun variance():Double{val values=(80..136).flatMap{y->(60..124).map{x->at(x,y,2).toDouble()}};val avg=values.average();return values.sumOf{(it-avg).pow(2)}/values.size}}
 private fun read():Pixels{val buffer=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,buffer);assertEquals(GL_NO_ERROR,glGetError());return Pixels(ByteArray(buffer.capacity()).also{buffer.get(it)})}
 private fun egl(block:()->Unit) {
  val d=eglGetDisplay(EGL_DEFAULT_DISPLAY);val version=IntArray(2);assertTrue(eglInitialize(d,version,0,version,1))
  val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1)
  assertTrue(eglChooseConfig(d,intArrayOf(EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,n,0))
  val c=eglCreateContext(d,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0)
  val s=eglCreatePbufferSurface(d,configs[0],intArrayOf(EGL_WIDTH,W,EGL_HEIGHT,H,EGL_NONE),0)
  try{assertTrue(eglMakeCurrent(d,s,s,c));block()}finally{eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(d,s);eglDestroyContext(d,c);eglTerminate(d)}
 }
 companion object{private const val W=256;private const val H=192;private val NORTH=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,1.0,0.0))}
}
