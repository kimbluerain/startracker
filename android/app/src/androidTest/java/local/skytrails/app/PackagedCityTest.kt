package local.skytrails.app
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.skytrails.app.location.CityIndex
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PackagedCityTest {
 @Test fun realApkOfflineIndexLoadsAndSearchesKnownCity() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val index=CityIndex.readGzip(context.assets.open("cities15000.data"))
  assertTrue(index.cities.size>30000)
  assertTrue(index.search("Hangzhou").any{it.zoneId=="Asia/Shanghai"})
 }
}
