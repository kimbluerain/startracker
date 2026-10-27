package local.skytrails.app.ui.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES20.*
import android.opengl.GLUtils
import local.skytrails.app.ar.Pixel
import local.skytrails.app.core.SkyDirection
import local.skytrails.app.ui.SkyTarget
import local.skytrails.app.ui.sky.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Small vector batches and cached label textures, composed into the sky's SAME GL output. */
class SkyOverlayPipeline(private val density:Float=1f) {
 private val flat=GlassPipeline.compileProgram(VERTEX,FRAGMENT)
 private val text=GlassPipeline.compileProgram(TEXT_VERTEX,TEXT_FRAGMENT)
 private data class Label(val texture:Int,val width:Int,val height:Int)
 private val labels=mutableMapOf<String,Label>()
 private data class LabelJob(val text:String,val x:Double,val y:Double,val color:FloatArray,val alpha:Float)
 private val labelJobs=ArrayList<LabelJob>(8)
 private var vertices=FloatArray(65536);private var used=0
 private var vertexBuffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
 private var width=0;private var height=0;private var masks:List<GlassMask> = emptyList()
 private val silver=floatArrayOf(.949f,.941f,.929f,1f)
 private val gold=floatArrayOf(.918f,.773f,.541f,1f)
 private val galaxy=floatArrayOf(.843f,.784f,.710f,1f)
 private val red=floatArrayOf(1f,.459f,.392f,1f)
 fun draw(frame:SkyFrameSnapshot,exclusions:List<GlassMask>,twinkleSeconds:Float=0f) {
  used=0;labelJobs.clear()
  width=frame.projection.width;height=frame.projection.height;masks=exclusions
  if(width<=0||height<=0)return
  glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA)
  val ink=if(frame.night)red else silver;val projection=frame.projection
  GalacticOverlay.path((0..360 step 2).map{SkyDirection(it.toDouble(),0.0)},projection).forEach{line(it.start,it.end,ink,.25f,1f,true)}
  listOf(0.0 to "北 N",90.0 to "东 E",180.0 to "南 S",270.0 to "西 W").forEach{(a,t)->
   val p=GalacticOverlay.target(SkyDirection(a,0.0),projection)
   if(p is TargetIndicator.Visible)label(t,p.pixel.x+5*density,p.pixel.y+8*density,ink,.7f)
  }
  frame.calculation?.let {c->frame.layers.visibleTargets.forEach {target->
   val color=if(frame.night)red else when(target){SkyTarget.SUN->gold;SkyTarget.MOON->silver;SkyTarget.GALAXY->galaxy}
   val segments=when(target){SkyTarget.SUN->GalacticOverlay.path(c.sunPath,projection);SkyTarget.MOON->GalacticOverlay.path(c.moonPath,projection);SkyTarget.GALAXY->GalacticOverlay.render(c.galaxy,projection).segments}
   segments.forEach {line(it.start,it.end,color,if(it.line==GalacticLine.BAND_EDGE).14f else if(target==SkyTarget.GALAXY).45f else if(it.belowHorizon).35f else .7f,if(it.line==GalacticLine.BAND_EDGE).6f else 1f,if(target==SkyTarget.MOON)false else it.belowHorizon||target==SkyTarget.GALAXY)}
   val direction=when(target){SkyTarget.SUN->c.sun;SkyTarget.MOON->c.moon;SkyTarget.GALAXY->c.galaxy.center}
   when(val p=GalacticOverlay.target(direction,projection)) {
    is TargetIndicator.Visible->{
     circle(p.pixel,26f,color,.05f);circle(p.pixel,18f,color,.08f);circle(p.pixel,if(target==SkyTarget.GALAXY)4f else 6f,color,1f)
     ring(p.pixel,11f,color,.7f)
     label(when(target){SkyTarget.SUN->"太阳";SkyTarget.MOON->"月亮";SkyTarget.GALAXY->"银心"},p.pixel.x+17*density,p.pixel.y-9*density,color,1f)
    }
    is TargetIndicator.Edge->{val dx=cos(p.angleRadians);val dy=sin(p.angleRadians);val q=p.pixel;line(Pixel(q.x-dx*12-dy*6,q.y-dy*12+dx*6),q,color,1f,1f,false);line(Pixel(q.x-dx*12+dy*6,q.y-dy*12-dx*6),q,color,1f,1f,false)}
    else->Unit
   }
  }}
  frame.calculation?.let{c->
   val stars=StarOverlay.render(c.stars,frame.starLayers,projection)
   stars.lines.forEach{stroke->val focused=stroke.id==frame.starFocusId;val color=if(frame.night)red else if(focused)gold else silver
    line(stroke.segment.start,stroke.segment.end,color,if(focused).54f else .28f,if(focused)1.05f else .75f,false)}
   stars.stars.forEach{s->
    val radius=(3.4-s.magnitude*.55).coerceIn(1.15,3.5).toFloat()
    val brightness=StarTwinkle.brightness(s.hip,twinkleSeconds,frame.starLayers.twinkle)
    StarSpikes.profile(s.magnitude,frame.starLayers.twinkle)?.let{profile->spikes(s.pixel,profile,ink,profile.strength*StarSpikes.intensity(s.hip,twinkleSeconds,s.below))}
    circle(s.pixel,radius+4f,ink,.05f*brightness);circle(s.pixel,radius,ink,(if(s.below).55f else .92f)*brightness)
   }
   stars.names.forEach{label(it.text,it.pixel.x+12*density,it.pixel.y+15*density,if(frame.night)red else if(it.id==frame.starFocusId)gold else silver,if(it.id==frame.starFocusId).95f else .6f)}
   if(frame.layers.moon)moonEndpointLabels(c,projection).forEach{endpoint->
    ring(endpoint.pixel,3.5f,ink,.75f);val textAt=moonEndpointLabelPosition(endpoint,GalacticOverlay.target(c.moon,projection),density);label(endpoint.text,textAt.x,textAt.y,ink,.78f)
   }
  }
  flushVertices(ink);labelJobs.forEach{drawLabel(it.text,it.x,it.y,it.color,it.alpha)}
  glDisable(GL_BLEND)
 }
 /** Each arm stays on a screen axis. Only vertex alpha changes with the visual clock. */
 private fun spikes(p:Pixel,profile:StarSpikeProfile,color:FloatArray,alpha:Float) {
  val x=p.x.toFloat();val y=p.y.toFloat();val halo=profile.haloRadius*density
  for(i in 0 until 36) {
   val a=i*PI/18;val b=(i+1)*PI/18
   polygon(floatArrayOf(x,y,x+cos(a).toFloat()*halo,y+sin(a).toFloat()*halo,x+cos(b).toFloat()*halo,y+sin(b).toFloat()*halo),color,alpha*StarSpikes.HALO_ALPHA,floatArrayOf(1f,0f,0f))
  }
  val length=profile.halfLength*density
  for(axis in 0 until 4) {
   val dx=when(axis){0->1f;2->-1f;else->0f};val dy=when(axis){1->1f;3->-1f;else->0f}
   fun arm(halfWidth:Float,opacity:Float) {
    val nx=-dy*halfWidth*density;val ny=dx*halfWidth*density
    polygon(floatArrayOf(x+nx,y+ny,x+dx*length,y+dy*length,x-nx,y-ny),color,alpha*opacity,floatArrayOf(1f,0f,1f))
   }
   arm(profile.halfWidth*StarSpikes.SOFT_WIDTH_SCALE,StarSpikes.SOFT_ALPHA)
   arm(profile.halfWidth,StarSpikes.CORE_ALPHA)
  }
 }
 private fun line(a:Pixel,b:Pixel,color:FloatArray,alpha:Float,stroke:Float,dash:Boolean) {
  val dx=b.x-a.x;val dy=b.y-a.y;val length=hypot(dx,dy);if(length<.01)return
  val nx=(-dy/length*stroke*density/2).toFloat();val ny=(dx/length*stroke*density/2).toFloat()
  fun segment(t0:Double,t1:Double){val ax=(a.x+dx*t0).toFloat();val ay=(a.y+dy*t0).toFloat();val bx=(a.x+dx*t1).toFloat();val by=(a.y+dy*t1).toFloat()
   polygon(floatArrayOf(ax+nx,ay+ny,ax-nx,ay-ny,bx+nx,by+ny,bx+nx,by+ny,ax-nx,ay-ny,bx-nx,by-ny),color,alpha)}
  if(!dash)segment(0.0,1.0) else {var p=0.0;val on=7*density;val period=13*density;while(p<length){segment(p/length,min(p+on,length)/length);p+=period}}
 }
 private fun circle(p:Pixel,r:Float,color:FloatArray,alpha:Float){val points=ArrayList<Float>(36*6);for(i in 0 until 36){val a=i*PI/18;val b=(i+1)*PI/18;points.addAll(listOf(p.x.toFloat(),p.y.toFloat(),(p.x+cos(a)*r*density).toFloat(),(p.y+sin(a)*r*density).toFloat(),(p.x+cos(b)*r*density).toFloat(),(p.y+sin(b)*r*density).toFloat()))};polygon(points.toFloatArray(),color,alpha)}
 private fun ring(p:Pixel,r:Float,color:FloatArray,alpha:Float){for(i in 0 until 36){val a=i*PI/18;val b=(i+1)*PI/18;line(Pixel(p.x+cos(a)*r*density,p.y+sin(a)*r*density),Pixel(p.x+cos(b)*r*density,p.y+sin(b)*r*density),color,alpha,.8f,false)}}
 private fun uniforms(program:Int,color:FloatArray,alpha:Float) {
  glUseProgram(program);glUniform2f(glGetUniformLocation(program,"uSize"),width.toFloat(),height.toFloat());glUniform4f(glGetUniformLocation(program,"uColor"),color[0],color[1],color[2],alpha)
  val rects=FloatArray(32);val radii=FloatArray(8);masks.take(8).forEachIndexed{i,m->rects[i*4]=m.left;rects[i*4+1]=m.top;rects[i*4+2]=m.right;rects[i*4+3]=m.bottom;radii[i]=m.radius}
  glUniform1i(glGetUniformLocation(program,"uCount"),min(8,masks.size));glUniform4fv(glGetUniformLocation(program,"uMasks"),8,rects,0);glUniform1fv(glGetUniformLocation(program,"uRadii"),8,radii,0)
 }
 private fun polygon(points:FloatArray,color:FloatArray,alpha:Float,vertexAlphas:FloatArray?=null) {
  val needed=used+points.size*3
  if(needed>vertices.size){vertices=vertices.copyOf(max(needed,vertices.size*2));vertexBuffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()}
  for(i in points.indices step 2){vertices[used++]=points[i];vertices[used++]=points[i+1];vertices[used++]=color[0];vertices[used++]=color[1];vertices[used++]=color[2];vertices[used++]=alpha*(vertexAlphas?.get(i/2)?:1f)}
 }
 private fun flushVertices(ink:FloatArray) {
  if(used==0)return
  uniforms(flat,ink,1f);vertexBuffer.clear();vertexBuffer.put(vertices,0,used);vertexBuffer.position(0)
  val a=glGetAttribLocation(flat,"aPosition");val color=glGetAttribLocation(flat,"aColor")
  glVertexAttribPointer(a,2,GL_FLOAT,false,24,vertexBuffer);vertexBuffer.position(2);glVertexAttribPointer(color,4,GL_FLOAT,false,24,vertexBuffer)
  glEnableVertexAttribArray(a);glEnableVertexAttribArray(color);glDrawArrays(GL_TRIANGLES,0,used/6);glDisableVertexAttribArray(a);glDisableVertexAttribArray(color)
 }
 private fun label(value:String,x:Double,y:Double,color:FloatArray,alpha:Float){labelJobs.add(LabelJob(value,x,y,color,alpha))}
 private fun drawLabel(value:String,x:Double,y:Double,color:FloatArray,alpha:Float) {
  if(value !in labels && labels.size>=96){val oldest=labels.keys.first();labels.remove(oldest)?.let{glDeleteTextures(1,intArrayOf(it.texture),0)}}
  val label=labels.getOrPut(value){
   val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=android.graphics.Color.WHITE;textSize=12*density};val metrics=paint.fontMetrics
   val w=ceil(paint.measureText(value)+4).toInt();val h=ceil(metrics.descent-metrics.ascent+4).toInt();val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
   Canvas(bitmap).drawText(value,2f,2-metrics.ascent,paint);val id=IntArray(1);glGenTextures(1,id,0);glBindTexture(GL_TEXTURE_2D,id[0]);GlassPipeline.parameters(GL_TEXTURE_2D);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();Label(id[0],w,h)
  }
  val left=x.toFloat().coerceIn(2f,max(2f,width-label.width-2f));val top=y.toFloat().coerceIn(2f,max(2f,height-label.height-2f));val right=left+label.width;val bottom=top+label.height
  uniforms(text,color,alpha);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,label.texture);glUniform1i(glGetUniformLocation(text,"uTex"),0)
  val data=buffer(floatArrayOf(left,top,0f,0f,right,top,1f,0f,left,bottom,0f,1f,right,bottom,1f,1f));val a=glGetAttribLocation(text,"aPosition");val uv=glGetAttribLocation(text,"aUv")
  data.position(0);glVertexAttribPointer(a,2,GL_FLOAT,false,16,data);data.position(2);glVertexAttribPointer(uv,2,GL_FLOAT,false,16,data);glEnableVertexAttribArray(a);glEnableVertexAttribArray(uv);glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(a);glDisableVertexAttribArray(uv)
 }
 fun release(){glDeleteProgram(flat);glDeleteProgram(text);labels.values.forEach{glDeleteTextures(1,intArrayOf(it.texture),0)};labels.clear()}
 private fun buffer(points:FloatArray)=ByteBuffer.allocateDirect(points.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(points);position(0)}
 companion object {
  private const val VERTEX="attribute vec2 aPosition;attribute vec4 aColor;uniform vec2 uSize;varying vec2 vPixel;varying vec4 vColor;void main(){vColor=aColor;vPixel=aPosition;gl_Position=vec4(aPosition.x/uSize.x*2.-1.,1.-aPosition.y/uSize.y*2.,0.,1.);}"
  private const val TEXT_VERTEX="attribute vec2 aPosition;attribute vec2 aUv;uniform vec2 uSize;varying vec2 vPixel;varying vec2 vUv;void main(){vPixel=aPosition;vUv=aUv;gl_Position=vec4(aPosition.x/uSize.x*2.-1.,1.-aPosition.y/uSize.y*2.,0.,1.);}"
  private const val CLIP="uniform vec4 uMasks[8];uniform float uRadii[8];uniform int uCount;varying vec2 vPixel;void clipMasks(){for(int i=0;i<8;i++){if(i<uCount){vec4 rect=uMasks[i];vec2 halfSize=(rect.zw-rect.xy)*.5;float r=min(uRadii[i],min(halfSize.x,halfSize.y));vec2 q=abs(vPixel-(rect.xy+rect.zw)*.5)-halfSize+vec2(r);float d=length(max(q,0.))+min(max(q.x,q.y),0.)-r;if(d<=0.)discard;}}}"
  private const val FRAGMENT="precision mediump float;varying vec4 vColor;"+CLIP+"void main(){clipMasks();gl_FragColor=vColor;}"
  private const val TEXT_FRAGMENT="precision mediump float;uniform sampler2D uTex;uniform vec4 uColor;varying vec2 vUv;"+CLIP+"void main(){clipMasks();gl_FragColor=vec4(uColor.rgb,uColor.a*texture2D(uTex,vUv).a);}"
 }
}
