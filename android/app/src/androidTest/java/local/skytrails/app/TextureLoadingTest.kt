package local.skytrails.app

import android.graphics.BitmapFactory
import android.opengl.GLES20.*
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import io.github.cosinekitty.astronomy.Time
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import local.skytrails.app.ui.renderer.FieldStar
import local.skytrails.app.ui.renderer.FieldStars
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.ui.AppState
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.renderer.TextureSampling
import local.skytrails.app.ui.sky.VirtualSkyScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TextureLoadingTest {
 @Test fun sceneUsesExactTimeForAllTwentyFiveCatalogAnchorsAtMinuteBoundaries() {
  ui.mainClock.autoAdvance=false
  val runtime=CameraRuntime();val site=Site(30.2741,120.1551,0.0,0.0,ZoneId.of("Asia/Shanghai"))
  fun calculation(at:Instant):SkyCalculation {
   val sky=StarCatalog.sky(site,at);val target=sky.positions.getValue(24436)
   return SkyCalculation(site,at,target,target,MilkyWayGeometry().sky(site,at),emptyList(),emptyList(),emptyList(),sky)
  }
  var calc by mutableStateOf(calculation(Instant.parse("2026-10-26T20:00:00Z")))
  ui.setContent{VirtualSkyScene(AppState(virtualSky=true,skyFollow=false),calc,runtime,null,{},Modifier.fillMaxSize())}
  for(second in listOf(0L,59L,60L)) {
   val next=calculation(Instant.parse("2026-10-26T20:00:00Z").plusSeconds(second))
   ui.runOnUiThread{calc=next}
   ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);runtime.skyView?.presented?.get()?.calculation==next}
   val frame=runtime.skyView!!.presented.get()!!;val years=Time.fromMillisecondsSince1970(next.at.toEpochMilli()).tt/365.25
   for(star in StarCatalog.stars) {
    val s=FieldStar(star.hip,star.raDeg.toFloat(),star.decDeg.toFloat(),star.pmRaMasYear.toFloat(),star.pmDecMasYear.toFloat(),star.magnitude.toFloat(),.65f)
    val m=FieldStars.galacticMotion(s);val g=DoubleArray(3){m[it]+m[it+3]*years};val length=sqrt(g.sumOf{it*it});for(i in 0..2)g[i]/=length
    val b=frame.basis;val expected=frame.projection.toCamera(next.stars!!.positions.getValue(star.hip))!!
    assertEquals("HIP ${star.hip} exact x at :$second",expected.xRight,b.x.xRight*g[0]+b.y.xRight*g[1]+b.z.xRight*g[2],2e-6)
    assertEquals("HIP ${star.hip} exact y at :$second",expected.yUp,b.x.yUp*g[0]+b.y.yUp*g[1]+b.z.yUp*g[2],2e-6)
    assertEquals("HIP ${star.hip} exact z at :$second",expected.zForward,b.x.zForward*g[0]+b.y.zForward*g[1]+b.z.zForward*g[2],2e-6)
   }
  }
 }

 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun actualContextLoadsRealFieldAndOnlySmallGalaxyFallback() {
  ui.mainClock.autoAdvance=false
  val runtime=CameraRuntime()
  ui.setContent{VirtualSkyScene(AppState(virtualSky=true,skyFollow=false),null,runtime,null,{},Modifier.fillMaxSize())}
  ui.waitUntil(15000){ui.mainClock.advanceTimeBy(64);runtime.skyView?.presented?.get()!=null}
  val view=runtime.skyView!!;assertNull(view.error)
  val limit=IntArray(1);val done=CountDownLatch(1)
  view.queueEvent{glGetIntegerv(GL_MAX_TEXTURE_SIZE,limit,0);done.countDown()}
  assertTrue(done.await(5,TimeUnit.SECONDS))
  val expected=if(limit[0]>=2048)2048 to 1024 else (2048/TextureSampling.sampleSize(2048,1024,limit[0])) to (1024/TextureSampling.sampleSize(2048,1024,limit[0]))
  assertEquals("Fixed HYG field catalog loaded",99049,view.loadedFieldStarCount)
  assertEquals("Catalog decoded once for this context",1L,view.fieldDecodeCount)
  val frames=view.frames;view.requestRender();ui.waitUntil(5000){view.frames>frames};assertEquals(1L,view.fieldDecodeCount)
  assertFalse("No photographic star texture retained",view.loadedTextureSizes.containsKey("bright-stars-panorama.jpg"))
  for(name in listOf("galaxy-fallback.jpg")) {
   assertEquals("Actual uploaded $name",expected,view.loadedTextureSizes[name])
   // Decode the real source through a constrained-device policy, without changing GL hardware.
   val bitmap=ui.activity.assets.open("sky/$name").use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=TextureSampling.sampleSize(2048,1024,1024);inScaled=false})}!!
   try{assertEquals(1024,bitmap.width);assertEquals(512,bitmap.height)}finally{bitmap.recycle()}
  }
  InstrumentationRegistry.getInstrumentation().sendStatus(2,android.os.Bundle().apply{putString("stream","TEXTURE_LOAD max=${limit[0]} sizes=${view.loadedTextureSizes}\n")})
 }
}
