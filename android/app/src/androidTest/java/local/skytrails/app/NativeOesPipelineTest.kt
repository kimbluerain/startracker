package local.skytrails.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.EGL14.*
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES20.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import local.skytrails.app.ar.*
import local.skytrails.app.ui.renderer.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** A real top-origin native buffer, not a bottom-up glTexImage2D stand-in.
 * Canvas -> Surface -> SurfaceTexture -> production OES/blur/mask shaders -> GL readback.
 * Sensor mount=0 is a declared synthetic producer fixture; no physical camera is opened. */
@RunWith(AndroidJUnit4::class)
class NativeOesPipelineTest {
    @Test fun nativeCornersAndOffCenterOverlayAgreeAtAllFourDisplayRotations() = withNativeFrame { frame ->
        for (display in listOf(0, 90, 180, 270)) {
            val preview = preview(display)
            val matrix = sampling(preview, frame.actual)
            assertTrue("Native Canvas producer must qualify for the synthetic mount", GlassSampling.residualAllowsOverlay(preview, frame.actual3))
            frame.pipeline.resize(preview.viewWidth, preview.viewHeight)
            frame.pipeline.draw(frame.texture, GL_TEXTURE_EXTERNAL_OES, matrix, emptyList(), false)
            val image = read(preview.viewWidth, preview.viewHeight)
            assertMarkers(image, display)
            val pixel = capture().projectNatural(CameraVector(-.375, .25, 1.0), preview)
            assertEquals(marker(display), pixel)
            assertColor("overlay and image marker D=$display", image, pixel!!, Color.MAGENTA)
        }
    }

    @Test fun offCenterGlassMaskKeepsNativeImagePositionAndOnlyBlursItsContents() = withNativeFrame { frame ->
        val mask = GlassMask(44f, 88f, 124f, 160f, 8f)
        for (display in listOf(0, 90, 180, 270)) {
            val preview = preview(display)
            val matrix = sampling(preview, frame.actual)
            frame.pipeline.resize(preview.viewWidth, preview.viewHeight)
            frame.pipeline.draw(frame.texture, GL_TEXTURE_EXTERNAL_OES, matrix, emptyList(), false)
            val sharp = read(preview.viewWidth, preview.viewHeight)
            val passes = frame.pipeline.blurPasses
            frame.pipeline.draw(frame.texture, GL_TEXTURE_EXTERNAL_OES, matrix, listOf(mask), true)
            val glass = read(preview.viewWidth, preview.viewHeight)
            assertNull(frame.pipeline.fallback)
            assertEquals(passes + 2, frame.pipeline.blurPasses)
            assertMarkers(sharp, display)
            assertMarkers(glass, display)
            for (y in 0 until glass.height) for (x in 0 until glass.width) {
                if (x < 44 || x >= 124 || y < 88 || y >= 160) {
                    for (channel in 0..3) assertEquals("Outside mask D=$display ($x,$y,$channel)", sharp.at(x,y,channel), glass.at(x,y,channel))
                }
            }
            // R/G encode independent horizontal/vertical ramps. Unlike a symmetric checker,
            // these catch an accidental FBO reflection even when blur variance still passes.
            for (point in listOf(Pixel(72.0, 116.0), Pixel(88.0, 128.0))) for (channel in 0..1) {
                val x=point.x.toInt(); val y=point.y.toInt()
                assertTrue("Blur must stay at the same image position D=$display channel=$channel",
                    abs(sharp.at(x,y,channel)-glass.at(x,y,channel)) <= 6)
            }
            assertTrue("Masked checkerboard must lose contrast D=$display", glass.blueVariance() < sharp.blueVariance() * .25)
            frame.pipeline.draw(frame.texture, GL_TEXTURE_EXTERNAL_OES, matrix, listOf(mask), false)
            assertEquals(passes + 2, frame.pipeline.blurPasses)
            assertArrayEquals(sharp.bytes, read(preview.viewWidth, preview.viewHeight).bytes)
        }
    }

    @Test fun unknownMountUsesProducerMatrixWithCorrectQuadOriginAndNoOpticalQualification() = withNativeFrame { frame ->
        frame.pipeline.resize(W, H)
        frame.pipeline.draw(frame.texture, GL_TEXTURE_EXTERNAL_OES,
            GlassSampling.unmountedViewToSampler(frame.actual), emptyList(), false)
        assertMarkers(read(W,H), 0)
        val unmounted = capture().copy(sensorDegrees = null)
        assertEquals(OpticsQuality.UNAVAILABLE, unmounted.quality)
        assertNull(unmounted.projectNatural(CameraVector(-.375,.25,1.0), preview(0)))
    }

