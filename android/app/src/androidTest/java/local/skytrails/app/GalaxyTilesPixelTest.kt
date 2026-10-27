package local.skytrails.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14.*
import android.opengl.GLES20.*
import android.opengl.GLUtils
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ui.sky.GalacticCameraBasis
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.*

class GalaxyTilesPixelTest {
 @Test fun realTilesMatchTheirSourceThroughLongitudeSeamAndBothPoles()=egl {
  val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
  val loader=GalaxyTileLoader(assets){};val tiles=GalaxyTilePipeline(loader);val renderer=SkyPipeline(fieldStars=emptyList(),tiles=tiles);renderer.resize(W,H)
  val bitmap=assets.open("sky/galaxy-fallback.jpg").use{BitmapFactory.decodeStream(it)}!!
  val ids=IntArray(1);glGenTextures(1,ids,0);glBindTexture(GL_TEXTURE_2D,ids[0]);GlassPipeline.parameters(GL_TEXTURE_2D);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle()
  val center=GalacticCameraBasis(CameraVector(0.0,0.0,1.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,1.0,0.0))
  val seam=center.copy(x=CameraVector(0.0,0.0,-1.0),y=CameraVector(1.0,0.0,0.0))
  val north=GalacticCameraBasis(CameraVector(0.0,-1.0,0.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,0.0,1.0))
  val south=GalacticCameraBasis(CameraVector(0.0,1.0,0.0),CameraVector(-1.0,0.0,0.0),CameraVector(0.0,0.0,-1.0))
  try {
   for(basis in listOf(center,seam,north,south)) {
    val deadline=System.nanoTime()+15_000_000_000L
    do{renderer.draw(ids[0],ids[0],basis,CameraVector(0.0,0.0,-1.0),true,false,20.0,blur=false);if(loader.idle)break;Thread.sleep(15)}while(System.nanoTime()<deadline)
    assertTrue("Tile work converges",loader.idle);assertNull(loader.lastError);assertNull(tiles.lastError);assertTrue(tiles.residentBytes<=GalaxyTiles.BUDGET_BYTES)
    val data=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);val bytes=ByteArray(data.capacity());data.get(bytes)
    val decoded=mutableMapOf<GalaxyTile,Bitmap>();val level=tiles.activeLevel;val focal=W/(2*tan(Math.toRadians(20.0)/2))
    try{for(y in listOf(48,96,144))for(x in listOf(64,127,128,192)) {
     val uv=basis.coordinate(CameraVector((x+.5-W/2)/focal,(H/2-y-.5)/focal,1.0))
     val key=GalaxyTile(level,(uv.u*level/512).toInt(),(uv.v*level/1024).toInt().coerceAtMost(level/1024-1))
     val source=decoded.getOrPut(key){assets.open(key.path).use{BitmapFactory.decodeStream(it)}!!}
     val sx=uv.u*level-key.x*512+.5;val sy=uv.v*level/2-key.y*512+.5;val ix=floor(sx).toInt();val iy=floor(sy).toInt();val fx=sx-ix;val fy=sy-iy
     for(c in 0..2) {
      fun channel(dx:Int,dy:Int)=((source.getPixel((ix+dx).coerceIn(0,513),(iy+dy).coerceIn(0,513)) ushr (16-c*8))and 255).toDouble()
      val sample=(channel(0,0)*(1-fx)+channel(1,0)*fx)*(1-fy)+(channel(0,1)*(1-fx)+channel(1,1)*fx)*fy
      val expected=(listOf(5,10,18)[c]+sample*.735).coerceAtMost(255.0)
      val actual=bytes[((H-1-y)*W+x)*4+c].toInt()and 255
      assertEquals("Exact source sample at $key/$x,$y/$c",expected,actual.toDouble(),4.0)
     }
    }}finally{decoded.values.forEach{it.recycle()}}
   }
  }finally{loader.pause();renderer.release();glDeleteTextures(1,ids,0)}
 }
 private fun egl(block:()->Unit) {
  val d=eglGetDisplay(EGL_DEFAULT_DISPLAY);val version=IntArray(2);assertTrue(eglInitialize(d,version,0,version,1))
  val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1)
  assertTrue(eglChooseConfig(d,intArrayOf(EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,n,0))
  val c=eglCreateContext(d,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0);val s=eglCreatePbufferSurface(d,configs[0],intArrayOf(EGL_WIDTH,W,EGL_HEIGHT,H,EGL_NONE),0)
  try{assertTrue(eglMakeCurrent(d,s,s,c));block()}finally{eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(d,s);eglDestroyContext(d,c);eglTerminate(d)}
 }
 companion object{private const val W=256;private const val H=192}
}
