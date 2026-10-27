package local.skytrails.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class OfflineLicensesTest {
    @Test fun packagedLicenseEvidenceIsCompleteAndMatchesItsChecksums() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun bytes(path: String) = assets.open(path).use { it.readBytes() }
        fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val index = JSONObject(bytes("licenses/index.json").decodeToString()).getJSONArray("documents")
        val paths = mutableSetOf<String>()
        for (i in 0 until index.length()) {
            val item = index.getJSONObject(i)
            val path = item.getString("path")
            assertTrue(paths.add(path))
            assertEquals(path, item.getString("sha256"), digest(bytes(path)))
        }
        val runtime = JSONObject(bytes("licenses/runtime-dependencies.json").decodeToString())
        assertEquals("privatePreviewRuntimeClasspath", runtime.getString("configuration"))
        val artifacts = runtime.getJSONArray("artifacts")
        assertTrue(artifacts.length() > 0)
        for (i in 0 until artifacts.length()) {
            val artifact = artifacts.getJSONObject(i)
            assertTrue(paths.contains(artifact.getString("licenseText")))
            assertEquals(64, artifact.getString("sha256").length)
            assertTrue(artifact.getJSONArray("pomEvidence").length() > 0)
            val embedded = artifact.getJSONArray("embeddedTexts")
            for (j in 0 until embedded.length()) {
                val text = embedded.getJSONObject(j)
                assertTrue(paths.contains(text.getString("path")))
                assertEquals(text.getString("sha256"), digest(bytes(text.getString("path"))))
            }
        }
        assertTrue(bytes("licenses/GPL-3.0.txt").decodeToString().contains("END OF TERMS AND CONDITIONS"))
        assertTrue(bytes("licenses/CC-BY-4.0.txt").decodeToString().contains("Section 8"))
    }
}
