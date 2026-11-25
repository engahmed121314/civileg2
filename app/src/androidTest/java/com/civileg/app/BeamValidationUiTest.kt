package com.civileg.app
import android.content.res.Configuration
import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
class BeamValidationUiTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun ar(id:Int):String {
  val c=Configuration(ctx.resources.configuration);c.setLocale(Locale("ar"))
  return ctx.createConfigurationContext(c).getString(id)
 }
 private fun reveal(m:SemanticsMatcher) {
  rule.onAllNodes(hasScrollAction())[0].performScrollToNode(m);rule.waitForIdle()
 }
 @Test fun invalidInputsAreRejectedAndEditsRemovePreviousResults() {
  rule.waitForIdle()
  if(rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty())
   rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
  rule.onNodeWithText(ar(R.string.nav_design_hub)).performClick()
  reveal(hasText(ar(R.string.home_beam)));rule.onNodeWithText(ar(R.string.home_beam)).performClick()
  reveal(hasTestTag("field_width"));rule.onNodeWithTag("field_width").performTextReplacement("")
  reveal(hasText(ar(R.string.beam_design_now)));rule.onNodeWithText(ar(R.string.beam_design_now)).performClick()
  rule.waitForIdle()
  rule.onNodeWithTag("field_width").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
  rule.onNodeWithText(ar(R.string.beam_results_economy)).assertDoesNotExist()
  rule.onNodeWithTag("field_width").performTextReplacement("٢٥٠")
  reveal(hasText(ar(R.string.beam_design_now)));rule.onNodeWithText(ar(R.string.beam_design_now)).performClick()
  rule.waitUntil(20000){rule.onAllNodesWithText(ar(R.string.beam_results_economy)).fetchSemanticsNodes().isNotEmpty()}
  // Capture the actual calculated Beam page; this does not confer code acceptance.
  rule.activity.runOnUiThread {
   rule.activity.currentFocus?.let { view ->
    (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
     .hideSoftInputFromWindow(view.windowToken,0)
    view.clearFocus()
   }
  }
  reveal(hasText(ar(R.string.column_drawing_title)))
  val out=File(ctx.filesDir,"drawing-samples/beam").apply{mkdirs()}
  val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  assertTrue("Actual Beam screenshot absent",bitmap!=null)
  File(out,"design-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
  // Current selected-code acceptance is deliberately incomplete. Confirm that the
  // real export button cannot issue a report, rather than forging a PDF fixture.
  val documents=ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)!!
  val before=documents.listFiles().orEmpty().filter {it.extension=="pdf"}.map{it.name}.toSet()
  reveal(hasText(ar(R.string.pdf_report)));rule.onNodeWithText(ar(R.string.pdf_report)).performClick()
  rule.waitForIdle()
  assertTrue("Incomplete Beam unexpectedly issued a PDF",documents.listFiles().orEmpty().none{it.extension=="pdf" && it.name !in before})
  File(out,"pdf-status.txt").writeText("Actual Beam PDF button invoked. Export blocked by incomplete selected-code acceptance. No PDF sample issued.\n")
  reveal(hasTestTag("field_width"));rule.onNodeWithTag("field_width").performTextReplacement("300")
  rule.waitForIdle();rule.onNodeWithText(ar(R.string.beam_results_economy)).assertDoesNotExist()
  File(ctx.filesDir,"drawing-samples/beam").apply{mkdirs()}.resolve("validation.txt").writeText(
   "Actual MainActivity Beam route: blank width rejected with field error; no default result; Arabic width accepted; width edit removed solved result. Not engineering acceptance.\n")
 }
}
