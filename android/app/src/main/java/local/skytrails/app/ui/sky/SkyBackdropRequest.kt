package local.skytrails.app.ui.sky

import local.skytrails.app.core.Site

data class SkyDirectionRequest(val site:Site?,val minute:Long?)
/** Every input that changes photographic pixels, including the loaded source identity. */
data class SkyRenderRequest(val direction:SkyDirectionRequest,val azimuth:Float,val altitude:Float,
 val horizontalFov:Double,val width:Int,val height:Int,val galaxy:Boolean,val night:Boolean,
 val exposure:Float,val sourceIdentity:Any?,val starsVisible:Boolean=true)
data class RequestedSkyResult<K,V>(val request:K,val value:V) {
 fun forRequest(expected:K):V?=value.takeIf{request==expected}
}
