package com.civileg.app.domain.calculations.utils
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test
class ColumnActivePathTest {
 private fun engine(): CalculatorEngine {
  // No SettingsManager field is used by the path under test. Avoid a compile-time
  // dependency on JDK-internal packages that Android's unit-test bootclasspath hides.
  val type=Class.forName("sun.misc.Unsafe")
  val field=type.getDeclaredField("theUnsafe").apply { isAccessible=true }
  return type.getMethod("allocateInstance",Class::class.java)
   .invoke(field.get(null),CalculatorEngine::class.java) as CalculatorEngine
 }

 @Test fun unavailableCapacityWithDemandDoesNotPass() {
  for(c in listOf(0.0,Double.NaN,Double.POSITIVE_INFINITY,-1.0)) {
   val r=ColumnMomentReview.assess(10.0,0.0,c,100.0)
   assertFalse(r.passed);assertFalse(r.assessed)
  }
 }
 @Test fun unusedAxisCapacityDoesNotInvalidateAssessedAxis() {
  val r=ColumnMomentReview.assess(10.0,0.0,100.0,0.0)
  assertTrue(r.passed);assertTrue(r.assessed)
 }
 @Test fun biaxialSumStillGoverns() {
  assertFalse(ColumnMomentReview.assess(80.0,80.0,100.0,100.0).passed)
  assertTrue(ColumnMomentReview.assess(50.0,50.0,100.0,100.0).passed)
 }
 @Test fun manualBarsNeverSilentlyBecomeAutomaticBars() {
  for(code in DesignCode.values()) for(n in listOf(4,8,20)) {
   try {
    engine().designColumn(400.0,400.0,1000.0,10.0,10.0,30.0,400.0,code,
      clearHeight=1000.0,autoOptimize=false,manualNumBars=n,preferredDiameter=20)
    fail("Unsupported manual fixed layout issued automatic design")
   }catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("Manual column reinforcement"))}
  }
 }
 @Test fun incompleteAssessmentIsNeverConfirmedSafe() {
  for(code in DesignCode.values()) {
   val r=engine().designColumn(400.0,400.0,500.0,10.0,10.0,30.0,400.0,code,clearHeight=3000.0)
   assertFalse("assessment is not complete for any layout yet",r.assessmentComplete)
   assertFalse(r.confirmedSafe)
  }
  val big=engine().designColumn(300.0,300.0,3000.0,0.0,0.0,25.0,400.0,DesignCode.ECP,clearHeight=3000.0)
  assertFalse(big.confirmedSafe)
 }
}
