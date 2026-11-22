package com.civileg.app.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.civileg.app.MainActivity
import com.civileg.app.R

/**
 * Disposable QA for the minified candidate's Frame area, grounded in source head
 * 78cdca0 (FrameAnalysisScreen / FrameAnalysisViewModel / HomeScreen):
 *  - The Home module card is found by its CLICKABLE-CARD semantics carrying BOTH
 *    the module title (R.string.home_frame) and its subtitle
 *    (R.string.home_frame_sub) - a bare first-text match is ambiguous with the
 *    Recent Projects section, which can list a seeded FRAME_ANALYSIS design.
 *    The LazyVerticalGrid is swiped until that card exists, then clicked.
 *  - Actual screen entry is asserted through the screen's own title
 *    (R.string.frame_title) and its ScrollableTabRow tab (R.string.frame_tab_nodes),
 *    not a generic back arrow or mere activity survival.
 *  - The saved-model open path (Actions -> R.string.frame_open_design ->
 *    QA_SENTINEL_FRAME_RECORD) is verified by DIRECT loaded state: after a
 *    baseline check that the empty model shows "Nodes: 0" / "Members: 0" (no
 *    state cleared or replaced to hide a failure), the Drawing tab info card
 *    must show "Nodes: 4" and "Members: 3" (localized via
 *    R.string.frame_tab_nodes / frame_tab_members). loadFrameDesign swallows
 *    errors and leaves the screen open, so waiting for the screen alone is a
 *    false positive; the node/member counts prove the real schemaVersion-2
 *    nested-JSON load succeeded in the minified app.
 * UNCOMPILED until the first real androidTest build proves it. Requires the seeded
 * sentinel record (fresh-14 and historical cases of tools/qa/host/seed_upgrade_db.py).
 */
@RunWith(AndroidJUnit4::class)
class FrameEntryTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    private fun openFrameScreenFromHome() {
        val moduleCard = hasClickAction() and
            hasText(string(R.string.home_frame), substring = true) and
            hasText(string(R.string.home_frame_sub), substring = true)
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        val scrollable = compose.onAllNodes(hasScrollAction())[0]
        var found = false
        for (attempt in 1..10) {
            if (compose.onAllNodes(moduleCard).fetchSemanticsNodes().isNotEmpty()) {
                found = true
                break
            }
            scrollable.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        check(found) { "Frame module card not found on Home after scrolling" }
        compose.onNode(moduleCard).performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(string(R.string.frame_title), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertFrameScreenVisible() {
        compose.onNodeWithText(string(R.string.frame_title), substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.frame_tab_nodes)).assertIsDisplayed()
    }

    @Test
    fun frameAnalysisEntryShowsRealFrameScreen() {
        openFrameScreenFromHome()
        assertFrameScreenVisible()
    }

    @Test
    fun savedFrameModelOpensFromRealSeededRecord() {
        openFrameScreenFromHome()
        assertFrameScreenVisible()

        // Pre-load baseline: with no saved draft, the Drawing tab info card shows
        // the empty model (0 nodes / 0 members). Proves the 4/3 state below comes
        // from the seeded record, not leftover state, without clearing anything.
        val emptyNodes = "${string(R.string.frame_tab_nodes)}: 0"
        val emptyMembers = "${string(R.string.frame_tab_members)}: 0"
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(emptyNodes, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(emptyNodes, substring = true).assertIsDisplayed()
        compose.onNodeWithText(emptyMembers, substring = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithText(string(R.string.frame_open_design)).performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("QA_SENTINEL_FRAME_RECORD").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("QA_SENTINEL_FRAME_RECORD").performClick()

        // Direct loaded-state proof on the Drawing tab info card: the seeded
        // record has exactly 4 nodes and 3 members. A failed load leaves the
        // screen open with the previous (empty) model, so this fails loudly.
        val loadedNodes = "${string(R.string.frame_tab_nodes)}: 4"
        val loadedMembers = "${string(R.string.frame_tab_members)}: 3"
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(loadedNodes, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(loadedNodes, substring = true).assertIsDisplayed()
        compose.onNodeWithText(loadedMembers, substring = true).assertIsDisplayed()
    }
}
