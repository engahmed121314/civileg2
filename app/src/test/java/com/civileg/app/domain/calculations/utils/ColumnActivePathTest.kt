package com.civileg.app.domain.calculations.utils
import com.civileg.app.domain.entities.DesignCode
import org.junit.Assert.*
import org.junit.Test
class ColumnActivePathTest {
 private fun engine(): CalculatorEngine {
  val f=sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe");f.isAccessible=true
  return (f.get(null) as sun.misc.Unsafe).allocateInstance(CalculatorEngine::class.java) as CalculatorEngine
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
}
