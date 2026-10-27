package local.skytrails.app.ui.sky

import local.skytrails.app.core.Site
import local.skytrails.app.ui.SkyCalculation
import local.skytrails.app.ui.SkyTarget
import java.time.Instant

/** A focus intent captures its own target/site/time; layer changes cannot retarget pending work. */
data class SkyFocusRequest(val id:Long,val target:SkyTarget,val site:Site?,val at:Instant,val constellationId:String?=null)
/** Observation-session state survives renderer/page replacement, but is never persisted. */
class SkyFocusConsumption {
 var consumed=0L;private set
 fun consume(request:SkyFocusRequest?,calculation:SkyCalculation?):SkyViewBasis? {
  val r=request?:return null;val c=calculation?:return null
  if(r.id<=consumed||r.site!=c.site||r.at!=c.at)return null
  val d=if(r.constellationId!=null)c.stars?.centers?.get(r.constellationId)?:return null else when(r.target){SkyTarget.SUN->c.sun;SkyTarget.MOON->c.moon;SkyTarget.GALAXY->c.galaxy.center}
  val result=SkyViewBasis.from(SkyProjection.virtual(100,100,70.0,d.azimuthDeg,d.altitudeDeg))?:return null
  consumed=r.id;return result
 }
}
