package local.skytrails.app.ui.renderer

import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GPU-only camera sampling, downsample, separable blur and viewport-mask compositing.
 * Public draw boundary also accepts a normal texture for independent instrumented image tests. */
class GlassPipeline {
 private val vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
  put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f));position(0)
 }
 private val plain=compileProgram(VERTEX,FRAGMENT_2D)
 private val external=runCatching {compileProgram(VERTEX,FRAGMENT_OES)}.getOrNull()
 private var blurProgram:Int?=null
 private var maskProgram:Int?=null
 private var w=0;private var h=0
 private var textures=IntArray(0);private var framebuffers=IntArray(0)
 var fallback:String?=null;private set
 var blurPasses=0L;private set
 fun resize(width:Int,height:Int) {
  w=width;h=height;deleteBuffers()
  runCatching {
   if(blurProgram==null)blurProgram=compileProgram(VERTEX,BLUR)
   if(maskProgram==null)maskProgram=compileProgram(VERTEX,MASK)
   textures=IntArray(3);framebuffers=IntArray(3);glGenTextures(3,textures,0);glGenFramebuffers(3,framebuffers,0)
   repeat(3){i->glBindTexture(GL_TEXTURE_2D,textures[i]);parameters(GL_TEXTURE_2D)
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,(w+3)/4,(h+3)/4,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
    glBindFramebuffer(GL_FRAMEBUFFER,framebuffers[i]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0)
    check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE){"Framebuffer incomplete"}
   }
  }.onSuccess{fallback=null}.onFailure{fallback="毛玻璃不可用，使用染色材质";deleteBuffers()}
  glBindFramebuffer(GL_FRAMEBUFFER,0)
 }
 fun draw(texture:Int,target:Int,sampling:FloatArray,masks:List<GlassMask>,blur:Boolean) {
  if(w<=0||h<=0)return
  glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,w,h)
  val source=if(target==GLES11Ext.GL_TEXTURE_EXTERNAL_OES)requireNotNull(external){"External camera texture unsupported"} else plain
  quad(source,texture,target,sampling)
  if(!blur||masks.isEmpty()||framebuffers.size!=3)return
  val bw=(w+3)/4;val bh=(h+3)/4
  glBindFramebuffer(GL_FRAMEBUFFER,framebuffers[0]);glViewport(0,0,bw,bh);quad(source,texture,target,sampling)
  glBindFramebuffer(GL_FRAMEBUFFER,framebuffers[1]);quad(blurProgram!!,textures[0],GL_TEXTURE_2D,FLIP){p->glUniform2f(glGetUniformLocation(p,"uStep"),1f/bw,0f)}
  glBindFramebuffer(GL_FRAMEBUFFER,framebuffers[2]);quad(blurProgram!!,textures[1],GL_TEXTURE_2D,FLIP){p->glUniform2f(glGetUniformLocation(p,"uStep"),0f,1f/bh)}
  blurPasses+=2
  glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,w,h)
  masks.take(8).forEach {m->quad(maskProgram!!,textures[2],GL_TEXTURE_2D,FLIP){p->
   glUniform4f(glGetUniformLocation(p,"uRect"),m.left,m.top,m.right,m.bottom)
   glUniform2f(glGetUniformLocation(p,"uSize"),w.toFloat(),h.toFloat());glUniform1f(glGetUniformLocation(p,"uRadius"),m.radius)
  }}
 }
 fun clear(){glBindFramebuffer(GL_FRAMEBUFFER,0);glClearColor(.08f,.13f,.17f,1f);glClear(GL_COLOR_BUFFER_BIT)}
 private fun quad(p:Int,texture:Int,target:Int,matrix:FloatArray,uniforms:(Int)->Unit={}) {
  glUseProgram(p);glActiveTexture(GL_TEXTURE0);glBindTexture(target,texture);glUniform1i(glGetUniformLocation(p,"uTex"),0)
  glUniformMatrix4fv(glGetUniformLocation(p,"uTransform"),1,false,matrix,0);uniforms(p)
  val a=glGetAttribLocation(p,"aPosition");val b=glGetAttribLocation(p,"aUv")
  vertices.position(0);glVertexAttribPointer(a,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(a)
  vertices.position(2);glVertexAttribPointer(b,2,GL_FLOAT,false,16,vertices);glEnableVertexAttribArray(b)
  glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(a);glDisableVertexAttribArray(b)
 }
 private fun deleteBuffers(){if(textures.isNotEmpty())glDeleteTextures(textures.size,textures,0);if(framebuffers.isNotEmpty())glDeleteFramebuffers(framebuffers.size,framebuffers,0);textures=IntArray(0);framebuffers=IntArray(0)}
 fun release(){deleteBuffers();listOfNotNull(plain,external,blurProgram,maskProgram).forEach(::glDeleteProgram)}
 companion object {
  val FLIP=floatArrayOf(1f,0f,0f,0f,0f,-1f,0f,0f,0f,0f,1f,0f,0f,1f,0f,1f)
  fun parameters(target:Int){glTexParameteri(target,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(target,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(target,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(target,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)}
  internal fun compileProgram(vertex:String,fragment:String):Int {
   fun shader(type:Int,code:String):Int {val s=glCreateShader(type);glShaderSource(s,code);glCompileShader(s);val status=IntArray(1);glGetShaderiv(s,GL_COMPILE_STATUS,status,0);if(status[0]==0){val log=glGetShaderInfoLog(s);glDeleteShader(s);error(log)};return s}
   val v=shader(GL_VERTEX_SHADER,vertex);val f=try{shader(GL_FRAGMENT_SHADER,fragment)}catch(e:Exception){glDeleteShader(v);throw e};val p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);glDeleteShader(v);glDeleteShader(f)
   val status=IntArray(1);glGetProgramiv(p,GL_LINK_STATUS,status,0);if(status[0]==0){val log=glGetProgramInfoLog(p);glDeleteProgram(p);error(log)};return p
  }
  private const val VERTEX="attribute vec2 aPosition; attribute vec2 aUv; uniform mat4 uTransform; varying vec2 vUv; varying vec2 vView; void main(){gl_Position=vec4(aPosition,0.,1.);vView=aUv;vUv=(uTransform*vec4(aUv,0.,1.)).xy;}"
  private const val FRAGMENT_2D="precision mediump float; uniform sampler2D uTex; varying vec2 vUv; void main(){gl_FragColor=texture2D(uTex,vUv);}"
  private const val FRAGMENT_OES="#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uTex; varying vec2 vUv; void main(){gl_FragColor=texture2D(uTex,vUv);}"
  private const val BLUR="precision mediump float; uniform sampler2D uTex;uniform vec2 uStep;varying vec2 vUv;void main(){vec4 c=texture2D(uTex,vUv)*0.227027; c+=(texture2D(uTex,vUv+uStep*1.)+texture2D(uTex,vUv-uStep*1.))*0.1945946;c+=(texture2D(uTex,vUv+uStep*2.)+texture2D(uTex,vUv-uStep*2.))*0.1216216;c+=(texture2D(uTex,vUv+uStep*3.)+texture2D(uTex,vUv-uStep*3.))*0.054054;c+=(texture2D(uTex,vUv+uStep*4.)+texture2D(uTex,vUv-uStep*4.))*0.016216;gl_FragColor=c;}"
  private const val MASK="precision mediump float;uniform sampler2D uTex;uniform vec4 uRect;uniform vec2 uSize;uniform float uRadius;varying vec2 vUv;varying vec2 vView;void main(){vec2 p=vView*uSize;vec2 halfSize=(uRect.zw-uRect.xy)*.5;float r=min(uRadius,min(halfSize.x,halfSize.y));vec2 q=abs(p-(uRect.xy+uRect.zw)*.5)-halfSize+vec2(r);float d=length(max(q,0.))+min(max(q.x,q.y),0.)-r;if(d>0.)discard;gl_FragColor=texture2D(uTex,vUv);}"
 }
}