    private fun preview(display:Int):PreviewGeometry {
        val swapped = display % 180 != 0
        return PreviewGeometry.create(1,W,H,if(swapped)H else W,if(swapped)W else H,0,display,7)
    }
    private fun capture() = CaptureGeometry(7,123,"native-canvas-fixture",null,"native-canvas-fixture",LensLock.DIRECT_REAR,
        0,W,H,OpticsMetadata(4.0,8.0,6.0,W,H,RectD(0.0,0.0,W.toDouble(),H.toDouble())),
        RectD(0.0,0.0,W.toDouble(),H.toDouble()),1f,1,0,0,0,1,RawCalibration(null,null),"synthetic test fixture")

    // Literal screen positions of the native (80,64) marker; independent of PreviewGeometry.
    private fun marker(display:Int) = when(display) {
        0 -> Pixel(80.0,64.0); 90 -> Pixel(64.0,176.0)
        180 -> Pixel(176.0,128.0); else -> Pixel(128.0,80.0)
    }
    private fun assertMarkers(image:Pixels,display:Int) {
        // Each 40px corner patch is probed at local (32,32), below/right of its label.
        // Raw R/G/B/Y probes: (32,32), (248,32), (32,184), (248,184).
        // Literal rotated positions remain independent of the production matrices.
        val positions = when(display) {
            0 -> listOf(Pixel(32.0,32.0),Pixel(248.0,32.0),Pixel(32.0,184.0),Pixel(248.0,184.0))
            90 -> listOf(Pixel(32.0,224.0),Pixel(32.0,8.0),Pixel(184.0,224.0),Pixel(184.0,8.0))
            180 -> listOf(Pixel(224.0,160.0),Pixel(8.0,160.0),Pixel(224.0,8.0),Pixel(8.0,8.0))
            else -> listOf(Pixel(160.0,32.0),Pixel(160.0,248.0),Pixel(8.0,32.0),Pixel(8.0,248.0))
        }
        positions.zip(listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)).forEachIndexed { i,(point,color) ->
            assertColor("Native corner $i D=$display",image,point,color)
        }
        assertColor("Off-center native marker D=$display",image,marker(display),Color.MAGENTA)
    }
    private fun assertColor(message:String,image:Pixels,point:Pixel,color:Int) {
        for ((channel,want) in listOf(Color.red(color),Color.green(color),Color.blue(color)).withIndex()) {
            assertEquals("$message channel=$channel",want.toDouble(),image.at(point.x.toInt(),point.y.toInt(),channel).toDouble(),4.0)
        }
    }
    private fun sampling(preview:PreviewGeometry,actual:FloatArray):FloatArray {
        val matrix=(GlassSampling.viewToSampler(preview,affine(actual))*Mat3.scaleTranslate(preview.viewWidth.toDouble(),preview.viewHeight.toDouble())).values()
        return floatArrayOf(matrix[0].toFloat(),matrix[3].toFloat(),0f,0f,matrix[1].toFloat(),matrix[4].toFloat(),0f,0f,0f,0f,1f,0f,matrix[2].toFloat(),matrix[5].toFloat(),0f,1f)
    }
    private fun affine(m:FloatArray)=Mat3.of(m[0].toDouble(),m[4].toDouble(),m[12].toDouble(),m[1].toDouble(),m[5].toDouble(),m[13].toDouble())
    private class Pixels(val width:Int,val height:Int,val bytes:ByteArray) {
        fun at(x:Int,y:Int,channel:Int)=bytes[((height-1-y)*width+x)*4+channel].toInt() and 255
        fun blueVariance():Double {
            val values=(108..140).flatMap { y -> (64..104).map { x -> at(x,y,2).toDouble() } }
            val mean=values.average();return values.sumOf { (it-mean)*(it-mean) } / values.size
        }
    }
    private fun read(width:Int,height:Int):Pixels {
        val buffer=ByteBuffer.allocateDirect(width*height*4)
        glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,buffer)
        assertEquals(GL_NO_ERROR,glGetError())
        return Pixels(width,height,ByteArray(buffer.capacity()).also { buffer.get(it) })
    }
    private data class NativeFrame(val texture:Int,val actual:FloatArray,val actual3:Mat3,val pipeline:GlassPipeline)

    private fun withNativeFrame(block:(NativeFrame)->Unit) {
        verifyFixtureSourcePixels()
        val display=eglGetDisplay(EGL_DEFAULT_DISPLAY)
        val version=IntArray(2);assertTrue(eglInitialize(display,version,0,version,1))
        var context=EGL_NO_CONTEXT;var output=EGL_NO_SURFACE
        var input:SurfaceTexture?=null;var producer:Surface?=null;var pipeline:GlassPipeline?=null
        val texture=IntArray(1)
        val callbacks=HandlerThread("NativeOesFixture").apply { start() }
        try {
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
            assertTrue(eglChooseConfig(display,intArrayOf(EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,
                EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,count,0))
            context=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE),0)
            output=eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL_WIDTH,W,EGL_HEIGHT,W,EGL_NONE),0)
            assertTrue(eglMakeCurrent(display,output,output,context))
            glGenTextures(1,texture,0);glBindTexture(GL_TEXTURE_EXTERNAL_OES,texture[0]);GlassPipeline.parameters(GL_TEXTURE_EXTERNAL_OES)
            val ready=CountDownLatch(1)
            input=SurfaceTexture(texture[0]).apply {
                setDefaultBufferSize(W,H)
                setOnFrameAvailableListener({ ready.countDown() },Handler(callbacks.looper))
            }
            producer=Surface(input)
            val canvas=producer.lockCanvas(null)
            try { drawFixture(canvas) } finally { producer.unlockCanvasAndPost(canvas) }
            assertTrue("Native Canvas frame did not arrive",ready.await(5,TimeUnit.SECONDS))
            input.updateTexImage()
            val actual=FloatArray(16);input.getTransformMatrix(actual)
            pipeline=GlassPipeline()
            block(NativeFrame(texture[0],actual,affine(actual),pipeline))
        } finally {
            pipeline?.release();producer?.release();input?.release()
            if(texture[0]!=0)glDeleteTextures(1,texture,0)
            eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT)
            if(output!=EGL_NO_SURFACE)eglDestroySurface(display,output)
            if(context!=EGL_NO_CONTEXT)eglDestroyContext(display,context)
            eglTerminate(display);callbacks.quitSafely();callbacks.join(2000)
        }
    }

    /** The same software Canvas producer, read before any texture transform or GL sampling. */
    private fun verifyFixtureSourcePixels() {
        val source=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        try {
            drawFixture(Canvas(source))
            val probes=listOf(Triple(32,32,Color.RED),Triple(248,32,Color.GREEN),
                Triple(32,184,Color.BLUE),Triple(248,184,Color.YELLOW))
            // Diagnostic only: the old probe can hit the white D; its exact glyph varies by font.
            val evidence="Native fixture source old(224,160)=#${Integer.toHexString(source.getPixel(224,160))}; " +
                "safe="+probes.joinToString { (x,y,_) -> "($x,$y)=#${Integer.toHexString(source.getPixel(x,y))}" }
            InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply { putString("stream","$evidence\n") })
            for((x,y,color) in probes) for(dy in -2..2) for(dx in -2..2) {
                assertEquals("Unlabelled source probe ($x,$y) neighborhood ($dx,$dy)",color,source.getPixel(x+dx,y+dy))
            }
            assertEquals("Source optical marker",Color.MAGENTA,source.getPixel(80,64))
        } finally { source.recycle() }
    }
    private fun drawFixture(canvas:Canvas) {
        val paint=Paint().apply { isAntiAlias=false;isDither=false }
        for(y in 0 until H step 4)for(x in 0 until W step 4) {
            paint.color=Color.rgb(30+(x+2)*180/W,40+(y+2)*160/H,if((x/4+y/4)%2==0)255 else 0)
            canvas.drawRect(x.toFloat(),y.toFloat(),(x+4).toFloat(),(y+4).toFloat(),paint)
        }
        for((x,y,color,label) in listOf(Corner(0,0,Color.RED,"A"),Corner(W-40,0,Color.GREEN,"B"),
            Corner(0,H-40,Color.BLUE,"C"),Corner(W-40,H-40,Color.YELLOW,"D"))) {
            paint.color=color;canvas.drawRect(x.toFloat(),y.toFloat(),(x+40).toFloat(),(y+40).toFloat(),paint)
            paint.color=Color.WHITE;paint.textSize=20f;canvas.drawText(label,(x+3).toFloat(),(y+22).toFloat(),paint)
        }
        paint.color=Color.MAGENTA;canvas.drawRect(64f,48f,96f,80f,paint)
    }
    private data class Corner(val x:Int,val y:Int,val color:Int,val label:String)
    companion object { private const val W=256; private const val H=192 }
}
