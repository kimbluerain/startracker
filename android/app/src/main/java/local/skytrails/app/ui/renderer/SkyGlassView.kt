package local.skytrails.app.ui.renderer

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ui.sky.*
import local.skytrails.app.ui.SkyCalculation
import local.skytrails.app.ui.SkyLayers
import local.skytrails.app.ui.StarLayers
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class SkyFrameSnapshot(val projection:SkyProjection,val basis:GalacticCameraBasis,val up:CameraVector?,val galaxy:Boolean,val night:Boolean,val calculation:SkyCalculation?=null,val layers:SkyLayers=SkyLayers(),val starLayers:StarLayers=StarLayers(),val starFocusId:String?=null,val horizontalFov:Double=70.0)
/** Textures upload once per GL context; sensor updates only publish a small immutable snapshot. */
class SkyGlassView(context:Context):GLSurfaceView(context),GLSurfaceView.Renderer {
 val pending=AtomicReference<SkyFrameSnapshot?>(null)
 val presented=AtomicReference<SkyFrameSnapshot?>(null)
 @Volatile var masks:List<GlassMask> = emptyList()
  set(value){if(field!=value){field=value;requestRender()}}
 @Volatile var blurEnabled=true
  set(value){if(field!=value){field=value;requestRender()}}
 @Volatile var error:String?=null;private set
 @Volatile var frames=0L;private set
 @Volatile var blurPasses=0L;private set
 @Volatile var contextGeneration=0L;private set
 @Volatile var loadedFieldStarCount=0;private set
 @Volatile var fieldDecodeCount=0L;private set
 @Volatile var loadedTextureSizes:Map<String,Pair<Int,Int>> = emptyMap();private set
 @Volatile var tileResidentBytes=0L;private set
 @Volatile var activeTileLevel=2048;private set
 val tilePendingJobs get()=tileLoader?.pendingJobs?:0
 val tileDecodeCount get()=tileLoader?.decodeCount?:0L
 val tileIdle get()=tileLoader?.idle?:true
 val tileError get()=tileLoader?.lastError?:tilePipeline?.lastError
 @Volatile private var tileLoader:GalaxyTileLoader?=null
 private var tilePipeline:GalaxyTilePipeline?=null
 private var foreground=true
 private var pipeline:SkyPipeline?=null
 private var textures=IntArray(0)
 private var surfaceWidth=0;private var surfaceHeight=0
 init{setEGLContextClientVersion(2);preserveEGLContextOnPause=true;setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY}
 fun submit(frame:SkyFrameSnapshot){if(pending.getAndSet(frame)!==frame)requestRender()}
 override fun onPause(){foreground=false;tileLoader?.pause();super.onPause()}
 override fun onResume(){foreground=true;tileLoader?.resume();super.onResume()}
 override fun onDetachedFromWindow(){tileLoader?.pause();super.onDetachedFromWindow()}
 override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
  tileLoader?.pause();tileResidentBytes=0;activeTileLevel=2048
  contextGeneration++;presented.set(null);loadedTextureSizes=emptyMap();loadedFieldStarCount=0;textures=IntArray(1);glGenTextures(1,textures,0)
  try {
   val limit=IntArray(1);glGetIntegerv(GL_MAX_TEXTURE_SIZE,limit,0)
   val loaded=mutableMapOf<String,Pair<Int,Int>>()
   listOf("galaxy-fallback.jpg").forEachIndexed {i,name->
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    context.assets.open("sky/$name").use{BitmapFactory.decodeStream(it,null,bounds)}
    val sample=TextureSampling.sampleSize(bounds.outWidth,bounds.outHeight,limit[0])
    val bitmap=context.assets.open("sky/$name").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample;inScaled=false})} ?: error("Sky texture unavailable")
    try{glBindTexture(GL_TEXTURE_2D,textures[i]);GlassPipeline.parameters(GL_TEXTURE_2D);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);check(glGetError()==GL_NO_ERROR){"Sky texture upload failed: $name"};loaded[name]=bitmap.width to bitmap.height}finally{bitmap.recycle()}
   }
   val stars=context.assets.open("sky/field-stars.bin").use{FieldStars.read(it)};fieldDecodeCount++
   val loader=tileLoader?:GalaxyTileLoader(context.assets){requestRender()};if(foreground)loader.resume()else loader.pause()
   tileLoader=loader;tilePipeline=GalaxyTilePipeline(loader)
   pipeline=SkyPipeline(context.resources.displayMetrics.density,stars,tilePipeline);loadedFieldStarCount=stars.size;loadedTextureSizes=loaded.toMap();error=null
  }catch(e:Exception){error=e.message;pipeline=null}
 }
 override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){surfaceWidth=width;surfaceHeight=height;pipeline?.resize(width,height)}
 override fun onDrawFrame(gl:GL10?) {
  val frame=pending.get();val renderer=pipeline
  if(frame==null||renderer==null||frame.projection.width!=surfaceWidth||frame.projection.height!=surfaceHeight){glClearColor(.025f,.035f,.05f,1f);glClear(GL_COLOR_BUFFER_BIT);return}
  renderer.draw(textures[0],textures[0],frame.basis,frame.up,frame.galaxy,frame.night,horizontalFov=frame.horizontalFov,masks=masks,blur=blurEnabled,frame=frame,twinkleSeconds=StarTwinkle.seconds())
  presented.set(frame);frames++;blurPasses=renderer.blurPasses;tileResidentBytes=tilePipeline?.residentBytes?:0;activeTileLevel=tilePipeline?.activeLevel?:2048
 }
}
