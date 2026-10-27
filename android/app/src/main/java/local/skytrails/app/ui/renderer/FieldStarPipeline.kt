package local.skytrails.app.ui.renderer

import android.opengl.GLES20.*
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.core.StarCatalog
import local.skytrails.app.ui.sky.GalacticCameraBasis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Real catalog directions, uploaded once per context. No per-frame CPU star projection. */
class FieldStarPipeline(stars:List<FieldStar>,private val density:Float=1f) {
 private val program=GlassPipeline.compileProgram(VERTEX,FRAGMENT)
 private val buffer:Int
 private val ordinaryCount:Int
 private val precise:List<FieldStar>
 private val pointLimit:Float
 init {
  val preciseIds=StarCatalog.stars.map{it.hip}.toSet()
  val ordinary=stars.filter{it.hip !in preciseIds};precise=stars.filter{it.hip in preciseIds};ordinaryCount=ordinary.size
  val data=ByteBuffer.allocateDirect(stars.size*STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer()
  (ordinary+precise).forEach{s->
   data.put(FieldStars.galacticMotion(s));data.put(FieldStars.color(s.bv))
   // Missing HIP is a real non-Hipparcos source; seed its visual phase from its coordinates.
   val seed=if(s.hip>0)s.hip else ((s.raDeg*10000).toInt()*31+(s.decDeg*10000).toInt()).and(Int.MAX_VALUE)
   data.put(s.magnitude);data.put((seed%997)*2f*PI.toFloat()/997f);data.put(2f*PI.toFloat()*(9+seed%13)/60f)
  }
  data.position(0);val ids=IntArray(1);glGenBuffers(1,ids,0);buffer=ids[0]
  glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,stars.size*STRIDE,data,GL_STATIC_DRAW);glBindBuffer(GL_ARRAY_BUFFER,0)
  val range=FloatArray(2);glGetFloatv(GL_ALIASED_POINT_SIZE_RANGE,range,0);pointLimit=range[1]
 }
 fun draw(width:Int,height:Int,basis:GalacticCameraBasis,up:CameraVector?,fov:Double,night:Boolean,twinkle:Boolean,seconds:Float,years:Float,hiddenHips:Set<Int> = emptySet()) {
  glUseProgram(program)
  fun scalar(name:String,v:Float){glUniform1f(glGetUniformLocation(program,name),v)}
  fun vector(name:String,v:CameraVector){glUniform3f(glGetUniformLocation(program,name),v.xRight.toFloat(),v.yUp.toFloat(),v.zForward.toFloat())}
  vector("uX",basis.x);vector("uY",basis.y);vector("uZ",basis.z);vector("uUp",up?:CameraVector(0.0,0.0,0.0))
  glUniform2f(glGetUniformLocation(program,"uSize"),width.toFloat(),height.toFloat())
  scalar("uFocal",(width/(2*tan(Math.toRadians(fov)/2))).toFloat());scalar("uDensity",density);scalar("uMaxPoint",pointLimit)
  scalar("uYears",years);scalar("uLimit",FieldStars.magnitudeLimit(fov));scalar("uTime",seconds)
  scalar("uTwinkle",if(twinkle)1f else 0f);scalar("uNight",if(night)1f else 0f);scalar("uHorizon",if(up!=null)1f else 0f)
  glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glBindBuffer(GL_ARRAY_BUFFER,buffer)
  val attributes=listOf("aPosition" to 0,"aMotion" to 12,"aColor" to 24,"aStar" to 36).map{(name,offset)->
   glGetAttribLocation(program,name).also{glEnableVertexAttribArray(it);glVertexAttribPointer(it,3,GL_FLOAT,false,STRIDE,offset)}
  }
  glDrawArrays(GL_POINTS,0,ordinaryCount)
  precise.forEachIndexed{i,s->if(s.hip !in hiddenHips)glDrawArrays(GL_POINTS,ordinaryCount+i,1)}
  attributes.forEach{glDisableVertexAttribArray(it)};glBindBuffer(GL_ARRAY_BUFFER,0);glDisable(GL_BLEND)
 }
 fun release(){glDeleteBuffers(1,intArrayOf(buffer),0);glDeleteProgram(program)}
 companion object {
  private const val STRIDE=12*4
  private const val VERTEX="""precision highp float;
   attribute vec3 aPosition;attribute vec3 aMotion;attribute vec3 aColor;attribute vec3 aStar;
   uniform vec3 uX;uniform vec3 uY;uniform vec3 uZ;uniform vec3 uUp;uniform vec2 uSize;
   uniform float uFocal;uniform float uDensity;uniform float uMaxPoint;uniform float uYears;uniform float uLimit;uniform float uTime;uniform float uTwinkle;uniform float uNight;uniform float uHorizon;
   varying vec3 vColor;varying vec4 vShape;varying vec3 vLight;
   void main(){
    vec3 g=normalize(aPosition+aMotion*uYears);vec3 ray=uX*g.x+uY*g.y+uZ*g.z;
    float mag=aStar.x;float radius=clamp(2.2-mag*.22,.55,2.3)*uDensity;
    float strength=pow(clamp((2.5-mag)/2.5,0.,1.),.6);
    float spike=radius*(3.+strength);float extent=max(radius*2.5,spike);
    gl_PointSize=min(uMaxPoint,extent*2.);vShape=vec4(radius,extent,(.45+.15*strength)*uDensity,spike);
    gl_Position=vec4(ray.xy*2.*uFocal/uSize,0.,ray.z);
    float visible=1.-smoothstep(uLimit-.35,uLimit+.35,mag);
    float altitude=asin(clamp(dot(ray,uUp),-1.,1.))*57.295779513;
    float horizon=mix(1.,.12+.88*smoothstep(-4.,4.,altitude),uHorizon);
    float wave=.35*sin(aStar.y+uTime*aStar.z)+.10*sin(aStar.y*.7+uTime*aStar.z*2.);
    float brightness=1.+uTwinkle*wave;
    float alpha=clamp(.92*exp(-.20*max(0.,mag-1.)),.08,.95)*visible*horizon;
    vLight=vec3(alpha*brightness,uTwinkle*strength*(.18+.82*clamp((wave+.45)/.9,0.,1.)),gl_PointSize*.5);
    vColor=mix(aColor,vec3(1.,.15,.09),uNight);
    if(ray.z<=0.||visible<=0.){gl_Position=vec4(2.,2.,2.,1.);vLight.x=0.;}
   }"""
  private const val FRAGMENT="""precision mediump float;
   varying vec3 vColor;varying vec4 vShape;varying vec3 vLight;
   void main(){
    vec2 p=(gl_PointCoord-.5)*2.*vLight.z;float r=length(p);float radius=vShape.x;
    float core=exp(-2.8*r*r/(radius*radius));float halo=.08*exp(-2.*r*r/(radius*radius*4.));
    vec2 arms=pow(max(vec2(0.),1.-abs(p)/vShape.w),vec2(1.7))*exp(-3.*p.yx*p.yx/(vShape.z*vShape.z));
    float spike=(arms.x+arms.y)*vLight.y*.55;
    float alpha=(core+halo+spike)*vLight.x*(1.-smoothstep(vShape.y*.8,vShape.y,r));
    gl_FragColor=vec4(vColor,clamp(alpha,0.,1.));
   }"""
 }
}
