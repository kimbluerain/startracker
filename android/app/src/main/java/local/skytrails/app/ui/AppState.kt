package local.skytrails.app.ui

import local.skytrails.app.core.*
import local.skytrails.app.ui.sky.SkyFocusRequest
import local.skytrails.app.location.*
import java.time.*

enum class AppTab { OBSERVE, PLAN, PLACES }
enum class SkyTarget { SUN, MOON, GALAXY }
/** Display choices do not add a planning target or change the ephemeris. */
enum class DisplayLayer(val title:String,val icon:SkyIcon,val target:SkyTarget?=null) {
 SUN("太阳",SkyIcon.SUN,SkyTarget.SUN),MOON("月亮",SkyIcon.MOON,SkyTarget.MOON),
 GALAXY("银河",SkyIcon.GALAXY,SkyTarget.GALAXY),STARS("星系星座",SkyIcon.STAR)
}
/** Visibility is independent from the selected information / planning target. */
data class SkyLayers(val sun:Boolean=true,val moon:Boolean=true,val galaxy:Boolean=true) {
    fun contains(target:SkyTarget)=when(target){SkyTarget.SUN->sun;SkyTarget.MOON->moon;SkyTarget.GALAXY->galaxy}
    fun set(target:SkyTarget,visible:Boolean)=when(target){SkyTarget.SUN->copy(sun=visible);SkyTarget.MOON->copy(moon=visible);SkyTarget.GALAXY->copy(galaxy=visible)}
    fun toggle(target:SkyTarget)=set(target,!contains(target))
    val visibleTargets get()=SkyTarget.entries.filter(::contains)
}
enum class AppThemeMode { SYSTEM, LIGHT, DARK, OUTDOOR, NIGHT }
data class AppState(val location:SelectedLocation?=null,val instant:Instant=Instant.now(),val live:Boolean=true,
    val tab:AppTab=AppTab.OBSERVE,val target:SkyTarget=SkyTarget.GALAXY,val theme:AppThemeMode=AppThemeMode.SYSTEM,
    val reduceTransparency:Boolean=false,val cameraPermission:Boolean=false,val locationPermission:Boolean=false,
    val realNow:Instant=Instant.now(),val layers:SkyLayers=SkyLayers(),val controlsHidden:Boolean=false,val virtualSky:Boolean=false,val focusRequest:Long=0,val skyFollow:Boolean=true,val physicalLocation:SelectedLocation?=null,val skyFocusRequest:SkyFocusRequest?=null,val starLayers:StarLayers=StarLayers(),val starFocusId:String?=null,val observationCategory:DisplayLayer=DisplayLayer.GALAXY) {
    fun focusConstellation(id:String,at:Instant=instant):AppState {
        if(id !in StarCatalog.ids)return this
        return copy(observationCategory=DisplayLayer.STARS,starFocusId=id,starLayers=starLayers.copy(enabled=starLayers.enabled+id,masterVisible=true),virtualSky=true,skyFollow=false,
            focusRequest=focusRequest+1,skyFocusRequest=SkyFocusRequest(focusRequest+1,target,location?.site,at,id))
    }
    fun select(place:SelectedLocation)=withSelectedLocation(place)
    fun withSelectedLocation(place:SelectedLocation?):AppState {
        val previous=poseLocation(realNow)
        val next=copy(location=place,physicalLocation=previous)
        return next.copy(physicalLocation=place?.takeIf{next.locationEligible(it,realNow)} ?: previous)
    }
    /** Session-only real-world context; preview city/year never supplies magnetic declination. */
    fun poseLocation(at:Instant):SelectedLocation?=listOfNotNull(physicalLocation,location).firstOrNull{locationEligible(it,at)}
    fun focus(target:SkyTarget,at:Instant=instant)=copy(observationCategory=DisplayLayer.entries.first{it.target==target},target=target,starFocusId=null,focusRequest=focusRequest+1,skyFollow=false,skyFocusRequest=SkyFocusRequest(focusRequest+1,target,location?.site,at))
    fun requestCameraMode(at:Instant)=if(physicalEligible(at))copy(virtualSky=false)else this
    fun event(target:SkyTarget,at:Instant)=copy(observationCategory=DisplayLayer.entries.first{it.target==target},target=target,starFocusId=null,instant=at,live=false,layers=layers.set(target,true),focusRequest=focusRequest+1,skyFollow=false,skyFocusRequest=SkyFocusRequest(focusRequest+1,target,location?.site,at))
    fun toggleLayer(layer:SkyTarget):AppState {
        val visible=layers.toggle(layer)
        val focus=when {visible.contains(layer)->layer;target==layer->visible.visibleTargets.firstOrNull() ?: target;else->target}
        return copy(layers=visible,target=focus,starFocusId=null)
    }
    fun displays(layer:DisplayLayer)=layer.target?.let(layers::contains) ?: starLayers.masterVisible
    fun toggleDisplayLayer(layer:DisplayLayer)=layer.target?.let{copy(layers=layers.toggle(it))}
        ?: copy(starLayers=starLayers.copy(masterVisible=!starLayers.masterVisible))
    fun allDisplayLayers(visible:Boolean)=copy(layers=SkyLayers(visible,visible,visible),
        starLayers=starLayers.copy(masterVisible=visible,enabled=if(visible)StarCatalog.ids else starLayers.enabled))
    fun preview(at:Instant)=copy(instant=at,live=false)
    fun now(at:Instant)=copy(instant=at,realNow=at,live=true)
    fun tick(at:Instant)=copy(realNow=at,instant=if(live)at else instant)
    private fun locationEligible(place:SelectedLocation,at:Instant):Boolean=when(place.source) {
        LocationSource.MANUAL->place.confirmedManualOnSite
        LocationSource.CITY_CENTER->false
        LocationSource.DEVICE->locationPermission&&place.fixAt?.let{Duration.between(it,at).seconds in 0..900}==true
    }
    fun physicalEligible(at:Instant)=location?.let{locationEligible(it,at)}==true
    fun cameraEligible(at:Instant)=cameraPermission && tab==AppTab.OBSERVE && physicalEligible(at)
}
fun instantOnDay(date:LocalDate,zone:ZoneId,fraction:Double):Instant {
    require(date.year in 2000..2050 && fraction.isFinite())
    val day=dayWindow(date,zone);val millis=Duration.between(day.start,day.endExclusive).toMillis()
    require(millis>0) { "此时区跳过了所选日期" }
    return day.start.plusMillis((millis*fraction.coerceIn(0.0,1.0)).toLong().coerceAtMost(millis-1))
}
class LatestRequest { private var version=0L; fun begin()=++version; fun accepts(id:Long)=id==version }
