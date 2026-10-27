package local.skytrails.app.core

import io.github.cosinekitty.astronomy.*
import java.time.Instant
import kotlin.math.*

/** μRA is eastward angular speed (includes cos(dec)), both proper motions in mas/year. */
data class CatalogStar(val hip:Int,val raDeg:Double,val decDeg:Double,val pmRaMasYear:Double,val pmDecMasYear:Double,val magnitude:Double)
data class Constellation(val id:String,val name:String,val subtitle:String,val edges:List<Pair<Int,Int>>,val extraStars:Set<Int> = emptySet()) {
 val members:Set<Int> = edges.flatMap{listOf(it.first,it.second)}.toSet()+extraStars
}
data class StarSky(val site:Site,val at:Instant,val positions:Map<Int,SkyDirection>,val centers:Map<String,SkyDirection>)
object StarCatalog {
 val stars:List<CatalogStar> = catalogStars
 // Original schematic connections, not IAU region boundaries or copied artwork.
 val constellations=listOf(
  Constellation("dipper","北斗七星","大熊座中的星群",listOf(54061 to 53910,53910 to 58001,58001 to 59774,59774 to 54061,59774 to 62956,62956 to 65378,65378 to 67301)),
  Constellation("orion","猎户座","肩、腰带与双足",listOf(26207 to 27989,26207 to 25336,27989 to 26727,25336 to 25930,25930 to 26311,26311 to 26727,25930 to 24436,26727 to 27366,24436 to 27366)),
  Constellation("cassiopeia","仙后座","北天的 W 形",listOf(746 to 3179,3179 to 4427,4427 to 6686,6686 to 8886)),
  Constellation("crux","南十字座","南天的十字形",listOf(61084 to 60718,59747 to 62434),setOf(60260)))
 val ids=constellations.map{it.id}.toSet()
 fun constellation(id:String?)=constellations.firstOrNull{it.id==id}
 /** Geometric airless direction, tolerance 0.05°. See docs/data/star-catalog.md. */
 fun equatorial(star:CatalogStar,at:Instant):EquatorialDirection {
  val v=propagatedVector(star,Time.fromMillisecondsSince1970(at.toEpochMilli())).toSpherical()
  return EquatorialDirection(v.lon,v.lat)
 }
 private fun propagatedVector(star:CatalogStar,time:Time):Vector {
  val years=time.tt/365.25
  val ra=Math.toRadians(star.raDeg);val dec=Math.toRadians(star.decDeg)
  val east=Math.toRadians(star.pmRaMasYear/3_600_000.0)*years
  val north=Math.toRadians(star.pmDecMasYear/3_600_000.0)*years
  val x=cos(dec)*cos(ra)-east*sin(ra)-north*sin(dec)*cos(ra)
  val y=cos(dec)*sin(ra)+east*cos(ra)-north*sin(dec)*sin(ra)
  val z=sin(dec)+north*cos(dec);val length=sqrt(x*x+y*y+z*z)
  return Vector(x/length,y/length,z/length,time)
 }
 fun sky(site:Site,at:Instant):StarSky {
  val time=Time.fromMillisecondsSince1970(at.toEpochMilli())
  val rotation=rotationEqjHor(time,Observer(site.latitude,site.longitude,site.elevationMeters+site.eyeHeightMeters))
  val vectors=stars.associate{it.hip to propagatedVector(it,time)}
  fun horizontal(v:Vector):SkyDirection {val h=rotation.rotate(v).toHorizontal(Refraction.None);return SkyDirection(h.lon,h.lat)}
  val positions=vectors.mapValues{horizontal(it.value)}
  val centers=constellations.associate{c->
   val members=c.members.map{vectors.getValue(it)}
   c.id to horizontal(Vector(members.sumOf{it.x},members.sumOf{it.y},members.sumOf{it.z},time))
  }
  return StarSky(site,at,positions,centers)
 }
}
