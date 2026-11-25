package com.civileg.app.domain.calculations.utils

/** Elastic UDL equilibrium, fixed end is x=0. Uses the solved shear, including self-weight. */
object BeamUdlDiagram {
 data class Sample(val xM: Double,val momentKnM: Double,val shearKn: Double)
 fun samples(spanM: Double, appliedShearKn: Double, support: CalculatorEngine.SupportType, intervals: Int=100): List<Sample> {
  require(spanM.isFinite() && spanM>0 && appliedShearKn.isFinite() && appliedShearKn>=0 && intervals>0)
  val w=appliedShearKn/spanM * when(support) {
   CalculatorEngine.SupportType.CANTILEVER -> 1.0
   CalculatorEngine.SupportType.FIXED_HINGED -> 8.0/5.0
   else -> 2.0
  }
  return (0..intervals).map { i ->
   val x=spanM*i/intervals
   val (m,v)=when(support) {
    CalculatorEngine.SupportType.CANTILEVER -> Pair(-w*(spanM-x)*(spanM-x)/2,w*(spanM-x))
    CalculatorEngine.SupportType.FIXED_FIXED -> Pair(-w*spanM*spanM/12+w*spanM*x/2-w*x*x/2,w*spanM/2-w*x)
    CalculatorEngine.SupportType.FIXED_HINGED -> Pair(-w*spanM*spanM/8+5*w*spanM*x/8-w*x*x/2,5*w*spanM/8-w*x)
    else -> Pair(w*x*(spanM-x)/2,w*spanM/2-w*x)
   }
   Sample(x,m,v)
  }
 }
}
