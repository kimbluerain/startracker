package local.skytrails.app.ui.renderer

import android.opengl.GLES20.*
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ui.sky.GalacticCameraBasis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tan
import io.github.cosinekitty.astronomy.Time
import local.skytrails.app.ui.sky.StarOverlay

/** GPU spherical panorama projection. Input bitmaps have north at v=0; output is an ordinary FBO. */
class SkyPipeline(density:Float=1f,fieldStars:List<FieldStar>?=null,private val tiles:GalaxyTilePipeline?=null) {
 private val field=fieldStars?.let{FieldStarPipeline(it,density)}
 private val overlay=SkyOverlayPipeline(density)
 private val program=GlassPipeline.compileProgram(VERTEX,FRAGMENT)
 private val vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
 private val glass=GlassPipeline()
 private var texture=0;private var fbo=0;private var width=0;private var height=0
 val blurPasses get()=glass.blurPasses
 fun resize(w:Int,h:Int) {
  width=w;height=h;deleteTarget()
  val ids=IntArray(1);glGenTextures(1,ids,0);texture=ids[0];glBindTexture(GL_TEXTURE_2D,texture);GlassPipeline.parameters(GL_TEXTURE_2D)
  glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
  glGenFramebuffers(1,ids,0);fbo=ids[0];glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0)
  check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
  glBindFramebuffer(GL_FRAMEBUFFER,0);glass.resize(w,h)
 }
 fun draw(stars:Int,galaxy:Int,basis:GalacticCameraBasis,up:CameraVector?,galaxyVisible:Boolean,night:Boolean,horizontalFov:Double=70.0,masks:List<GlassMask> = emptyList(),blur:Boolean=true,frame:SkyFrameSnapshot?=null,starsVisible:Boolean=frame?.starLayers?.masterVisible?:true,twinkleSeconds:Float=0f,twinkle:Boolean=frame?.starLayers?.twinkle?:false) {
  if(width<1||height<1)return
  glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,width,height);glUseProgram(program)
  fun vector(name:String,v:CameraVector){glUniform3f(glGetUniformLocation(program,name),v.xRight.toFloat(),v.yUp.toFloat(),v.zForward.toFloat())}
  vector("uX",basis.x);vector("uY",basis.y);vector("uZ",basis.z);vector("uUp",up?:CameraVector(0.0,0.0,0.0))
  glUniform2f(glGetUniformLocation(program,"uSize"),width.toFloat(),height.toFloat())
  glUniform1f(glGetUniformLocation(program,"uFocal"),(width/(2*tan(Math.toRadians(horizontalFov)/2))).toFloat())
  glUniform1f(glGetUniformLocation(program,"uGalaxy"),if(galaxyVisible)1f else 0f);glUniform1f(glGetUniformLocation(program,"uNight"),if(night)1f else 0f)
  glUniform1f(glGetUniformLocation(program,"uStarsVisible"),if(starsVisible&&field==null)1f else 0f)
  glUniform1f(glGetUniformLocation(program,"uTwinkle"),if(twinkle&&starsVisible)1f else 0f)
  glUniform1f(glGetUniformLocation(program,"uTime"),twinkleSeconds)
  // A virtual sky map shows the full celestial sphere. The precise overlay retains the real horizon.
  glUniform1f(glGetUniformLocation(program,"uHorizon"),0f)
  glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,stars);glUniform1i(glGetUniformLocation(program,"uStars"),0)
  glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,galaxy);glUniform1i(glGetUniformLocation(program,"uDiffuse"),1)
  val a=glGetAttribLocation(program,"aPosition");vertices.position(0);glVertexAttribPointer(a,2,GL_FLOAT,false,0,vertices);glEnableVertexAttribArray(a);glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(a)
  tiles?.draw(width,height,horizontalFov,basis,null,night,galaxyVisible)
  if(starsVisible)field?.let {catalog->
   val hidden=frame?.let{StarOverlay.render(it.calculation?.stars,it.starLayers,it.projection).stars.map{s->s.hip}.toSet()}?:emptySet()
   val years=frame?.calculation?.at?.let{(Time.fromMillisecondsSince1970(it.toEpochMilli()).tt/365.25).toFloat()}?:0f
   catalog.draw(width,height,basis,null,horizontalFov,night,twinkle,twinkleSeconds,years,hidden)
  }
  // FBO pixels have bottom origin. This is the plain 2D path, not the OES camera transform.
  glass.draw(texture,GL_TEXTURE_2D,GlassPipeline.FLIP,masks,blur)
  frame?.let{overlay.draw(it,masks,twinkleSeconds)}
 }
 private fun deleteTarget(){if(texture!=0)glDeleteTextures(1,intArrayOf(texture),0);if(fbo!=0)glDeleteFramebuffers(1,intArrayOf(fbo),0);texture=0;fbo=0}
 fun release(){deleteTarget();tiles?.release();field?.release();overlay.release();glass.release();glDeleteProgram(program)}
 companion object {
  private const val VERTEX="attribute vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.,1.);}"
  private const val FRAGMENT="""precision highp float;
   uniform sampler2D uStars;uniform sampler2D uDiffuse;
   uniform vec2 uSize;uniform float uFocal;uniform vec3 uX;uniform vec3 uY;uniform vec3 uZ;uniform vec3 uUp;
   uniform float uTime;uniform float uTwinkle;uniform float uStarsVisible;uniform float uGalaxy;uniform float uNight;uniform float uHorizon;
   void main(){
    vec3 ray=normalize(vec3((gl_FragCoord.xy-uSize*.5)/uFocal,1.));
    vec3 g=vec3(dot(ray,uX),dot(ray,uY),dot(ray,uZ));
    vec2 uv=vec2(fract(.5-atan(g.y,g.x)/6.28318530718),.5-asin(clamp(g.z,-1.,1.))/3.14159265359);
    float altitude=asin(clamp(dot(ray,uUp),-1.,1.))*57.295779513;
    float fade=mix(1.,.12+.88*smoothstep(-4.,4.,altitude),uHorizon);
    // The seed is fixed on the celestial sphere, not to screen pixels or camera pose.
    float shimmer=1.;
    if(uTwinkle>.5){
    vec2 cell=floor(uv*vec2(1024.,512.));
    float seed=fract(sin(dot(cell,vec2(127.1,311.7)))*43758.5453);
    float phase=seed*6.28318530718;float frequency=6.28318530718*(9.+floor(seed*13.))/60.;
    // Center the gentle pulse on static brightness so stars both glow and fade.
    shimmer=1.+.35*sin(phase+uTime*frequency)+.10*sin(phase*.7+uTime*frequency*2.);
    }
    vec3 color=vec3(5.,10.,18.)/255.+(texture2D(uStars,uv).rgb*.95*uStarsVisible*shimmer+texture2D(uDiffuse,uv).rgb*1.05*uGalaxy)*fade*.70;
    color=clamp(color,0.,1.);float red=dot(color,vec3(.7,.15,.15));
    gl_FragColor=vec4(mix(color,vec3(red,red*.15,red*.09),uNight),1.);
   }"""
 }
}
