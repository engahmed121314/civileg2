package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.utils.LocaleHelper
import kotlin.math.hypot

enum class FrameValidationCode {
    TOO_FEW_JOINTS,
    NO_MEMBERS,
    DUPLICATE_JOINT_ID,
    DUPLICATE_JOINT,
    MISSING_JOINT,
    ZERO_LENGTH_MEMBER,
    DUPLICATE_MEMBER,
    DISCONNECTED_JOINT,
    NO_SUPPORT,
    MISSING_LOAD_TARGET,
    EMPTY_LOAD_CASE,
    INVALID_SLAB,
    SLAB_EDGE_WITHOUT_MEMBER
}

data class FrameValidationIssue(
    val code: FrameValidationCode,
    val messageAr: String,
    val messageEn: String,
    val nodeIds: List<Int> = emptyList(),
    val memberIds: List<Int> = emptyList()
) {
    val message: String get() = if (LocaleHelper.isArabic()) messageAr else messageEn
}

object FrameModelValidator {

    private const val DUPLICATE_TOLERANCE_M = 1e-4
    private const val ZERO_LENGTH_TOLERANCE_M = 1e-6

    fun validate(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad> = emptyList(),
        memberLoads: List<MemberLoad> = emptyList(),
        slabs: List<SlabPanel> = emptyList()
    ): List<FrameValidationIssue> {
        val issues = mutableListOf<FrameValidationIssue>()

        if (nodes.size < 2) {
            issues += FrameValidationIssue(
                FrameValidationCode.TOO_FEW_JOINTS,
                "يجب إدخال عقدتين على الأقل",
                "At least two joints are required"
            )
        }
        if (members.isEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.NO_MEMBERS,
                "يجب إدخال عضو واحد على الأقل",
                "At least one member is required"
            )
        }

