package local.skytrails.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun offlineLicenseCanBeOpenedScrolledAndClosed() {
        ui.onNodeWithTag("settings").performClick()
        ui.onNodeWithTag("about_open").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithTag("license_gpl").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("license_gpl").performScrollTo().performClick()
        ui.waitUntil(10000) {
            ui.onAllNodes(hasTestTag("license_text") and hasText("GNU GENERAL PUBLIC LICENSE", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("license_scroll").performTouchInput { swipeUp() }
        ui.onNodeWithTag("about_back").performClick()
        ui.onNodeWithTag("about_version").assertExists()
        ui.onNodeWithTag("about_back").performClick()
        ui.onNodeWithText("显示设置").assertExists()
    }
}
