package local.skytrails.app.ui

import local.skytrails.app.core.StarCatalog

data class StarLayers(val stars:Boolean=true,val lines:Boolean=true,val names:Boolean=true,
 val enabled:Set<String> = setOf("dipper","orion","cassiopeia","crux"),val masterVisible:Boolean=true,val twinkle:Boolean=false) {
 fun shows(id:String)=masterVisible&&id in enabled&&(stars||lines||names)
 fun toggle(id:String):StarLayers = if(id !in StarCatalog.ids)this else copy(enabled=if(id in enabled)enabled-id else enabled+id)
}