        val duplicateIds = nodes.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.DUPLICATE_JOINT_ID,
                "توجد أرقام عقد مكررة: ${duplicateIds.joinToString()}",
                "Duplicate joint IDs: ${duplicateIds.joinToString()}",
                nodeIds = duplicateIds.toList()
            )
        }

        val nodeById = nodes.associateBy { it.id }
        val duplicatePairs = mutableListOf<Pair<FrameNode, FrameNode>>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                if (hypot(a.x - b.x, a.y - b.y) <= DUPLICATE_TOLERANCE_M) {
                    duplicatePairs += a to b
                }
            }
        }
        for ((a, b) in duplicatePairs) {
            issues += FrameValidationIssue(
                FrameValidationCode.DUPLICATE_JOINT,
                "العقدتان ${a.id} و${b.id} لهما نفس الإحداثيات",
                "Joints ${a.id} and ${b.id} have the same coordinates",
                nodeIds = listOf(a.id, b.id)
            )
        }

        val usedNodeIds = mutableSetOf<Int>()
        val seenMemberPairs = mutableSetOf<Pair<Int, Int>>()
        for (member in members) {
            val a = nodeById[member.nodeI]
            val b = nodeById[member.nodeJ]
            if (a == null || b == null) {
                issues += FrameValidationIssue(
                    FrameValidationCode.MISSING_JOINT,
                    "العضو ${member.id} مرتبط بعقدة غير موجودة",
                    "Member ${member.id} references a missing joint",
                    memberIds = listOf(member.id)
                )
                continue
            }
            usedNodeIds += a.id
            usedNodeIds += b.id

            val length = hypot(b.x - a.x, b.y - a.y)
            if (length <= ZERO_LENGTH_TOLERANCE_M) {
                issues += FrameValidationIssue(
                    FrameValidationCode.ZERO_LENGTH_MEMBER,
                    "العضو ${member.id} طوله صفر",
                    "Member ${member.id} has zero length",
                    memberIds = listOf(member.id)
                )
            }

            val pair = if (member.nodeI <= member.nodeJ) {
                member.nodeI to member.nodeJ
            } else {
                member.nodeJ to member.nodeI
            }
            if (!seenMemberPairs.add(pair)) {
                issues += FrameValidationIssue(
                    FrameValidationCode.DUPLICATE_MEMBER,
                    "يوجد عضو مكرر بين العقدتين ${pair.first} و${pair.second}",
                    "A duplicate member exists between joints ${pair.first} and ${pair.second}",
                    nodeIds = listOf(pair.first, pair.second),
                    memberIds = listOf(member.id)
                )
            }
        }

        val disconnected = nodes.filter { it.id !in usedNodeIds }.map { it.id }
        if (nodes.size >= 2 && disconnected.isNotEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.DISCONNECTED_JOINT,
                "عقد غير متصلة بأي عضو: ${disconnected.joinToString()}",
                "Joints not connected to any member: ${disconnected.joinToString()}",
                nodeIds = disconnected
            )
        }

        if (nodes.isNotEmpty() && nodes.none { it.support.restrainedDOFs.isNotEmpty() }) {
            issues += FrameValidationIssue(
                FrameValidationCode.NO_SUPPORT,
                "لا يوجد أي استناد في النموذج",
                "The model has no supports"
            )
        }

        val memberIds = members.map { it.id }.toSet()
        for (load in nodalLoads) {
            if (nodeById[load.nodeId] == null) {
                issues += FrameValidationIssue(
                    FrameValidationCode.MISSING_LOAD_TARGET,
                    "حمل عقدي مرتبط بعقدة غير موجودة: ${load.nodeId}",
                    "A nodal load references a missing joint: ${load.nodeId}",
                    nodeIds = listOf(load.nodeId)
                )
            }
            if (load.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "يوجد حمل عقدي بدون حالة حمل",
                    "A nodal load has no load case",
                    nodeIds = listOf(load.nodeId)
                )
            }
        }
        for (load in memberLoads) {
            if (load.memberId !in memberIds) {
                issues += FrameValidationIssue(
                    FrameValidationCode.MISSING_LOAD_TARGET,
                    "حمل عضو مرتبط بعضو غير موجود: ${load.memberId}",
                    "A member load references a missing member: ${load.memberId}",
                    memberIds = listOf(load.memberId)
                )
            }
            if (load.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "يوجد حمل عضو بدون حالة حمل",
                    "A member load has no load case",
                    memberIds = listOf(load.memberId)
                )
            }
        }

        for (panel in slabs) {
            if (panel.nodeIds.size != 4 || panel.nodeIds.distinct().size != 4) {
                issues += FrameValidationIssue(
                    FrameValidationCode.INVALID_SLAB,
                    "البلاطة ${panel.id} يجب أن تحتوي على 4 عقد مختلفة",
                    "Slab ${panel.id} must have 4 distinct joints",
                    nodeIds = panel.nodeIds
                )
                continue
            }
            val points = panel.nodeIds.mapNotNull { nodeById[it] }
            if (points.size != 4) {
                issues += FrameValidationIssue(
                    FrameValidationCode.INVALID_SLAB,
                    "البلاطة ${panel.id} مرتبطة بعقدة غير موجودة",
                    "Slab ${panel.id} references a missing joint",
                    nodeIds = panel.nodeIds
                )
                continue
            }
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                if (hypot(b.x - a.x, b.y - a.y) <= ZERO_LENGTH_TOLERANCE_M) {
                    issues += FrameValidationIssue(
                        FrameValidationCode.INVALID_SLAB,
                        "البلاطة ${panel.id} لها ضلع بطول صفر بين العقدتين ${a.id} و${b.id}",
                        "Slab ${panel.id} has a zero-length edge between joints ${a.id} and ${b.id}",
                        nodeIds = listOf(a.id, b.id)
                    )
                }
                val pair = if (a.id <= b.id) a.id to b.id else b.id to a.id
                if (pair !in seenMemberPairs) {
                    issues += FrameValidationIssue(
                        FrameValidationCode.SLAB_EDGE_WITHOUT_MEMBER,
                        "ضلع البلاطة ${panel.id} بين العقدتين ${a.id} و${b.id} لا يستند على عضو",
                        "Slab ${panel.id} edge between joints ${a.id} and ${b.id} has no supporting member",
                        nodeIds = listOf(a.id, b.id)
                    )
                }
            }
            if (panel.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "البلاطة ${panel.id} بدون حالة حمل",
                    "Slab ${panel.id} has no load case",
                    nodeIds = panel.nodeIds
                )
            }
        }

        return issues
    }
}
