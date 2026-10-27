package local.skytrails.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.roundToInt
import local.skytrails.app.ui.renderer.CameraRuntime
import local.skytrails.app.ui.renderer.GlassMask

@Composable fun FrostedSurface(modifier:Modifier=Modifier,opaque:Boolean=false,night:Boolean=false,radius:Dp=26.dp,content:@Composable BoxScope.()->Unit) {
 val shape=RoundedCornerShape(radius)
 val light=LocalLightGlass.current
 val base=if(light)Color(0xFFE7E5E1) else if(night)Color(0xFF2B1714) else Color(0xFF363A43)
 val background=if(opaque)Brush.verticalGradient(listOf(base,base)) else Brush.linearGradient(listOf(base.copy(alpha=if(light).87f else .46f),base.copy(alpha=if(light).78f else .24f),(if(light)Color.White else if(night)Color(0xFF20100D)else Color(0xFF202228)).copy(alpha=if(light).86f else .44f)))
 val edge=if(night)NightRed else Color.White
 val backdrop=LocalFrostedBackdrop.current
 var bounds by remember {mutableStateOf(Rect.Zero)}
 Box(modifier.onGloballyPositioned{bounds=it.boundsInWindow()}.clip(shape).border(.5.dp,Brush.linearGradient(listOf(edge.copy(alpha=.4f),edge.copy(alpha=.08f),edge.copy(alpha=.22f))),shape)) {
  if(!opaque)backdrop?.frame?.let {frame->
   Canvas(Modifier.matchParentSize()) {
    val image=frame.imageRect;val clip=frame.clipRect
    clipRect(clip.left-bounds.left,clip.top-bounds.top,clip.right-bounds.left,clip.bottom-bounds.top) {
     drawImage(frame.blurred,dstOffset=IntOffset((image.left-bounds.left).roundToInt(),(image.top-bounds.top).roundToInt()),dstSize=IntSize(image.width.roundToInt().coerceAtLeast(1),image.height.roundToInt().coerceAtLeast(1)))
    }
   }
  }
  Box(Modifier.matchParentSize().background(background))
  content()
 }
}
/** Each visible panel owns exactly one mask. Disposing a panel also removes its GL blur. */
@Composable fun Modifier.glassMask(runtime:CameraRuntime,name:String,radius:Dp=26.dp):Modifier {
 val pixels=with(LocalDensity.current){radius.toPx()}
 DisposableEffect(runtime,name){onDispose {runtime.windowMasks.remove(name);runtime.masks()}}
 return this.onGloballyPositioned {coordinates->
  val b=coordinates.boundsInWindow();val next=GlassMask(b.left,b.top,b.right,b.bottom,pixels)
  if(runtime.windowMasks[name]!=next){runtime.windowMasks[name]=next;runtime.masks()}
 }
}
