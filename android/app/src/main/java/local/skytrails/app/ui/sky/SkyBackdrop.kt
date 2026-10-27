package local.skytrails.app.ui.sky

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import local.skytrails.app.ui.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.core.*
import java.time.Instant
import kotlin.math.*

private data class Panorama(val width:Int,val height:Int,val pixels:IntArray)
private class SkyPanoramas(val stars:Panorama,val galaxy:Panorama)
private object PanoramaCache {
 @Volatile var value:SkyPanoramas?=null
 fun load(context:android.content.Context):SkyPanoramas = value ?: synchronized(this) {
  value ?: run {
   fun read(name:String):Panorama {
    val bitmap=context.assets.open("sky/$name").use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=2})} ?: error("Sky texture could not be decoded")
    val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    val result=Panorama(bitmap.width,bitmap.height,pixels);bitmap.recycle();return result
   }
   SkyPanoramas(read("bright-stars-panorama.jpg"),read("milkyway-panorama.jpg")).also {value=it}
  }
 }
}

/** Photographic data are sampled on the sphere, never stretched as a fixed screen wallpaper. */
@Composable fun SkyBackdrop(site:Site?,at:Instant?,modifier:Modifier=Modifier,azimuth:Float=235f,altitude:Float=30f,galaxy:Boolean=true,night:Boolean=false,horizontalFov:Double=70.0,exposure:Float=1f,starsVisible:Boolean=true) {
 val context=LocalContext.current.applicationContext
 val panoramas by produceState<SkyPanoramas?>(null) {value=withContext(Dispatchers.IO){runCatching{PanoramaCache.load(context)}.getOrNull()}}
 // Quantize only the photographic backdrop to a minute; numerical target anchors retain exact time.
 val minute=at?.epochSecond?.div(60)
 val directionRequest=SkyDirectionRequest(site,minute)
 val directions by produceState<RequestedSkyResult<SkyDirectionRequest,List<SkyDirection>>?>(null,directionRequest) {
  val points=if(site!=null && minute!=null)withContext(Dispatchers.Default) {
   val geometry=MilkyWayGeometry();val time=Instant.ofEpochSecond(minute*60)
   listOf(GalacticCoordinate(0.0,0.0),GalacticCoordinate(90.0,0.0),GalacticCoordinate(0.0,90.0)).map{geometry.direction(it,site,time)}
  } else emptyList()
  value=RequestedSkyResult(directionRequest,points)
 }
 val currentDirections=directions?.forRequest(directionRequest)
 BoxWithConstraints(modifier.background(if(night)Color(0xFF150706) else Color(0xFF080F19))) {
  val ratio=maxHeight.value/maxWidth.value.coerceAtLeast(1f)
  val width=480;val height=(width*ratio).roundToInt().coerceIn(1,1600)
  val request=SkyRenderRequest(directionRequest,azimuth,altitude,horizontalFov,width,height,galaxy,night,exposure,panoramas,starsVisible)
  val basis=remember(directionRequest,currentDirections,azimuth,altitude,horizontalFov,width,height) {
   if(site==null)GalacticCameraBasis.UNLOCATED else currentDirections?.let{GalacticCameraBasis.from(SkyProjection.virtual(width,height,horizontalFov,azimuth.toDouble(),altitude.toDouble()),it)}
  }
  val backdrop by produceState<RequestedSkyResult<SkyRenderRequest,Bitmap>?>(null,request,basis) {
   val maps=panoramas ?: return@produceState
   val axes=basis ?: return@produceState
   val image=withContext(Dispatchers.Default) {
    val out=IntArray(width*height);val f=width/(2*tan(Math.toRadians(horizontalFov)/2))
    fun sample(map:Panorama,u:Double,v:Double):Int {
     val x=(u*map.width).toInt().coerceIn(0,map.width-1);val y=(v*map.height).toInt().coerceIn(0,map.height-1)
     return map.pixels[y*map.width+x]
    }
    for(y in 0 until height) {
     if(y%16==0)currentCoroutineContext().ensureActive()
     for(x in 0 until width) {
      val ray=CameraVector((x+.5-width/2.0)/f,(height/2.0-y-.5)/f,1.0)
      val uv=axes.coordinate(ray);val star=if(starsVisible)sample(maps.stars,uv.u,uv.v)else 0;val cloud=if(galaxy)sample(maps.galaxy,uv.u,uv.v)else 0
      out[y*width+x]=composeSkyPixel(star,cloud,galaxy,1.0,night,exposure.toDouble())
     }
    }
    Bitmap.createBitmap(out,width,height,Bitmap.Config.ARGB_8888)
   }
   value=RequestedSkyResult(request,image)
  }
  SkyBackdropImage(request,if(basis!=null)backdrop else null,Modifier.fillMaxSize())
 }
}

/** A pending/replaced request clears both visible photo and its shared glass source. */
@Composable fun SkyBackdropImage(expected:SkyRenderRequest,result:RequestedSkyResult<SkyRenderRequest,Bitmap>?,modifier:Modifier=Modifier) {
 FrostedBitmapBackdrop(result?.forRequest(expected),modifier)
}
