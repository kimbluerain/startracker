package local.skytrails.app.ui.sky

import local.skytrails.app.ar.Pixel
import local.skytrails.app.core.*
import local.skytrails.app.ui.*
import java.time.format.DateTimeFormatter

data class StarMark(val hip:Int,val pixel:Pixel,val magnitude:Double,val below:Boolean)
data class ConstellationStroke(val id:String,val segment:SkySegment)
data class SkyText(val text:String,val pixel:Pixel,val id:String)
data class StarDrawing(val stars:List<StarMark>,val lines:List<ConstellationStroke>,val names:List<SkyText>)
object StarOverlay {
 fun render(sky:StarSky?,layers:StarLayers,projection:SkyProjection):StarDrawing {
  if(sky==null||!layers.masterVisible)return StarDrawing(emptyList(),emptyList(),emptyList())
  val objects=StarCatalog.constellations.filter{it.id in layers.enabled}
  val members=objects.flatMap{it.members}.toSet()
  val stars=if(layers.stars)StarCatalog.stars.filter{it.hip in members}.mapNotNull{s->
   val p=sky.positions[s.hip]?.let{GalacticOverlay.target(it,projection)} as? TargetIndicator.Visible
   p?.let{StarMark(s.hip,it.pixel,s.magnitude,it.belowHorizon)}
  }else emptyList()
  val lines=if(layers.lines)objects.flatMap{c->c.edges.flatMap{(a,b)->
   val start=sky.positions[a];val end=sky.positions[b]
   if(start==null||end==null)emptyList()else GalacticOverlay.path(listOf(start,end),projection).map{ConstellationStroke(c.id,it)}
  }}else emptyList()
  val names=if(layers.names)objects.mapNotNull{c->
   val p=sky.centers[c.id]?.let{GalacticOverlay.target(it,projection)} as? TargetIndicator.Visible
   p?.let{SkyText(c.name,it.pixel,c.id)}
  }else emptyList()
  return StarDrawing(stars,lines,names)
 }
}
/** A local calendar day is an open time interval, NOT an orbital loop. */
fun moonEndpointLabels(c:SkyCalculation,projection:SkyProjection):List<SkyText> {
 if(c.moonPath.size<2)return emptyList()
 val zone=c.site.zone;val day=dayWindow(c.at.atZone(zone).toLocalDate(),zone)
 if(day.start>=day.endExclusive)return emptyList()
 val format=DateTimeFormatter.ofPattern("MM/dd HH:mm")
 return listOf(Triple("起",day.start,c.moonPath.first()),Triple("止",day.endExclusive,c.moonPath.last())).mapNotNull{(title,at,d)->
  val p=GalacticOverlay.target(d,projection) as? TargetIndicator.Visible
  p?.let{SkyText("$title · ${format.format(at.atZone(zone))}",it.pixel,"moon_$title")}
 }
}

fun moonRangeText(c:SkyCalculation?):String? {
 if(c==null||c.moonPath.size<2)return null
 val zone=c.site.zone;val day=dayWindow(c.at.atZone(zone).toLocalDate(),zone)
 if(day.start>=day.endExclusive)return null
 val format=DateTimeFormatter.ofPattern("HH:mm")
 return "当日月轨 · ${format.format(day.start.atZone(zone))}—次日${format.format(day.endExclusive.atZone(zone))}"
}

/** Move text, never the endpoint, away from a nearby current Moon marker. */
fun moonEndpointLabelPosition(endpoint:SkyText,moon:TargetIndicator,density:Float):Pixel {
 val p=endpoint.pixel
 val nearby=moon as? TargetIndicator.Visible
 val near=nearby!=null&&kotlin.math.hypot(nearby.pixel.x-p.x,nearby.pixel.y-p.y)<90*density
 val dy=if(near){if(nearby!!.pixel.y>=p.y)-30 else 12}else -16
 return Pixel(p.x+9*density,p.y+dy*density)
}
