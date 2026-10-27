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

/** Actual 2D texture -> sky FBO -> existing blur pipeline pixels, independent of OES. */
class SkyPipelinePixelTest {
 @Test fun asymmetricLongitudeLatitudeFixtureHasCorrectOriginAtFourRotationsAndRoll()=egl {
  val stars=texture(false);val black=solid(Color.BLACK);val p=SkyPipeline();p.resize(W,H)
  try {
   for(angle in listOf(0.0,90.0,180.0,270.0,30.0)) {
    val a=Math.toRadians(angle);val c=cos(a);val s=sin(a)
    val axes=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-c,-s,0.0),CameraVector(-s,c,0.0))
    p.draw(stars,black,axes,null,false,false,90.0,blur=false)
    val image=read()
    for((x,y)in listOf(64 to 48,184 to 60,100 to 140,208 to 132)) {
     // Independent inverse display ray. R encodes increasing u; G encodes southward v.
     val dx=(x+.5-W/2.0)/(W/2.0);val dy=(H/2.0-y-.5)/(W/2.0)
     val horizontal=c*dx+s*dy;val vertical=-s*dx+c*dy
     val u=.5+atan2(horizontal,1.0)/(2*PI)
     val v=.5-asin(vertical/sqrt(dx*dx+dy*dy+1))/PI
     assertEquals("R rotation=$angle at $x,$y",5+(20+u*160)*.665,image.at(x,y,0).toDouble(),3.0)
     assertEquals("G rotation=$angle at $x,$y",10+(20+v*160)*.665,image.at(x,y,1).toDouble(),3.0)
    }
   }
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,black),0)}
 }
 @Test fun starMasterAndTwinkleAffectPhotographicStarsButNeverDiffuseGalaxy()=egl {
  val stars=solid(Color.rgb(180,180,180));val diffuse=solid(Color.rgb(90,60,45));val p=SkyPipeline();p.resize(W,H)
  fun render(visible:Boolean,galaxy:Boolean,time:Float,twinkle:Boolean):Pixels {
   p.draw(stars,diffuse,NORTH,null,galaxy,false,90.0,blur=false,starsVisible=visible,twinkleSeconds=time,twinkle=twinkle)
   return read()
  }
  try {
   val off=render(false,false,0f,true);val starsOnly=render(true,false,0f,false)
   assertEquals(5,off.at(100,70,0));assertEquals(10,off.at(100,70,1));assertEquals(18,off.at(100,70,2))
   assertTrue(starsOnly.at(100,70,0)>off.at(100,70,0)+100)
   assertArrayEquals(starsOnly.bytes,render(true,false,13f,false).bytes)
   assertArrayEquals(off.bytes,render(false,false,13f,true).bytes)
   val galaxyOnly=render(false,true,0f,true)
   assertTrue(galaxyOnly.at(100,70,0)>off.at(100,70,0)+50)
   assertArrayEquals(galaxyOnly.bytes,render(false,true,13f,true).bytes)
   val a=render(true,false,0f,true);val b=render(true,false,1.3f,true)
   var changed=0;for(y in 0 until H step 4)for(x in 0 until W step 4)if(abs(a.at(x,y,0)-b.at(x,y,0))>4)changed++
   assertTrue("Many independently seeded celestial cells must brighten or dim",changed>W*H/64)
   assertTrue("Neighbouring cells should have different phase",(0 until W step 8).map{a.at(it,H/2,0)}.toSet().size>5)
   var brighter=0;var dimmer=0
   for(y in 0 until H step 4)for(x in 0 until W step 4) {
    val static=starsOnly.at(x,y,0)
    if(a.at(x,y,0)>static+12)brighter++
    if(a.at(x,y,0)<static-12)dimmer++
   }
   assertTrue("Photographic stars must glow above static brightness, not only fade",brighter>W*H/128)
   assertTrue("Independent phases retain a softer dim part of the cycle",dimmer>W*H/128)
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,diffuse),0)}
 }
 @Test fun fallbackSkyRetainsItsBrightnessBelowTheHorizon()=egl {
  val stars=solid(Color.rgb(80,80,80));val diffuse=solid(Color.rgb(90,60,45));val p=SkyPipeline();p.resize(W,H)
  try {
   p.draw(stars,diffuse,NORTH,null,true,false,90.0,blur=false);val full=read()
   p.draw(stars,diffuse,NORTH,CameraVector(0.0,0.0,-1.0),true,false,90.0,blur=false);val below=read()
   assertTrue("Fixture contains star and diffuse pixels",full.at(100,70,0)>100)
   assertEquals("Fallback must remain visible below horizon",full.at(100,70,0),below.at(100,70,0))
   assertArrayEquals(full.bytes,below.bytes)
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,diffuse),0)}
 }
 @Test fun galaxyOffRemovesDiffusePixelsAndNightRemovesBlueGreen()=egl {
  val stars=solid(Color.rgb(20,30,40));val diffuse=solid(Color.rgb(120,80,60));val p=SkyPipeline();p.resize(W,H)
  try {
   p.draw(stars,diffuse,NORTH,null,true,false,90.0,blur=false);val on=read()
   p.draw(stars,diffuse,NORTH,null,false,false,90.0,blur=false);val off=read()
   assertTrue(on.at(100,70,0)>off.at(100,70,0)+70)
   assertEquals(5+20*.665,off.at(100,70,0).toDouble(),2.0)
   p.draw(stars,diffuse,NORTH,null,true,true,90.0,blur=false);val red=read()
   for(y in 0 until H step 8)for(x in 0 until W step 8){assertTrue(red.at(x,y,0)>red.at(x,y,1)*5);assertTrue(red.at(x,y,0)>red.at(x,y,2)*7)}
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,diffuse),0)}
 }
 @Test fun offCenterBlurLowersVarianceWithoutReflectingOrChangingUnmaskedPixels()=egl {
  val stars=texture(true);val black=solid(Color.BLACK);val p=SkyPipeline();p.resize(W,H)
  try {
   p.draw(stars,black,NORTH,null,false,false,90.0,blur=false);val sharp=read()
   p.draw(stars,black,NORTH,null,false,false,90.0,listOf(GlassMask(40f,60f,145f,158f,8f)),true);val soft=read()
   assertEquals(2L,p.blurPasses)
   for(y in 0 until H)for(x in 0 until W)if(x<40||x>=145||y<60||y>=158)for(ch in 0..3)assertEquals(sharp.at(x,y,ch),soft.at(x,y,ch))
   for((x,y)in listOf(70 to 90,115 to 125))for(ch in 0..1)assertEquals(sharp.at(x,y,ch).toDouble(),soft.at(x,y,ch).toDouble(),3.0)
   assertTrue("Actual masked texture loses checker contrast",soft.variance()<sharp.variance()*.45)
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,black),0)}
 }
 @Test fun longitudeSeamAndNorthPoleKeepCorrectLatitudeAndNoBlackHoles()=egl {
  val stars=texture(false);val black=solid(Color.BLACK);val p=SkyPipeline();p.resize(W,H)
  try {
   val seam=GalacticCameraBasis(CameraVector(0.0,0.0,-1.0),CameraVector(1.0,0.0,0.0),CameraVector(0.0,1.0,0.0))
   p.draw(stars,black,seam,null,false,false,90.0,blur=false);val edge=read()
   assertTrue(edge.at(W/2-10,H/2,0)>edge.at(W/2+10,H/2,0)+80)
   val pole=GalacticCameraBasis(CameraVector(0.0,-1.0,0.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,0.0,1.0))
   p.draw(stars,black,pole,null,false,false,90.0,blur=false);val top=read()
   assertTrue(top.at(W/2,H/2,1)in 20..30);assertTrue(top.at(W/2,H/2,0)>15)
  }finally{p.release();glDeleteTextures(2,intArrayOf(stars,black),0)}
 }
 @Test fun aFreshContextRecreatesIdenticalSkyAndBlurPixels() {
  fun render():ByteArray {var bytes=byteArrayOf();egl{val a=texture(true);val b=solid(Color.BLACK);val p=SkyPipeline();p.resize(W,H);p.draw(a,b,NORTH,null,false,true,90.0,listOf(GlassMask(40f,60f,145f,158f,8f)),true);bytes=read().bytes;p.release();glDeleteTextures(2,intArrayOf(a,b),0)};return bytes}
  assertArrayEquals(render(),render())
 }
 @Test fun alternatingPosesCompositePhotographicAnchorAndNoncentralMarkerInSamePixels()=egl {
  val bitmap=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLACK)}
  // Artificial source landmark at Galactic l=-12, b=+8. It is asymmetric/off center.
  val u=.5+12.0/360;val v=.5-8.0/180
  for(y in 0 until 512)for(x in 0 until 1024)if(abs(x/1024.0-u)<.025&&abs(y/512.0-v)<.025)bitmap.setPixel(x,y,Color.BLUE)
  val stars=upload(bitmap);val black=solid(Color.BLACK);val pipeline=SkyPipeline();pipeline.resize(W,H)
  val at=Instant.EPOCH;val site=Site(30.0,120.0,0.0,0.0,ZoneId.of("UTC"));val target=SkyDirection(12.0,8.0)
  val calc=SkyCalculation(site,at,target,target,GalacticSky(site,at,emptyList(),emptyList(),emptyList(),target),emptyList(),emptyList(),emptyList())
  try {
   repeat(24){i->
    val fov=listOf(20.0,40.0,70.0,100.0)[i%4]
    val heading=12.0-fov*.15;val pitch=8.0-fov*.08
    val projection=SkyProjection.virtual(W,H,fov,heading,pitch)
    val basis=GalacticCameraBasis.from(projection,listOf(SkyDirection(0.0,0.0),SkyDirection(270.0,0.0),SkyDirection(0.0,90.0)))!!
    val snapshot=SkyFrameSnapshot(projection,basis,null,false,false,calc,SkyLayers(true,false,false),horizontalFov=fov)
    pipeline.draw(stars,black,basis,null,false,false,fov,blur=false,frame=snapshot)
    val pixels=read()
    // Independent ENU trigonometry, not the production projection or overlay helper.
    val d=Math.toRadians(12.0-heading);val h=Math.toRadians(8.0);val p=Math.toRadians(pitch)
    val z=sin(h)*sin(p)+cos(h)*cos(d)*cos(p)
    val focal=W/(2*tan(Math.toRadians(fov)/2))
    val x=(W/2.0+focal*cos(h)*sin(d)/z).roundToInt();val y=(H/2.0-focal*(sin(h)*cos(p)-cos(h)*cos(d)*sin(p))/z).roundToInt()
    assertTrue("Frame $i contains gold overlay at the new off-center anchor",pixels.at(x,y,0)>210&&pixels.at(x,y,1)>175)
    assertTrue("Frame $i blue photographic landmark surrounds that same anchor",pixels.at(x,y+8,2)>145&&pixels.at(x,y+8,0)<90)
    // A hidden Sun must remove the positional marker, without changing the photo landmark.
    pipeline.draw(stars,black,basis,null,false,false,fov,blur=false,frame=snapshot.copy(layers=SkyLayers(false,false,false)))
    val hidden=read();assertTrue(hidden.at(x,y,2)>160);assertTrue(hidden.at(x,y,0)<20)
    if(i==0){
     pipeline.draw(stars,black,basis,null,false,false,fov,masks=listOf(GlassMask((x-16).toFloat(),(y-16).toFloat(),(x+16).toFloat(),(y+16).toFloat(),4f)),blur=false,frame=snapshot)
     val masked=read();for(channel in 0..2)assertEquals("Mask excludes precise overlay while retaining its background",hidden.at(x,y,channel),masked.at(x,y,channel))
     pipeline.draw(stars,black,basis,null,false,true,fov,blur=false,frame=snapshot.copy(night=true))
     val night=read();assertTrue("Night marker remains red in final composite",night.at(x,y,0)>night.at(x,y,1)*1.8&&night.at(x,y,0)>night.at(x,y,2)*2)
    }
   }
  }finally{pipeline.release();glDeleteTextures(2,intArrayOf(stars,black),0)}
 }
 private fun texture(checker:Boolean):Int {
  val bitmap=Bitmap.createBitmap(512,256,Bitmap.Config.ARGB_8888)
  for(y in 0 until 256)for(x in 0 until 512)bitmap.setPixel(x,y,Color.rgb(20+x*160/512,20+y*160/256,if(checker&&(x/2+y/2)%2==0)255 else 0))
  return upload(bitmap)
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
