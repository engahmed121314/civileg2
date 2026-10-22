package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.ConcreteSectionProps
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.MemberLoadType
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabDistribution
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.domain.entities.SupportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameModelValidatorTest {

    private val section = ConcreteSectionProps(width = 300.0, depth = 500.0)

    private fun beam(
        id: Int = 1,
        nodeI: Int = 1,
        nodeJ: Int = 2
    ) = FrameMember(
        id = id,
        nodeI = nodeI,
        nodeJ = nodeJ,
        materialType = FrameMaterialType.Concrete,
        memberType = FrameMemberType.Beam,
        concreteSection = section
    )

    private fun validBeamModel(): Pair<List<FrameNode>, List<FrameMember>> {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 6.0, 0.0, SupportType.Roller)
        )
        return nodes to listOf(beam())
    }

    @Test
    fun validModel_hasNoIssues() {
        val (nodes, members) = validBeamModel()

        val issues = FrameModelValidator.validate(nodes, members)

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun tooFewJoints_isRejected() {
        val nodes = listOf(FrameNode(1, 0.0, 0.0, SupportType.Fixed))
        val issues = FrameModelValidator.validate(nodes, listOf(beam(nodeI = 1, nodeJ = 2)))

        assertTrue(issues.any { it.code == FrameValidationCode.TOO_FEW_JOINTS })
    }

    @Test
    fun duplicateJointsAtSameCoordinates_areRejected() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 3.0, 0.0, SupportType.Free),
            FrameNode(3, 3.0, 0.0, SupportType.Free)
        )
        val members = listOf(beam(nodeJ = 2), beam(id = 2, nodeI = 2, nodeJ = 3))

        val issues = FrameModelValidator.validate(nodes, members)

        assertTrue(issues.any { it.code == FrameValidationCode.DUPLICATE_JOINT })
    }

    @Test
    fun duplicateJointIds_areRejected() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(1, 3.0, 0.0, SupportType.Roller)
        )
        val issues = FrameModelValidator.validate(nodes, listOf(beam()))

        assertTrue(issues.any { it.code == FrameValidationCode.DUPLICATE_JOINT_ID })
    }

    @Test
    fun zeroLengthMember_isRejected() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Pin),
            FrameNode(2, 0.0, 0.0, SupportType.Roller)
        )
        val issues = FrameModelValidator.validate(nodes, listOf(beam()))

        assertTrue(issues.any { it.code == FrameValidationCode.ZERO_LENGTH_MEMBER })
    }

    @Test
    fun duplicateMember_isRejected() {
        val (nodes, _) = validBeamModel()
        val members = listOf(beam(), beam(id = 2, nodeI = 2, nodeJ = 1))

        val issues = FrameModelValidator.validate(nodes, members)

        assertTrue(issues.any { it.code == FrameValidationCode.DUPLICATE_MEMBER })
    }

    @Test
    fun memberWithMissingJoint_isRejected() {
        val (nodes, _) = validBeamModel()
        val issues = FrameModelValidator.validate(nodes, listOf(beam(nodeJ = 99)))

        assertTrue(issues.any { it.code == FrameValidationCode.MISSING_JOINT })
    }

    @Test
    fun disconnectedJoint_isRejected() {
        val (nodes, members) = validBeamModel()
        val withExtraJoint = nodes + FrameNode(3, 3.0, 5.0, SupportType.Free)

        val issues = FrameModelValidator.validate(withExtraJoint, members)

        val issue = issues.single { it.code == FrameValidationCode.DISCONNECTED_JOINT }
        assertEquals(listOf(3), issue.nodeIds)
    }

    @Test
    fun modelWithoutSupport_isRejected() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Free),
            FrameNode(2, 6.0, 0.0, SupportType.Free)
        )
        val issues = FrameModelValidator.validate(nodes, listOf(beam()))

        assertTrue(issues.any { it.code == FrameValidationCode.NO_SUPPORT })
    }

    @Test
    fun loadsOnMissingTargets_areRejected() {
        val (nodes, members) = validBeamModel()
        val nodalLoads = listOf(NodalLoad(nodeId = 99, fy = -10.0))
        val memberLoads = listOf(MemberLoad(memberId = 99, loadType = MemberLoadType.UDL, value = 2.0))

        val issues = FrameModelValidator.validate(nodes, members, nodalLoads, memberLoads)

        assertEquals(
            2,
            issues.count { it.code == FrameValidationCode.MISSING_LOAD_TARGET }
        )
    }

    @Test
    fun blankLoadCase_isRejected() {
        val (nodes, members) = validBeamModel()
        val nodalLoads = listOf(NodalLoad(nodeId = 1, fy = -10.0, loadCase = " "))
        val memberLoads = listOf(MemberLoad(memberId = 1, value = 2.0, loadCase = ""))

        val issues = FrameModelValidator.validate(nodes, members, nodalLoads, memberLoads)

        assertEquals(2, issues.count { it.code == FrameValidationCode.EMPTY_LOAD_CASE })
        assertFalse(issues.any { it.code == FrameValidationCode.MISSING_LOAD_TARGET })
    }

    @Test
    fun validSlabOnAllEdges_hasNoIssues() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 4.0, 0.0, SupportType.Fixed),
            FrameNode(3, 4.0, 3.0, SupportType.Free),
            FrameNode(4, 0.0, 3.0, SupportType.Free)
        )
        val members = listOf(
            beam(1, 1, 2),
            beam(2, 2, 3),
            beam(3, 3, 4),
            beam(4, 4, 1)
        )
        val slab = SlabPanel(
            id = 1,
            nodeIds = listOf(1, 2, 3, 4),
            loadKNm2 = 2.0,
            distribution = SlabDistribution.TwoWay
        )

        val issues = FrameModelValidator.validate(nodes, members, slabs = listOf(slab))

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun slabEdgeWithoutMember_isRejected() {
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, 4.0, 0.0, SupportType.Fixed),
            FrameNode(3, 4.0, 3.0, SupportType.Free),
            FrameNode(4, 0.0, 3.0, SupportType.Free)
        )
        val members = listOf(
            beam(1, 1, 2),
            beam(2, 2, 3),
            beam(3, 3, 4)
        )
        val slab = SlabPanel(id = 1, nodeIds = listOf(1, 2, 3, 4))

        val issues = FrameModelValidator.validate(nodes, members, slabs = listOf(slab))

        assertTrue(issues.any { it.code == FrameValidationCode.SLAB_EDGE_WITHOUT_MEMBER })
    }

    @Test
    fun slabWithDuplicateJoints_isRejected() {
        val (nodes, members) = validBeamModel()
        val slab = SlabPanel(id = 1, nodeIds = listOf(1, 2, 1, 2))

        val issues = FrameModelValidator.validate(nodes, members, slabs = listOf(slab))

        assertTrue(issues.any { it.code == FrameValidationCode.INVALID_SLAB })
    }
}
