package local.skytrails.app.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.core.StarSky
import local.skytrails.app.core.StarCatalog
import local.skytrails.app.ui.*

fun focusedTargetGuidance(target:SkyTarget,layers:SkyLayers,direction:SkyDirection?,projection:SkyProjection?,physical:Boolean):String? {
 if(target==SkyTarget.GALAXY||!layers.contains(target)||direction==null||projection==null)return null
 val indicator=GalacticOverlay.target(direction,projection)
 if(indicator !is TargetIndicator.Behind)return null
 return "${targetName(target)}在身后 · ${if(physical)"转身寻找" else "拖动寻找"}"+if(indicator.belowHorizon)" · 地平线下"else ""
}
@Composable fun FocusedTargetGuidance(target:SkyTarget,layers:SkyLayers,direction:SkyDirection?,projection:SkyProjection?,physical:Boolean,night:Boolean,modifier:Modifier=Modifier) {
 val hint=focusedTargetGuidance(target,layers,direction,projection,physical) ?: return
 Text(hint,modifier.testTag("focused_target_guidance").background(if(night)Color(0xE6280906)else Color(0xD9101216),RoundedCornerShape(18.dp)).padding(horizontal=14.dp,vertical=9.dp),color=if(night)NightRed else MoonSilver,fontSize=12.sp)
}

@Composable fun ConstellationGuidance(id:String,layers:StarLayers,sky:StarSky?,projection:SkyProjection?,physical:Boolean,night:Boolean,modifier:Modifier=Modifier) {
 if(!layers.shows(id)||sky==null||projection==null)return
 val direction=sky.centers[id]?:return
 val indicator=GalacticOverlay.target(direction,projection)
 if(indicator !is TargetIndicator.Behind)return
 val name=StarCatalog.constellation(id)?.name?:return
 val text="$name 在身后 · ${if(physical)"转身寻找"else"拖动寻找"}"+if(indicator.belowHorizon)" · 地平线下"else""
 Text(text,modifier.testTag("constellation_guidance").background(if(night)Color(0xE6280906)else Color(0xD9101216),RoundedCornerShape(18.dp)).padding(horizontal=14.dp,vertical=9.dp),color=if(night)NightRed else MoonSilver,fontSize=12.sp)
}
