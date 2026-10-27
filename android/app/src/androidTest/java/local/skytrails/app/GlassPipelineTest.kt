package local.skytrails.app
import android.opengl.EGL14.*
import android.opengl.GLES20.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ar.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** Readback is confined to instrumentation. Uses the exact production draw/blur shaders. */
@RunWith(AndroidJUnit4::class)
class GlassPipelineTest {
 @Test fun ordinaryBottomOriginTextureRemainsAlignedWhileOnlyMaskedHighFrequenciesBlur() {
  val display=eglGetDisplay(EGL_DEFAULT_DISPLAY);val version=IntArray(2);assertTrue(eglInitialize(display,version,0,version,1))
  val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
  assertTrue(eglChooseConfig(display,intArrayOf(EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,count,0))
  var context=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0)
  val surface=eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL_WIDTH,128,EGL_HEIGHT,128,EGL_NONE),0)
  assertTrue(eglMakeCurrent(display,surface,surface,context))
  val texture=IntArray(1);glGenTextures(1,texture,0);glBindTexture(GL_TEXTURE_2D,texture[0]);GlassPipeline.parameters(GL_TEXTURE_2D)
  val pixels=ByteBuffer.allocateDirect(128*128*4)
  // This fixture deliberately uploads its bottom row first. Native Canvas/OES buffers
  // have the opposite origin; their independent test is NativeOesPipelineTest.
  for(row in 0..127)for(x in 0..127){val y=127-row;val rgb=when{ x<16&&y<16->intArrayOf(255,0,0);x>=112&&y<16->intArrayOf(0,255,0);x<16&&y>=112->intArrayOf(0,0,255);x>=112&&y>=112->intArrayOf(255,255,0);else->{val a=if((x/4+y/4)%2==0)255 else 0;intArrayOf(a,a,a)}};rgb.forEach{pixels.put(it.toByte())};pixels.put(255.toByte())};pixels.position(0)
  glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,128,128,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)
  var pipeline=GlassPipeline();pipeline.resize(128,128)
  fun read():ByteArray{val b=ByteBuffer.allocateDirect(128*128*4);glReadPixels(0,0,128,128,GL_RGBA,GL_UNSIGNED_BYTE,b);return ByteArray(b.capacity()).also{b.get(it)}}
  fun color(data:ByteArray,x:Int,y:Int,c:Int)=data[((127-y)*128+x)*4+c].toInt()and 255
  try {
   pipeline.draw(texture[0],GL_TEXTURE_2D,GlassPipeline.FLIP,emptyList(),false);val clear=read()
   assertTrue(color(clear,8,8,0)>240);assertTrue(color(clear,8,8,1)<10)
   pipeline.draw(texture[0],GL_TEXTURE_2D,GlassPipeline.FLIP,listOf(GlassMask(32f,32f,96f,96f,8f)),true);val blurred=read()
   assertNull(pipeline.fallback);assertEquals(2L,pipeline.blurPasses)
   for(y in 0..20)for(x in 0..127)for(c in 0..2)assertEquals(color(clear,x,y,c),color(blurred,x,y,c))
   fun variance(d:ByteArray):Double{val v=(45..80).flatMap{y->(45..80).map{x->color(d,x,y,0).toDouble()}};val m=v.average();return v.sumOf{(it-m)*(it-m)}/v.size}
   assertTrue("Masked checkerboard must lose high-frequency contrast",variance(blurred)<variance(clear)*.25)
   pipeline.draw(texture[0],GL_TEXTURE_2D,GlassPipeline.FLIP,listOf(GlassMask(32f,32f,96f,96f,8f)),false);assertEquals(2L,pipeline.blurPasses);assertArrayEquals(clear,read())
   val preview=PreviewGeometry.create(1,128,128,128,128,90,0)
   // Explicit adapter from native top-origin sampling to this ordinary bottom-origin texture.
   val t=(Mat3.scaleTranslate(1.0,-1.0,0.0,1.0)*GlassSampling.viewToSampler(preview,GlassSampling.predicted(90))*Mat3.scaleTranslate(128.0,128.0)).values()
   val matrix=floatArrayOf(t[0].toFloat(),t[3].toFloat(),0f,0f,t[1].toFloat(),t[4].toFloat(),0f,0f,0f,0f,1f,0f,t[2].toFloat(),t[5].toFloat(),0f,1f)
   pipeline.draw(texture[0],GL_TEXTURE_2D,matrix,emptyList(),false);val rotated=read()
   assertTrue("Quarter-turn top-left is original bottom-left blue",color(rotated,8,8,2)>240)
   assertTrue(color(rotated,120,8,0)>240)
   assertEquals(GL_NO_ERROR,glGetError())
   // Invalid allocation is rejected; reallocation recovers ordinary preview and material.
   pipeline.resize(0,0);assertNotNull(pipeline.fallback)
   pipeline.resize(128,128);assertNull(pipeline.fallback)
   assertThrows(IllegalStateException::class.java){GlassPipeline.compileProgram("attribute vec4 p; void main(){gl_Position=p;}","invalid fragment shader")}
   // Destroy the actual EGL context, rebuild all GPU resources, then verify the same image.
   pipeline.release();glDeleteTextures(1,texture,0)
   eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroyContext(display,context)
   context=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0)
   assertTrue(eglMakeCurrent(display,surface,surface,context))
   glGenTextures(1,texture,0);glBindTexture(GL_TEXTURE_2D,texture[0]);GlassPipeline.parameters(GL_TEXTURE_2D);pixels.position(0)
   glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,128,128,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)
   pipeline=GlassPipeline();pipeline.resize(128,128);pipeline.draw(texture[0],GL_TEXTURE_2D,GlassPipeline.FLIP,emptyList(),false)
   assertArrayEquals(clear,read());assertEquals(GL_NO_ERROR,glGetError())
  } finally {pipeline.release();glDeleteTextures(1,texture,0);eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(display,surface);eglDestroyContext(display,context);eglTerminate(display)}
 }
}
