package local.skytrails.app.core

import io.github.cosinekitty.astronomy.*
import java.time.Instant

/** Fixed ICRS direction in degrees, at infinity; no proper motion or parallax. */
data class EquatorialDirection(val raDeg: Double, val decDeg: Double) {
    init { require(raDeg.isFinite() && decDeg.isFinite() && decDeg in -90.0..90.0) }
}
data class GalacticCoordinate(val longitudeDeg: Double, val latitudeDeg: Double) {
    init { require(longitudeDeg.isFinite() && latitudeDeg.isFinite() && latitudeDeg in -90.0..90.0) }
}
/** Context travels with the sampled geometry so the UI cannot silently relabel its time/site. */
data class GalacticSky(val site: Site, val at: Instant, val plane: List<SkyDirection>,
    val bandSouth: List<SkyDirection>, val bandNorth: List<SkyDirection>, val center: SkyDirection)

/**
 * Canonical Galactic/ICRS rotation from ERFA 2.0.1 eraG2icrs (retained source/license
 * in test resources/reference/galactic). Physical Sgr A* uses SIMBAD's radio ICRS
 * J2000 coordinate, 2011AJ....142...35P, NOT the geometric Galactic origin.
 *
 * Horizon conversion reuses Astronomy Engine's precession/nutation/Earth rotation.
 * ICRS is treated as EQJ at this app's 0.05-degree tolerance. Annual aberration,
 * light deflection, polar motion and proper motion are not applied. Thus these are
 * AIRLESS geometric directions, not a milliarcsecond apparent-place model.
 * The 45 independent Astropy apparent-place fixtures bound the sampled difference.
 */
class MilkyWayGeometry : GalacticDirections {
    fun toEquatorial(point: GalacticCoordinate): EquatorialDirection {
        val v=galacticToIcrs(Spherical(point.latitudeDeg,point.longitudeDeg,1.0).toVector(epoch))
        val s=v.toSpherical()
        return EquatorialDirection(s.lon,s.lat)
    }

    fun toGalactic(point: EquatorialDirection): GalacticCoordinate {
        val v=Spherical(point.decDeg,point.raDeg,1.0).toVector(epoch)
        val g=Vector(matrix[0][0]*v.x+matrix[0][1]*v.y+matrix[0][2]*v.z,
            matrix[1][0]*v.x+matrix[1][1]*v.y+matrix[1][2]*v.z,
            matrix[2][0]*v.x+matrix[2][1]*v.y+matrix[2][2]*v.z,epoch).toSpherical()
        return GalacticCoordinate(g.lon,g.lat)
    }

    fun direction(point: GalacticCoordinate, site: Site, at: Instant): SkyDirection =
        horizonConverter(site,at)(galacticToIcrs(Spherical(point.latitudeDeg,point.longitudeDeg,1.0).toVector(epoch)))

    override fun center(site: Site, at: Instant): SkyDirection = horizonConverter(site,at)(radioCenter)
    override fun plane(site: Site, at: Instant): List<SkyDirection> = sampledPlane.map(horizonConverter(site,at))

    /** ±5 degrees is a schematic recognition band, not measured visible brightness/width. */
    fun sky(site: Site, at: Instant): GalacticSky {
        val convert=horizonConverter(site,at)
        return GalacticSky(site,at,sampledPlane.map(convert),sampledSouth.map(convert),sampledNorth.map(convert),convert(radioCenter))
    }

    private fun horizonConverter(site: Site, at: Instant): (Vector)->SkyDirection {
        val time=Time.fromMillisecondsSince1970(at.toEpochMilli())
        val rotation=rotationEqjHor(time,Observer(site.latitude,site.longitude,site.elevationMeters+site.eyeHeightMeters))
        return { v ->
            val h=rotation.rotate(v.copy(t=time)).toHorizontal(Refraction.None)
            SkyDirection(h.lon,h.lat)
        }
    }

    companion object {
        private val epoch=Time(0.0)
        // Row-major ICRS -> Galactic. Transpose gives Galactic -> ICRS.
        private val matrix=arrayOf(
            doubleArrayOf(-0.05487556041621537,-0.8734370902348850,-0.4838350155487132),
            doubleArrayOf(0.49410942787558365,-0.4448296299600112,0.7469822444972188),
            doubleArrayOf(-0.8676661490190047,-0.19807637343120152,0.4559837761750669))
        private fun galacticToIcrs(g:Vector)=Vector(
            matrix[0][0]*g.x+matrix[1][0]*g.y+matrix[2][0]*g.z,
            matrix[0][1]*g.x+matrix[1][1]*g.y+matrix[2][1]*g.z,
            matrix[0][2]*g.x+matrix[1][2]*g.y+matrix[2][2]*g.z,epoch)
        private val radioCenter=Spherical(-29.00782497222222,266.416816625,1.0).toVector(epoch)
        private fun ring(latitude:Double):List<Vector> {
            val points=(0 until 360).map {galacticToIcrs(Spherical(latitude,it.toDouble(),1.0).toVector(epoch))}
            return points+points.first()
        }
        private val sampledPlane=ring(0.0)
        private val sampledSouth=ring(-5.0)
        private val sampledNorth=ring(5.0)
    }
}
