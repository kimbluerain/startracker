package local.skytrails.app.ui.renderer

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class FieldStar(val hip:Int,val raDeg:Float,val decDeg:Float,val pmRaMasYear:Float,val pmDecMasYear:Float,val magnitude:Float,val bv:Float)
/** STF1 stores ICRS/J2000; eastward proper motion already includes cos(dec). */
object FieldStars {
 fun read(input:InputStream):List<FieldStar> {
  val bytes=input.readBytes();require(bytes.size>=8){"Truncated STF1 header"}
  val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  require((0..3).map{b.get().toInt().toChar()}.joinToString("")=="STF1"){"Invalid star catalog"}
  val count=b.int;require(count in 0..250000&&b.remaining().toLong()==count*28L){"Invalid STF1 record count"}
  return List(count) {
   FieldStar(b.int,b.float,b.float,b.float,b.float,b.float,b.float).also {s->
    require(s.hip>=0&&s.raDeg.isFinite()&&s.raDeg>=0&&s.raDeg<360&&s.decDeg.isFinite()&&s.decDeg in -90f..90f&&s.pmRaMasYear.isFinite()&&s.pmDecMasYear.isFinite()&&s.magnitude.isFinite()&&s.bv.isFinite()){"Invalid STF1 record"}
   }
  }
 }
 /** Galactic unit direction followed by its tangent velocity, radians per TT year. */
 fun galacticMotion(star:FieldStar):FloatArray {
  val ra=Math.toRadians(star.raDeg.toDouble());val dec=Math.toRadians(star.decDeg.toDouble())
  val east=Math.toRadians(star.pmRaMasYear/3600000.0);val north=Math.toRadians(star.pmDecMasYear/3600000.0)
  val p=doubleArrayOf(cos(dec)*cos(ra),cos(dec)*sin(ra),sin(dec))
  val v=doubleArrayOf(-east*sin(ra)-north*sin(dec)*cos(ra),east*cos(ra)-north*sin(dec)*sin(ra),north*cos(dec))
  return (rotate(p)+rotate(v)).map{it.toFloat()}.toFloatArray()
 }
 fun magnitudeLimit(horizontalFov:Double):Float=(6.3+2*log2(70.0/horizontalFov.coerceIn(20.0,100.0))).coerceIn(5.5,9.5).toFloat()
 /** Restrained B-V tint; visual RGB only, not a stellar temperature measurement. */
 fun color(bv:Float):FloatArray {
  val t=((bv+.4f)/2.4f).coerceIn(0f,1f)
  return if(t<.44f){val a=t/.44f;floatArrayOf(.76f+.24f*a,.84f+.16f*a,1f)}
   else {val a=(t-.44f)/.56f;floatArrayOf(1f,1f-.18f*a,1f-.38f*a)}
 }
 private fun rotate(v:DoubleArray)=doubleArrayOf(
  -.05487556041621537*v[0]-.8734370902348850*v[1]-.4838350155487132*v[2],
  .49410942787558365*v[0]-.4448296299600112*v[1]+.7469822444972188*v[2],
  -.8676661490190047*v[0]-.19807637343120152*v[1]+.4559837761750669*v[2])
}
