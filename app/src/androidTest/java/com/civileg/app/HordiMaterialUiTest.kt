package com.civileg.app
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
class HordiMaterialUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun ar(id:Int):String {val c=Configuration(ctx.resources.configuration);c.setLocale(Locale("ar"));return ctx.createConfigurationContext(c).getString(id)}
 private fun reveal(m:SemanticsMatcher) {rule.onAllNodes(hasScrollAction())[0].performScrollToNode(m);rule.waitForIdle()}
 @Test fun actualMaterialSelectorChangesSurfaceClass() {
  rule.waitForIdle()
  if(rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
  rule.onNodeWithText(ar(R.string.nav_design_hub)).performClick()
  reveal(hasText(ar(R.string.home_hordi_slab)));rule.onNodeWithText(ar(R.string.home_hordi_slab)).performClick()
  reveal(hasText("Bar Surface / Bond Class"));rule.onNodeWithText("Unknown — estimate only").assertIsDisplayed().performClick()
  rule.onNodeWithText("Deformed (ribbed) bars").performClick();rule.waitForIdle()
  rule.onNodeWithText("Deformed (ribbed) bars").assertIsDisplayed()
  val out=File(ctx.filesDir,"drawing-samples/hordi").apply{mkdirs()}
  val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(out,"material-selector.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
  rule.onNodeWithText("Deformed (ribbed) bars").performClick();rule.onNodeWithText("Plain (smooth) bars").performClick()
  rule.onNodeWithText("Plain (smooth) bars").assertIsDisplayed()
  File(out,"selector-status.txt").writeText("Actual MainActivity Hordi selector: starts UNKNOWN, selects DEFORMED then PLAIN. No solved engineering, drawing, export or durable form restoration claimed.\n")
 }
}
