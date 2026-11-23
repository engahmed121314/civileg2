package com.civileg.app.domain.modeler
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test

class FrameNumericModelValidationTest {
    private val nodes=listOf(FrameNode(1,0.0,0.0,SupportType.Fixed),FrameNode(2,4.0,0.0,SupportType.Fixed))
    private val member=FrameMember(1,1,2,concreteSection=ConcreteSectionProps(250.0,500.0))
    @Test fun rejectsNonFiniteCoordinatesStrengthCoverAndLoadPositionBeforeSolve() {
        fun issues(ns:List<FrameNode> = nodes, m:FrameMember = member, loads:List<MemberLoad> = emptyList()) = FrameModelValidator.validate(ns,listOf(m),memberLoads=loads)
        assertTrue(issues(ns=nodes.map { it.copy(x=Double.NaN) }).any { it.code==FrameValidationCode.INVALID_NUMBER })
        for(section in listOf(ConcreteSectionProps(250.0,500.0,fy=Double.NaN),ConcreteSectionProps(250.0,500.0,cover=Double.POSITIVE_INFINITY),ConcreteSectionProps(250.0,50.0)))
            assertTrue(issues(m=member.copy(concreteSection=section)).any { it.code==FrameValidationCode.INVALID_NUMBER })
        for(type in listOf(MemberLoadType.PointLoad,MemberLoadType.Moment,MemberLoadType.Trapezoidal))
            assertTrue(issues(loads=listOf(MemberLoad(1,type,10.0,Double.NaN))).any { it.code==FrameValidationCode.INVALID_NUMBER })
    }
    @Test fun acceptsFiniteSignedLoadsAndZeroEndpoint() {
        val issues=FrameModelValidator.validate(nodes,listOf(member),listOf(NodalLoad(2,-1.0,0.0,-2.0)),listOf(MemberLoad(1,MemberLoadType.PointLoad,-10.0,0.0)))
        assertTrue(issues.none { it.code==FrameValidationCode.INVALID_NUMBER })
    }
}
