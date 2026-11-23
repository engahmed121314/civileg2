package com.civileg.app.domain.modeler
import com.civileg.app.domain.entities.*
import org.junit.Assert.*
import org.junit.Test
class FrameNodeMoveValidationTest {
    @Test(timeout=10000) fun pointerCheckAt500And2000NodesDoesNotRunWholeModelValidator() {
        for(size in listOf(500,2000)) {
            val nodes=(1..size).map { FrameNode(it,it*4.0,0.0) }
            val members=(1 until size).map { FrameMember(it,it,it+1,concreteSection=ConcreteSectionProps(250.0,500.0)) }
            val checker=FrameNodeMoveValidation(FrameEditCandidate(nodes,members,emptyList(),emptyList(),emptyList()),1)
            repeat(500) { assertTrue(checker.validate(4.0,0.001+it*0.00001).isEmpty()) }
            assertTrue(checker.validate(8.0,0.0).isNotEmpty())
            assertTrue(checker.validate(Double.NaN,0.0).isNotEmpty())
        }
    }
    @Test fun shorteningIncidentMemberRejectsPointLoadButIgnoresUdlHiddenPosition() {
        val nodes=listOf(FrameNode(1,0.0,0.0),FrameNode(2,4.0,0.0))
        val m=FrameMember(1,1,2,concreteSection=ConcreteSectionProps(250.0,500.0))
        val base=FrameEditCandidate(nodes,listOf(m),emptyList(),emptyList(),emptyList())
        assertTrue(FrameNodeMoveValidation(base.copy(memberLoads=listOf(MemberLoad(1,MemberLoadType.PointLoad,1.0,3.5))),2).validate(3.0,0.0).isNotEmpty())
        for(type in listOf(MemberLoadType.UDL,MemberLoadType.LinearVarying)) {
            assertTrue(FrameNodeMoveValidation(base.copy(memberLoads=listOf(MemberLoad(1,type,1.0,Double.NaN))),2).validate(3.0,0.0).isEmpty())
        }
    }
    @Test fun changedNumericTextInExistingFaultDoesNotRejectUnrelatedRepair() {
        val before=FrameEditCandidate(listOf(FrameNode(1,0.0,0.0),FrameNode(2,4.0,0.0)),
            listOf(FrameMember(1,1,2,concreteSection=ConcreteSectionProps(250.0,500.0))),emptyList(),listOf(MemberLoad(1,MemberLoadType.PointLoad,1.0,10.0)),emptyList())
        val after=before.copy(nodes=before.nodes.map { if(it.id==2) it.copy(x=5.0) else it })
        assertTrue(FrameMutationValidation.introduced(before,after).isEmpty())
        assertTrue(FrameMutationValidation.introduced(before,after.copy(memberLoads=after.memberLoads+MemberLoad(999))).isNotEmpty())
    }
    @Test fun duplicateToleranceMatchesSolveBoundary() {
        val nodes=listOf(FrameNode(1,0.0,0.0),FrameNode(2,1.0,0.0))
        val c=FrameEditCandidate(nodes,emptyList(),emptyList(),emptyList(),emptyList())
        for(delta in listOf(0.5e-4,1.0e-4,1.01e-4)) {
            val x=1.0-delta
            val solveRejects=FrameModelValidator.validate(nodes.map { if(it.id==1) it.copy(x=x) else it },emptyList())
                .any { it.code==FrameValidationCode.DUPLICATE_JOINT }
            assertEquals(solveRejects,FrameNodeMoveValidation(c,1).validate(x,0.0).isNotEmpty())
        }
    }
    @Test fun panelCrossingZeroAreaAndValidMoveMatchExistingSolveRules() {
        val nodes=listOf(FrameNode(1,0.0,0.0),FrameNode(2,4.0,0.0),FrameNode(3,4.0,4.0),FrameNode(4,0.0,4.0))
        val members=(1..4).map { id -> FrameMember(id,id,id%4+1,concreteSection=ConcreteSectionProps(250.0,500.0)) }
        val slab=SlabPanel(id=1,nodeIds=listOf(1,2,3,4),thicknessMm=200.0,loadKNm2=10.0)
        val c=FrameEditCandidate(nodes,members,emptyList(),emptyList(),listOf(slab))
        for(point in listOf(-1.0 to 0.0,6.0 to 2.0,8.0 to -4.0)) {
            val updated=nodes.map { if(it.id==1) it.copy(x=point.first,y=point.second) else it }
            val solveRejects=FrameModelValidator.validate(updated,members,slabs=listOf(slab)).any {
                it.code in setOf(FrameValidationCode.DEGENERATE_SLAB,FrameValidationCode.INVALID_SLAB,FrameValidationCode.ZERO_LENGTH_MEMBER,FrameValidationCode.DUPLICATE_JOINT) }
            assertEquals(solveRejects,FrameNodeMoveValidation(c,1).validate(point.first,point.second).isNotEmpty())
        }
    }
}
