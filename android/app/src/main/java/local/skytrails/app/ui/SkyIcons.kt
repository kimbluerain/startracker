package local.skytrails.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class SkyIcon { TRAIL, ORBIT, SUN, MOON, GALAXY, CALENDAR, PIN, SEARCH, SETTINGS, LOCATION, PLUS, CROSSHAIR, CHEVRON, COLLAPSE, EXPAND, STAR, CLOSE, INFO, SKY_VIEW, AR_VIEW }
/** One native vector set: consistent round 1.6-unit strokes, no emoji/font substitutions. */
@Composable fun SkyGlyph(icon:SkyIcon,modifier:Modifier=Modifier,color:Color=MoonSilver) {
 Canvas(modifier.size(24.dp)) {
  val stroke=Stroke(1.6f,cap=StrokeCap.Round)
  withTransform({scale(size.width/24f,size.height/24f,Offset.Zero)}) {
   fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x,y),Offset(x2,y2),1.6f,StrokeCap.Round)
   fun circle(r:Float,x:Float=12f,y:Float=12f)=drawCircle(color,r,Offset(x,y),style=stroke)
   fun path(vararg points:Float,close:Boolean=false){val p=Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);if(close)p.close();drawPath(p,color,style=stroke)}
   when(icon) {
    SkyIcon.SKY_VIEW->{
     fun sparkle(x:Float,y:Float,r:Float) {
      val p=Path().apply{moveTo(x,y-r);quadraticTo(x+r*.15f,y-r*.15f,x+r,y);quadraticTo(x+r*.15f,y+r*.15f,x,y+r);quadraticTo(x-r*.15f,y+r*.15f,x-r,y);quadraticTo(x-r*.15f,y-r*.15f,x,y-r);close()}
      drawPath(p,color,style=Stroke(1.25f,cap=StrokeCap.Round))
     }
     sparkle(9.5f,14f,7f);sparkle(18f,5.5f,3.5f)
     drawCircle(color.copy(alpha=.7f),.8f,Offset(19f,18.5f))
    }
    SkyIcon.AR_VIEW->{
     val corners=Path().apply {
      moveTo(8f,3f);lineTo(5.5f,3f);quadraticTo(3f,3f,3f,5.5f);lineTo(3f,8f)
      moveTo(16f,3f);lineTo(18.5f,3f);quadraticTo(21f,3f,21f,5.5f);lineTo(21f,8f)
      moveTo(21f,16f);lineTo(21f,18.5f);quadraticTo(21f,21f,18.5f,21f);lineTo(16f,21f)
      moveTo(8f,21f);lineTo(5.5f,21f);quadraticTo(3f,21f,3f,18.5f);lineTo(3f,16f)
     }
     drawPath(corners,color,style=Stroke(1.35f,cap=StrokeCap.Round))
     drawCircle(color,4.2f,Offset(12f,12f),style=Stroke(1.35f))
     drawCircle(color.copy(alpha=.65f),1f,Offset(12f,12f))
    }
    SkyIcon.TRAIL->{
     rotate(-32f,Offset(12f,12f)){drawArc(color.copy(alpha=.7f),20f,288f,false,Offset(2f,6f),Size(20f,12f),style=Stroke(1.1f,cap=StrokeCap.Round));drawArc(color.copy(alpha=.32f),190f,120f,false,Offset(5f,3f),Size(14f,18f),style=Stroke(.8f,cap=StrokeCap.Round))}
     val p=Path().apply{moveTo(13f,6f);quadraticTo(13.6f,10.4f,18f,11f);quadraticTo(13.6f,11.6f,13f,16f);quadraticTo(12.4f,11.6f,8f,11f);quadraticTo(12.4f,10.4f,13f,6f);close()};drawPath(p,color);drawCircle(color.copy(alpha=.65f),.9f,Offset(5f,16.5f))
    }
    SkyIcon.ORBIT->{circle(7f);rotate(-28f,Offset(12f,12f)){drawOval(color,Offset(1f,8f),Size(22f,8f),style=stroke)}}
    SkyIcon.SUN->{circle(4f);for(i in 0..7){val a=i*PI/4;line(12+cos(a).toFloat()*7,12+sin(a).toFloat()*7,12+cos(a).toFloat()*9,12+sin(a).toFloat()*9)}}
    SkyIcon.MOON->{val p=Path().apply{moveTo(15f,3f);cubicTo(2f,3f,2f,21f,15f,21f);cubicTo(8f,17f,8f,7f,15f,3f);close()};drawPath(p,color)}
    SkyIcon.GALAXY->{rotate(-35f,Offset(12f,12f)){drawOval(color,Offset(2f,7f),Size(20f,10f),style=stroke);drawOval(color.copy(alpha=.65f),Offset(6f,3f),Size(12f,18f),style=stroke)};drawCircle(color,2f,Offset(12f,12f))}
    SkyIcon.CALENDAR->{drawRoundRect(color,Offset(4f,5f),Size(16f,16f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke);line(4f,10f,20f,10f);line(8f,2f,8f,7f);line(16f,2f,16f,7f)}
    SkyIcon.PIN->{val p=Path().apply{moveTo(12f,22f);cubicTo(8f,17f,4f,12f,5f,8f);cubicTo(7f,0f,17f,0f,19f,8f);cubicTo(20f,12f,16f,17f,12f,22f);close()};drawPath(p,color,style=stroke);circle(2.5f,12f,9f)}
    SkyIcon.SEARCH->{circle(7f,10f,10f);line(15f,15f,21f,21f)}
    SkyIcon.SETTINGS->{
     val gear=Path();for(i in 0..47){val a=-PI/2+i*PI/24;val r=if(i%8 in 2..5)9f else 7.3f;val x=12+cos(a).toFloat()*r;val y=12+sin(a).toFloat()*r;if(i==0)gear.moveTo(x,y)else gear.lineTo(x,y)};gear.close();drawPath(gear,color,style=Stroke(1.35f,cap=StrokeCap.Round));drawCircle(color,3.1f,Offset(12f,12f),style=Stroke(1.35f))
    }
    SkyIcon.LOCATION->path(3f,10f,21f,3f,14f,21f,11f,13f,3f,10f,close=true)
    SkyIcon.PLUS->{line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
    SkyIcon.CROSSHAIR->{circle(6.5f);circle(2.5f);line(12f,1f,12f,4f);line(12f,20f,12f,23f);line(1f,12f,4f,12f);line(20f,12f,23f,12f)}
    SkyIcon.CHEVRON->path(9f,5f,16f,12f,9f,19f)
    SkyIcon.COLLAPSE->path(6f,9f,12f,15f,18f,9f)
    SkyIcon.EXPAND->path(6f,15f,12f,9f,18f,15f)
    SkyIcon.STAR->{val p=Path();for(i in 0..9){val a=-PI/2+i*PI/5;val r=if(i%2==0)9f else 4f;val x=12+cos(a).toFloat()*r;val y=12+sin(a).toFloat()*r;if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};p.close();drawPath(p,color)}
    SkyIcon.CLOSE->{line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
    SkyIcon.INFO->{circle(9f);line(12f,11f,12f,17f);drawCircle(color,1f,Offset(12f,7f))}
   }
  }
 }
}
fun targetIcon(target:SkyTarget)=when(target){SkyTarget.SUN->SkyIcon.SUN;SkyTarget.MOON->SkyIcon.MOON;SkyTarget.GALAXY->SkyIcon.GALAXY}
