package com.civileg.app.domain.calculations.utils
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class BeamUdlDiagramTest {
 @Test fun supportBoundaryValuesAndEquilibrium() {
  val l=8.0;val w=10.0
  for(s in CalculatorEngine.SupportType.entries) {
   val shear=w*l*when(s) { CalculatorEngine.SupportType.CANTILEVER->1.0;CalculatorEngine.SupportType.FIXED_HINGED->5.0/8;else->0.5 }
   val a=BeamUdlDiagram.samples(l,shear,s,1000)
   if(s!=CalculatorEngine.SupportType.FIXED_FIXED) assertEquals(0.0,a.last().momentKnM,1e-8)
   for(pair in a.zipWithNext()) {
    val (p,q)=pair
    assertEquals((p.shearKn+q.shearKn)/2,(q.momentKnM-p.momentKnM)/(q.xM-p.xM),1e-7)
   }
   when(s) {
    CalculatorEngine.SupportType.CANTILEVER -> {assertEquals(-320.0,a.first().momentKnM,1e-8);assertEquals(0.0,a.last().shearKn,1e-8)}
    CalculatorEngine.SupportType.FIXED_HINGED -> {assertEquals(-80.0,a.first().momentKnM,1e-8);assertEquals(-30.0,a.last().shearKn,1e-8);assertTrue(a.any{it.momentKnM>0})}
    CalculatorEngine.SupportType.FIXED_FIXED -> {assertEquals(-640.0/12,a.first().momentKnM,1e-8);assertEquals(a.first().momentKnM,a.last().momentKnM,1e-8)}
    else -> assertEquals(80.0,a.maxOf { it.momentKnM },1e-8)
   }
   assertEquals(shear,a.maxOf { abs(it.shearKn) },1e-8)
  }
 }
 @Test(expected=IllegalArgumentException::class) fun zeroSpanRejected(){ BeamUdlDiagram.samples(0.0,5.0,CalculatorEngine.SupportType.CANTILEVER) }
}
