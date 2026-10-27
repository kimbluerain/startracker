package local.skytrails.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import local.skytrails.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/** Tests displayed pixels, including async publication and window-to-panel sampling. */
class FrostedBackdropDisplayTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun stationaryAsyncImagePublishesAndOnlyPanelLosesFineDetail() {
  val loaded=CompletableDeferred<Bitmap>();val backdrop=FrostedBackdrop()
  ui.setContent {
   val source by produceState<Bitmap?>(null){value=loaded.await()}
   CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
    SkyTheme(AppThemeMode.DARK) {
     Box(Modifier.size(240.dp,300.dp).testTag("pixel_fixture")) {
      FrostedBitmapBackdrop(source,Modifier.fillMaxSize())
      FrostedSurface(Modifier.offset(20.dp,120.dp).size(200.dp,100.dp),radius=12.dp){}
     }
    }
   }
  }
  ui.waitForIdle();assertNull(backdrop.frame)
  val pixels=IntArray(240*300){i->if((i%240)/4%2==0)0xff101010.toInt()else 0xffeeeeee.toInt()}
  loaded.complete(Bitmap.createBitmap(pixels,240,300,Bitmap.Config.ARGB_8888))
  // There is no timer, click, model update or parent recomposition after async completion.
  ui.waitUntil(10000){backdrop.frame!=null};ui.waitForIdle()
  val capture=ui.onNodeWithTag("pixel_fixture").captureToImage();val map=capture.toPixelMap()
  fun deviation(y:Float):Double {
   val row=(map.height*y).toInt();val values=(map.width/6 until map.width*5/6).map{x->map[x,row].red.toDouble()}
   val mean=values.average();return sqrt(values.map{(it-mean)*(it-mean)}.average())
  }
  val outside=deviation(.20f);val inside=deviation(.55f)
  assertTrue("uncovered stripes must remain sharp: $outside",outside>.35)
  assertTrue("glass must filter displayed detail, not merely tint: $inside vs $outside",inside<outside*.20)
  save(capture.asAndroidBitmap(),"frosted-stripes.png")
 }
 @Test fun switchingBackgroundOwnerCannotClearNewFrameAndCameraRemovalClearsIt() {
  val backdrop=FrostedBackdrop();val first=Any();val second=Any()
  val frame=BackdropFrame(Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).asImageBitmap(),Rect(0f,0f,2f,2f),Rect(0f,0f,2f,2f))
  backdrop.publish(first,frame);backdrop.publish(second,frame);backdrop.release(first)
  assertSame(frame,backdrop.frame);backdrop.release(second);assertNull(backdrop.frame)
  var virtual by mutableStateOf(true)
  ui.setContent{CompositionLocalProvider(LocalFrostedBackdrop provides backdrop){
   if(virtual)FrostedBitmapBackdrop(frame.blurred.asAndroidBitmap(),Modifier.size(200.dp))
   else Box(Modifier.size(200.dp).background(Color.Black).testTag("camera_placeholder"))
  }}
  ui.waitUntil(10000){backdrop.frame!=null}
  ui.runOnIdle{virtual=false};ui.onNodeWithTag("camera_placeholder").assertExists()
  ui.runOnIdle{assertNull("camera must not inherit a virtual photo",backdrop.frame)}
 }
 @Test fun planNightPixelsAndGlassSourceHaveNoBlueGreenCast() {
  val backdrop=FrostedBackdrop();var captureBounds=Rect.Zero
  ui.setContent{CompositionLocalProvider(LocalFrostedBackdrop provides backdrop){
   SkyTheme(AppThemeMode.NIGHT){Box(Modifier.fillMaxSize().onGloballyPositioned{captureBounds=it.boundsInWindow()}.testTag("night_plan")){
    PlanScreen(AppState(tab=AppTab.PLAN,theme=AppThemeMode.NIGHT),null,{},{},{},{})
   }}
  }}
  ui.waitUntil(15000){backdrop.frame!=null};ui.waitForIdle()
  val capture=ui.onNodeWithTag("night_plan").captureToImage()
  val arc=ui.onNodeWithTag("plan_sun_arc").captureToImage()
  // captureToImage uses window pixels on Android. Exclude only the measured OS system
  // bar insets, mapped from window coordinates to this node's actual capture bounds.
  var safeWindow=Rect.Zero
  ui.runOnIdle {
   val decor=ui.activity.window.decorView
   val insets=requireNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(WindowInsetsCompat.Type.systemBars())
   safeWindow=Rect(insets.left.toFloat(),insets.top.toFloat(),(decor.width-insets.right).toFloat(),(decor.height-insets.bottom).toFloat())
  }
  val sampledBounds=captureBounds.intersect(safeWindow)
  assertTrue("measured app content must exist",sampledBounds.width>0&&sampledBounds.height>0)
  // Preserve evidence before any colour assertion, including genuine failures.
  save(capture.asAndroidBitmap(),"plan-night-pixels.png");save(arc.asAndroidBitmap(),"plan-night-arc.png")
  val directory=File(ui.activity.getExternalFilesDir(null),"validation")
  File(directory,"plan-night-region.txt").writeText("capture=$captureBounds\nsystemSafe=$safeWindow\nsampled=$sampledBounds\nimage=${capture.width}x${capture.height}\n")
  fun assertRed(image:androidx.compose.ui.graphics.ImageBitmap,label:String,windowBounds:Rect?=null,minBrightness:Float=.14f) {
   val pixels=image.toPixelMap();var lit=0;var nonRed=0
   for(y in 0 until pixels.height step 3)for(x in 0 until pixels.width step 3) {
    if(windowBounds!=null) {
     val windowX=windowBounds.left+(x+.5f)*windowBounds.width/pixels.width
     val windowY=windowBounds.top+(y+.5f)*windowBounds.height/pixels.height
     if(windowX<sampledBounds.left||windowX>=sampledBounds.right||windowY<sampledBounds.top||windowY>=sampledBounds.bottom)continue
    }
    val color=pixels[x,y]
    if(color.red+color.green+color.blue>minBrightness){lit++;if(color.red<color.green*1.4f||color.red<color.blue*1.4f)nonRed++}
   }
   assertTrue("$label needs visible pixels",lit>100)
   assertTrue("$label must be red throughout: $nonRed / $lit",nonRed<lit*.005)
  }
  assertRed(backdrop.frame!!.blurred,"glass source")
  assertRed(capture,"rendered Plan content",captureBounds)
  // This extra foreground check keeps thin horizon/time regressions from being diluted
  // by the full photograph. It supplements the unchanged strict full-content check.
  assertRed(arc,"sun arc, horizon and time foreground",minBrightness=.5f)
 }
 private fun save(bitmap:Bitmap,name:String) {
  val directory=File(ui.activity.getExternalFilesDir(null),"validation").apply{mkdirs()}
  File(directory,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
}
