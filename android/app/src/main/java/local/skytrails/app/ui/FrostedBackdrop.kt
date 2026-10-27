package local.skytrails.app.ui

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.roundToInt

/** A shared, softly filtered photograph. Panels sample this same image in window coordinates. */
data class BackdropFrame(val blurred:ImageBitmap,val imageRect:Rect,val clipRect:Rect)
class FrostedBackdrop {
 var frame by mutableStateOf<BackdropFrame?>(null)
  private set
 private var owner:Any?=null
 fun publish(source:Any,value:BackdropFrame) {owner=source;frame=value}
 fun release(source:Any) {if(owner===source){owner=null;frame=null}}
}
val LocalFrostedBackdrop=staticCompositionLocalOf<FrostedBackdrop?>{null}

private data class BlurredSource(val source:Bitmap,val image:ImageBitmap)

/** The same measured image drives visible pixels and the glass sampling coordinates. */
@Composable fun FrostedBitmapBackdrop(source:Bitmap?,modifier:Modifier=Modifier,contentScale:ContentScale=ContentScale.FillBounds) {
 val backdrop=LocalFrostedBackdrop.current
 val owner=remember{Any()}
 var bounds by remember{mutableStateOf(Rect.Zero)}
 val filtered by produceState<BlurredSource?>(null,source) {
  value=source?.let{withContext(Dispatchers.Default){BlurredSource(it,frostedBitmap(it))}}
 }
 // Read async result AND layout in composition, not inside the SideEffect callback: both
 // must invalidate publication even when there is no timer or other recomposition.
 val soft=filtered?.takeIf{it.source===source}?.image
 val viewport=bounds
 val frame=if(source!=null&&soft!=null&&viewport.width>0&&viewport.height>0) {
  val scale=contentScale.computeScaleFactor(Size(source.width.toFloat(),source.height.toFloat()),viewport.size)
  val w=source.width*scale.scaleX;val h=source.height*scale.scaleY
  BackdropFrame(soft,Rect(viewport.center.x-w/2,viewport.center.y-h/2,viewport.center.x+w/2,viewport.center.y+h/2),viewport)
 } else null
 DisposableEffect(backdrop,owner){onDispose{backdrop?.release(owner)}}
 SideEffect{if(frame!=null)backdrop?.publish(owner,frame) else backdrop?.release(owner)}
 Box(modifier.onGloballyPositioned{bounds=it.boundsInWindow()}) {
  source?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=contentScale)}
 }
}

/** Separable low-pass filter, supported on API 30 too. Text and vector overlays never enter it. */
suspend fun frostedBitmap(source:Bitmap):ImageBitmap {
 val scale=320f/max(source.width,source.height)
 val width=(source.width*scale.coerceAtMost(1f)).roundToInt().coerceAtLeast(1)
 val height=(source.height*scale.coerceAtMost(1f)).roundToInt().coerceAtLeast(1)
 val small=source.scale(width,height,true)
 var pixels=IntArray(width*height);small.getPixels(pixels,0,width,0,0,width,height)
 if(small!==source)small.recycle()
 val radius=5;val span=radius*2+1
 repeat(2) {
  val horizontal=IntArray(pixels.size)
  for(y in 0 until height) {
   if(y%32==0)currentCoroutineContext().ensureActive()
   var r=0;var g=0;var b=0
   fun add(x:Int,sign:Int){val value=pixels[y*width+x.coerceIn(0,width-1)];r+=((value ushr 16)and 255)*sign;g+=((value ushr 8)and 255)*sign;b+=(value and 255)*sign}
   for(x in -radius..radius)add(x,1)
   for(x in 0 until width){horizontal[y*width+x]=(255 shl 24)or((r/span)shl 16)or((g/span)shl 8)or(b/span);add(x-radius,-1);add(x+radius+1,1)}
  }
  val vertical=IntArray(pixels.size)
  for(x in 0 until width) {
   var r=0;var g=0;var b=0
   fun add(y:Int,sign:Int){val value=horizontal[y.coerceIn(0,height-1)*width+x];r+=((value ushr 16)and 255)*sign;g+=((value ushr 8)and 255)*sign;b+=(value and 255)*sign}
   for(y in -radius..radius)add(y,1)
   for(y in 0 until height){vertical[y*width+x]=(255 shl 24)or((r/span)shl 16)or((g/span)shl 8)or(b/span);add(y-radius,-1);add(y+radius+1,1)}
  }
  pixels=vertical
 }
 return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888).asImageBitmap()
}
