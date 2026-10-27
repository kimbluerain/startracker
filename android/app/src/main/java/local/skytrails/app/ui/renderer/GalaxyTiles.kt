package local.skytrails.app.ui.renderer

import local.skytrails.app.ui.sky.GalacticCameraBasis
import local.skytrails.app.ar.CameraVector
import kotlin.math.*

data class GalaxyTile(val level:Int,val x:Int,val y:Int) {
 val path get()="sky/galaxy-tiles/$level/${x}_${y}.jpg"
}
data class GalaxyTilePlan(val level:Int,val tiles:List<GalaxyTile>)
object GalaxyTiles {
 const val TILE_BYTES=514L*514*4
 const val BUDGET_BYTES=48L*1024*1024
 const val MAX_RESIDENT=(BUDGET_BYTES/TILE_BYTES).toInt()
 fun center(tile:GalaxyTile,basis:GalacticCameraBasis):CameraVector {
  val longitude=2*PI*(.5-(tile.x+.5)/(tile.level/512));val latitude=PI*(.5-(tile.y+.5)/(tile.level/1024))
  val x=cos(latitude)*cos(longitude);val y=cos(latitude)*sin(longitude);val z=sin(latitude)
  return CameraVector(basis.x.xRight*x+basis.y.xRight*y+basis.z.xRight*z,basis.x.yUp*x+basis.y.yUp*y+basis.z.yUp*z,basis.x.zForward*x+basis.y.zForward*y+basis.z.zForward*z)
 }
 // The sum of half-width and half-height encloses the entire spherical tile, including poles.
 fun radius(tile:GalaxyTile)=2*PI/(tile.level/512)
 fun select(width:Int,height:Int,fov:Double,basis:GalacticCameraBasis):GalaxyTilePlan {
  require(width>0&&height>0)
  val half=Math.toRadians(fov)/2;val vertical=atan(tan(half)*height/width)
  val desired=width*360/fov;val levels=listOf(16384,8192,4096)
  val initial=when{desired>8192->16384;desired>4096->8192;else->4096}
  for(level in levels.filter{it<=initial}) {
   val visible=ArrayList<Pair<GalaxyTile,Double>>()
   for(y in 0 until level/1024)for(x in 0 until level/512) {
    val tile=GalaxyTile(level,x,y);val c=center(tile,basis);val allowance=sin(radius(tile))
    if(abs(c.xRight)*cos(half)-c.zForward*sin(half)<=allowance&&abs(c.yUp)*cos(vertical)-c.zForward*sin(vertical)<=allowance&&c.zForward>=-allowance)visible+=tile to c.zForward
   }
   if(visible.size<=MAX_RESIDENT)return GalaxyTilePlan(level,visible.sortedByDescending{it.second}.map{it.first})
  }
  error("Global 4K grid must fit the byte budget")
 }
}
