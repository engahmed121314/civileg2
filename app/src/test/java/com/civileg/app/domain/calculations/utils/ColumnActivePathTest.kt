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
 @Test fun manualBarsAreCheckedAsEntered() {
  for(code in DesignCode.values()) {
   val r=engine().designColumn(400.0,400.0,1000.0,10.0,10.0,30.0,400.0,code,
     clearHeight=1000.0,autoOptimize=false,manualNumBars=8,preferredDiameter=20)
   assertEquals(8,r.reinforcement.numBars); assertEquals(20,r.reinforcement.diameter)
   assertEquals(8*Math.PI*400.0/4.0,r.reinforcementArea,1e-6)
  }
 }
 @Test fun tooFewManualBarsAreRejectedAndWeakLayoutIsUnsafe() {
  try{ engine().designColumn(400.0,400.0,1000.0,10.0,10.0,30.0,400.0,DesignCode.ECP,
     clearHeight=1000.0,autoOptimize=false,manualNumBars=3,preferredDiameter=20); fail("3 bars accepted")
  }catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("at least"))}
  val weak=engine().designColumn(300.0,300.0,3000.0,0.0,0.0,25.0,400.0,DesignCode.ECP,
     clearHeight=3000.0,autoOptimize=false,manualNumBars=4,preferredDiameter=12)
  assertFalse(weak.isSafe)
 }
}
