package local.skytrails.app.ui.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class TextureSamplingTest {
 @Test fun preservesFourKOnCapableHardwareAndOnlyDownsamplesToFit() {
  assertEquals(1,TextureSampling.sampleSize(4096,2048,4096))
  assertEquals(1,TextureSampling.sampleSize(4096,2048,8192))
  assertEquals(2,TextureSampling.sampleSize(4096,2048,2048))
  assertEquals(4,TextureSampling.sampleSize(4096,2048,1536))
  assertEquals(4,TextureSampling.sampleSize(2048,4096,1024))
  assertEquals(2,TextureSampling.sampleSize(4097,2048,4096))
 }
}
