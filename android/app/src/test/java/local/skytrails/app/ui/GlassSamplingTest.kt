package local.skytrails.app.ui
import local.skytrails.app.ui.renderer.*
import local.skytrails.app.ar.*
import org.junit.Assert.*
import org.junit.Test
class GlassSamplingTest {
 @Test fun unknownMountConvertsQuadOriginWithoutGuessingSensorOrDroppingProducerCrop() {
  // Real SurfaceTexture-style native crop: GL bottom-left maps to native (0.1,0.9).
  val actual=floatArrayOf(.8f,0f,0f,0f,0f,-.8f,0f,0f,0f,0f,1f,0f,.1f,.9f,0f,1f)
  val original=actual.copyOf();val t=GlassSampling.unmountedViewToSampler(actual)
  assertEquals(.1f,t[12],1e-6f);assertEquals(.1f,t[13],1e-6f)
  assertEquals(.9f,t[0]+t[4]+t[12],1e-6f);assertEquals(.9f,t[1]+t[5]+t[13],1e-6f)
  assertArrayEquals(original,actual,0f)
 }
 @Test fun sensorQuarterTurnUsesCorrectCornersWithoutDoubleRotation() {
  val p=PreviewGeometry.create(1,4,2,2,4,90,0)
  val t=GlassSampling.viewToSampler(p,GlassSampling.predicted(90))
  fun point(x:Double,y:Double,u:Double,v:Double){val p=t.map(Pixel(x,y));assertEquals(u,p.x,1e-9);assertEquals(v,p.y,1e-9)}
  // Native OES rows start at the top, unlike the bottom-up glTexImage2D fixture.
  point(0.0,0.0,0.0,1.0);point(2.0,0.0,0.0,0.0);point(0.0,4.0,1.0,1.0)
 }
 @Test fun producerResidualCropAppliesIdenticallyToSharpAndBlurSampling() {
  val p=PreviewGeometry.create(1,4,2,4,2,0,0)
  val actual=Mat3.scaleTranslate(.8,.8,.1,.1)*GlassSampling.predicted(0)
  val t=GlassSampling.viewToSampler(p,actual)
  assertEquals(.1,t.map(Pixel(0.0,0.0)).x,1e-9);assertEquals(.1,t.map(Pixel(0.0,0.0)).y,1e-9)
  assertEquals(.9,t.map(Pixel(4.0,2.0)).x,1e-9);assertEquals(.9,t.map(Pixel(4.0,2.0)).y,1e-9)
 }
 @Test fun maskSubtractsWindowOffsetExactlyOnce() {
  val m=GlassSampling.localMask(GlassMask(30f,100f,230f,300f,24f),10f,60f)
  assertEquals(20f,m.left);assertEquals(40f,m.top);assertEquals(220f,m.right);assertEquals(240f,m.bottom)
 }
 @Test fun unknownProducerRotationCannotBorrowCameraOverlay() {
  val p=PreviewGeometry.create(1,1920,1080,1080,1920,90,0)
  assertTrue(GlassSampling.residualAllowsOverlay(p,GlassSampling.predicted(90)))
  assertFalse(GlassSampling.residualAllowsOverlay(p,GlassSampling.predicted(0)))
  assertFalse(GlassSampling.residualAllowsOverlay(p,Mat3.scaleTranslate(.8,.8,.1,.1)*GlassSampling.predicted(90)))
 }
 @Test fun inversePoseReturnsTrueBearingAndRejectsZeroRay() {
  val orientation=SkyOrientation.virtualView(90.0,30.0)
  val d=orientation.fromCamera(CameraVector(0.0,0.0,1.0))!!
  assertEquals(90.0,d.azimuthDeg,1e-9);assertEquals(30.0,d.altitudeDeg,1e-9)
  assertNull(orientation.fromCamera(CameraVector(0.0,0.0,0.0)))
  val eastDeclination=SkyOrientation(doubleArrayOf(1.0,0.0,0.0,0.0,0.0,-1.0,0.0,1.0,0.0),10.0)
  assertEquals(10.0,eastDeclination.fromCamera(CameraVector(0.0,0.0,1.0))!!.azimuthDeg,1e-9)
 }
}
