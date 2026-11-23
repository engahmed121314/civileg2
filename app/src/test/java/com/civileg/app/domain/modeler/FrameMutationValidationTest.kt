package com.civileg.app.domain.modeler
import com.civileg.app.domain.entities.*
import org.junit.Test
import org.junit.Assert.*
class FrameMutationValidationTest {
    private val blank=FrameEditCandidate(emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    @Test fun partialConstructionAllowedNoSupportRequiredButInvalidBatchRejected() {
        val one=blank.copy(nodes=listOf(FrameNode(1,0.0,0.0)))
        assertTrue(FrameMutationValidation.introduced(blank,one).isEmpty())
        assertTrue(FrameMutationValidation.introduced(one,one.copy(nodes=one.nodes+FrameNode(2,Double.NaN,1.0))).isNotEmpty())
        val missingMember=one.copy(members=listOf(FrameMember(1,1,9,concreteSection=ConcreteSectionProps(250.0,500.0))))
        assertTrue(FrameMutationValidation.introduced(one,missingMember).isNotEmpty())
    }
    @Test fun unrelatedExistingFaultDoesNotBlockRepairButNewFaultDoes() {
        val before=blank.copy(nodes=listOf(FrameNode(1,0.0,0.0),FrameNode(2,4.0,0.0),FrameNode(3,8.0,0.0)),
            members=listOf(FrameMember(1,1,2,concreteSection=ConcreteSectionProps(250.0,500.0))),
            memberLoads=listOf(MemberLoad(999,MemberLoadType.UDL,1.0)))
        val fixed=before.copy(nodes=before.nodes.map { if(it.id==3) it.copy(x=9.0) else it })
        assertTrue(FrameMutationValidation.introduced(before,fixed).isEmpty())
        assertTrue(FrameMutationValidation.introduced(before,before.copy(memberLoads=before.memberLoads+MemberLoad(888))).isNotEmpty())
    }
    @Test fun clipboardOverflowAndMissingReferencesFailBeforePartialMapping() {
        val c=ModelerClipboard.Content(nodes=listOf(FrameNode(1,0.0,0.0)))
        fun refused(block:()->Unit) { try { block();fail("invalid paste accepted") } catch(_:IllegalArgumentException) {} }
        refused { ModelerClipboard.paste(c,listOf(FrameNode(Int.MAX_VALUE,0.0,0.0)),emptyList(),emptyList(),1.0) }
        refused { ModelerClipboard.paste(c.copy(members=listOf(FrameMember(1,1,9))),emptyList(),emptyList(),emptyList(),1.0) }
    }
}
