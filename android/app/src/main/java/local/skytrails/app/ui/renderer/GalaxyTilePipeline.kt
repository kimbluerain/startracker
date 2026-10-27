package local.skytrails.app.ui.renderer

import android.opengl.GLES20.*
import android.opengl.GLUtils
import local.skytrails.app.ar.CameraVector
import local.skytrails.app.ui.sky.GalacticCameraBasis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Exact inverse projection over the 2K fallback; missing tiles can never leave holes. */
class GalaxyTilePipeline(private val loader:GalaxyTileLoader) {
 private val program=GlassPipeline.compileProgram(VERTEX,FRAGMENT)
 private val vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
 private val textures=LinkedHashMap<GalaxyTile,Int>(64,.75f,true)
 private val failed=mutableSetOf<GalaxyTile>()
 var lastError:String?=null;private set
 var activeLevel=2048;private set
 val residentBytes get()=textures.size*GalaxyTiles.TILE_BYTES
 fun draw(width:Int,height:Int,fov:Double,basis:GalacticCameraBasis,up:CameraVector?,night:Boolean,visible:Boolean) {
  val plan=if(visible)GalaxyTiles.select(width,height,fov,basis)else GalaxyTilePlan(2048,emptyList())
  activeLevel=plan.level
  val needed=plan.tiles.toSet()
  for((key,bitmap) in loader.takeReady()) {
   try {
    if(key !in needed)continue
    while(textures.size>=GalaxyTiles.MAX_RESIDENT&&key !in textures) {
     val oldest=textures.keys.firstOrNull{it !in needed}?:textures.keys.first()
     glDeleteTextures(1,intArrayOf(textures.remove(oldest)!!),0)
    }
    val id=IntArray(1);glGenTextures(1,id,0);glBindTexture(GL_TEXTURE_2D,id[0]);GlassPipeline.parameters(GL_TEXTURE_2D)
    GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0)
    val error=glGetError();if(error!=GL_NO_ERROR){glDeleteTextures(1,id,0);failed+=key;lastError="Galaxy tile upload failed: $error";continue}
    textures.put(key,id[0])?.let{glDeleteTextures(1,intArrayOf(it),0)}
   }finally{bitmap.recycle()}
  }
  loader.request(plan.tiles.filter{it !in textures&&it !in failed})
  if(!visible)return
  glUseProgram(program);glDisable(GL_BLEND)
  fun vector(name:String,v:CameraVector){glUniform3f(glGetUniformLocation(program,name),v.xRight.toFloat(),v.yUp.toFloat(),v.zForward.toFloat())}
  vector("uX",basis.x);vector("uY",basis.y);vector("uZ",basis.z);vector("uUp",up?:CameraVector(0.0,0.0,0.0))
  val focal=width/(2*tan(Math.toRadians(fov)/2))
  glUniform2f(glGetUniformLocation(program,"uSize"),width.toFloat(),height.toFloat());glUniform1f(glGetUniformLocation(program,"uFocal"),focal.toFloat())
  glUniform1f(glGetUniformLocation(program,"uNight"),if(night)1f else 0f);glUniform1f(glGetUniformLocation(program,"uHorizon"),if(up!=null)1f else 0f)
  glActiveTexture(GL_TEXTURE0);glUniform1i(glGetUniformLocation(program,"uTile"),0)
  val a=glGetAttribLocation(program,"aPosition");vertices.position(0);glVertexAttribPointer(a,2,GL_FLOAT,false,0,vertices);glEnableVertexAttribArray(a)
  glEnable(GL_SCISSOR_TEST)
  for(tile in plan.tiles)textures[tile]?.let{id->
   val bounds=scissor(tile,basis,focal,width,height);if(bounds[2]<=0||bounds[3]<=0)return@let
   glScissor(bounds[0],bounds[1],bounds[2],bounds[3]);glBindTexture(GL_TEXTURE_2D,id)
   glUniform3f(glGetUniformLocation(program,"uTileOrigin"),(tile.x*512).toFloat(),(tile.y*512).toFloat(),tile.level.toFloat())
   glDrawArrays(GL_TRIANGLE_STRIP,0,4)
  }
  glDisable(GL_SCISSOR_TEST);glDisableVertexAttribArray(a)
 }
 // Conservative cap projection bounds only reduce fragment work; the shader clips exact UV edges.
 private fun scissor(tile:GalaxyTile,basis:GalacticCameraBasis,focal:Double,w:Int,h:Int):IntArray {
  val c=GalaxyTiles.center(tile,basis);val r=2*sin(GalaxyTiles.radius(tile)/2)
  if(c.zForward-r<=.001)return intArrayOf(0,0,w,h)
  fun extent(value:Double,center:Double,limit:Int):Pair<Int,Int> {
   val ratios=listOf((value-r)/(c.zForward-r),(value-r)/(c.zForward+r),(value+r)/(c.zForward-r),(value+r)/(c.zForward+r))
   return floor(center+focal*ratios.min()).toInt().coerceIn(0,limit) to ceil(center+focal*ratios.max()).toInt().coerceIn(0,limit)
  }
  val x=extent(c.xRight,w/2.0,w);val y=extent(c.yUp,h/2.0,h)
  return intArrayOf(x.first,y.first,x.second-x.first,y.second-y.first)
 }
 fun release(){textures.values.forEach{glDeleteTextures(1,intArrayOf(it),0)};textures.clear();glDeleteProgram(program)}
 companion object {
  private const val VERTEX="attribute vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.,1.);}"
  private const val FRAGMENT="""precision highp float;
   uniform sampler2D uTile;uniform vec3 uTileOrigin;uniform vec2 uSize;uniform float uFocal;
   uniform vec3 uX;uniform vec3 uY;uniform vec3 uZ;uniform vec3 uUp;uniform float uNight;uniform float uHorizon;
   void main(){
    vec3 ray=normalize(vec3((gl_FragCoord.xy-uSize*.5)/uFocal,1.));vec3 g=vec3(dot(ray,uX),dot(ray,uY),dot(ray,uZ));
    vec2 uv=vec2(fract(.5-atan(g.y,g.x)/6.28318530718),.5-asin(clamp(g.z,-1.,1.))/3.14159265359);
    vec2 local=uv*vec2(uTileOrigin.z,uTileOrigin.z*.5)-uTileOrigin.xy;
    if(local.x<0.||local.y<0.||local.x>=512.||local.y>=512.)discard;
    float altitude=asin(clamp(dot(ray,uUp),-1.,1.))*57.295779513;float fade=mix(1.,.12+.88*smoothstep(-4.,4.,altitude),uHorizon);
    vec3 color=clamp(vec3(5.,10.,18.)/255.+texture2D(uTile,(local+1.)/514.).rgb*1.05*fade*.70,0.,1.);
    float red=dot(color,vec3(.7,.15,.15));gl_FragColor=vec4(mix(color,vec3(red,red*.15,red*.09),uNight),1.);
   }"""
 }
}
