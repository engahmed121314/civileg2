package com.civileg.app.domain.modeler

import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.MemberLoad
import com.civileg.app.domain.entities.NodalLoad
import com.civileg.app.domain.entities.SlabPanel
import com.civileg.app.utils.LocaleHelper
import kotlin.math.abs
import kotlin.math.hypot

enum class FrameValidationCode {
    INVALID_NUMBER,
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
    SLAB_EDGE_WITHOUT_MEMBER,
    DUPLICATE_SLAB,
    DEGENERATE_SLAB
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

    internal const val DUPLICATE_TOLERANCE_M = 1e-4
    internal const val ZERO_LENGTH_TOLERANCE_M = 1e-6
    internal const val ZERO_AREA_TOLERANCE_M2 = 1e-6

    fun validate(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad> = emptyList(),
        memberLoads: List<MemberLoad> = emptyList(),
        slabs: List<SlabPanel> = emptyList()
    ): List<FrameValidationIssue> {
        val issues = mutableListOf<FrameValidationIssue>()

        fun invalid(ar: String, en: String, nodeIds: List<Int> = emptyList(), memberIds: List<Int> = emptyList()) {
            issues += FrameValidationIssue(FrameValidationCode.INVALID_NUMBER, ar, en, nodeIds, memberIds)
        }
        nodes.filter { !it.x.isFinite() || !it.y.isFinite() }.forEach { n ->
            invalid("إحداثيات العقدة ${n.id}: أدخل قيم X وY محدودة بالمتر.", "Joint ${n.id} coordinates: enter finite X and Y values in metres.", nodeIds = listOf(n.id))
        }
        members.forEach { m ->
            m.concreteSection?.takeIf { m.materialType == com.civileg.app.domain.entities.FrameMaterialType.Concrete }?.let { c ->
                if (!c.width.isFinite() || c.width <= 0 || !c.depth.isFinite() || c.depth <= 0 ||
                    !c.fcu.isFinite() || c.fcu <= 0 || !c.fy.isFinite() || c.fy <= 0 || !c.cover.isFinite() || c.cover < 0 || c.effectiveDepth <= 0) {
                    invalid("مقطع العضو ${m.id}: أدخل عرضًا وعمقًا ومقاومات موجبة ومحدودة، وغطاءً غير سالب يترك عمقًا فعالًا موجبًا.",
                        "Member ${m.id} section: enter finite positive width, depth and strengths, and non-negative cover leaving positive effective depth.", memberIds = listOf(m.id))
                }
            }
        }
        nodalLoads.filter { !it.fx.isFinite() || !it.fy.isFinite() || !it.mz.isFinite() }.forEach { load ->
            invalid("حمل العقدة ${load.nodeId}: أدخل قيم Fx وFy وMz محدودة. الإشارة السالبة مسموحة.",
                "Joint ${load.nodeId} load: enter finite Fx, Fy and Mz values. Negative values are allowed.", nodeIds = listOf(load.nodeId))
        }
        memberLoads.forEach { load ->
            if (!load.value.isFinite()) invalid("حمل العضو ${load.memberId}: أدخل قيمة حمل محدودة؛ الإشارة السالبة مسموحة.",
                "Member ${load.memberId} load: enter a finite load value; negative values are allowed.", memberIds = listOf(load.memberId))
            val member = members.find { it.id == load.memberId }
            if (member != null && !FrameInputValidation.positionValid(load.loadType, load.position.toString(), member.getLength(nodes))) {
                val ramp = load.loadType == com.civileg.app.domain.entities.MemberLoadType.Trapezoidal
                val limit = member.getLength(nodes) / if (ramp) 2.0 else 1.0
                invalid(if (ramp) "حمل شبه منحرف (Trapezoidal) على العضو ${load.memberId}: أدخل طول ارتفاع أكبر من صفر ولا يزيد على $limit متر (نصف طول العضو)."
                    else "حمل العضو ${load.memberId}: أدخل موقعًا محدودًا بين صفر و$limit متر من عقدة البداية.",
                    if (ramp) "Trapezoidal load on member ${load.memberId}: enter a finite rise length greater than 0 and at most $limit m (half the member length)."
                    else "Member ${load.memberId} load: enter a finite position from 0 to $limit m from the start joint.", memberIds = listOf(load.memberId))
            }
        }
        slabs.filter { !it.thicknessMm.isFinite() || it.thicknessMm <= 0 || !it.loadKNm2.isFinite() }.forEach { slab ->
            invalid("البلاطة ${slab.id}: أدخل سمكًا موجبًا محدودًا بالمليمتر وحملًا محدودًا بالكيلونيوتن/م².",
                "Slab ${slab.id}: enter finite positive thickness in mm and a finite load in kN/m2.")
        }

        if (nodes.size < 2) {
            issues += FrameValidationIssue(
                FrameValidationCode.TOO_FEW_JOINTS,
                "أضف عقدتين على الأقل من قائمة العقد أو أداة رسم العقد، ثم اربطهما بعضو.",
                "Add at least two joints using the joints list or joint tool, then connect them with a member."
            )
        }
        if (members.isEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.NO_MEMBERS,
                "أضف عضوًا واحدًا على الأقل يصل بين عقدتين مختلفتين.",
                "Add at least one member connecting two different joints."
            )
        }

        val duplicateIds = nodes.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.DUPLICATE_JOINT_ID,
                "توجد أرقام عقد مكررة: ${duplicateIds.joinToString()}. صحح الأرقام لتكون فريدة قبل التحليل.",
                "Duplicate joint IDs: ${duplicateIds.joinToString()}. Give each joint a unique ID before solving.",
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
                "العقدتان ${a.id} و${b.id} لهما نفس الإحداثيات. عدّل موقع إحداهما أو احذف العقدة المكررة وأعد توصيل الأعضاء.",
                "Joints ${a.id} and ${b.id} have the same coordinates. Move one, or remove the duplicate and reconnect its members.",
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
                    "العضو ${member.id} مرتبط بعقدة غير موجودة. اختر عقدتي بداية ونهاية من قائمة العقد الموجودة.",
                    "Member ${member.id} references a missing joint. Select start and end IDs from the existing joints list.",
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
                    "العضو ${member.id} طوله صفر. اختر عقدتي نهاية بإحداثيات مختلفة.",
                    "Member ${member.id} has zero length. Choose end joints with different coordinates.",
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
                    "يوجد عضو مكرر بين العقدتين ${pair.first} و${pair.second}. احذف التكرار أو صحح عقدتي النهاية.",
                    "A duplicate member exists between joints ${pair.first} and ${pair.second}. Remove the duplicate or correct its end joints.",
                    nodeIds = listOf(pair.first, pair.second),
                    memberIds = listOf(member.id)
                )
            }
        }

        val disconnected = nodes.filter { it.id !in usedNodeIds }.map { it.id }
        if (nodes.size >= 2 && disconnected.isNotEmpty()) {
            issues += FrameValidationIssue(
                FrameValidationCode.DISCONNECTED_JOINT,
                "عقد غير متصلة بأي عضو: ${disconnected.joinToString()}. صل العقد المطلوبة بالأعضاء أو احذف العقد غير المستخدمة.",
                "Joints not connected to any member: ${disconnected.joinToString()}. Connect required joints or remove unused ones.",
                nodeIds = disconnected
            )
        }

        if (nodes.isNotEmpty() && nodes.none { it.support.restrainedDOFs.isNotEmpty() }) {
            issues += FrameValidationIssue(
                FrameValidationCode.NO_SUPPORT,
                "لا يوجد أي استناد في النموذج. حدد الارتكازات الفعلية من خصائص العقد؛ لا تضف قيدًا لا يمثّل المنشأ.",
                "The model has no supports. Set the actual supports in joint properties; do not add restraints that do not represent the structure."
            )
        }

        val memberIds = members.map { it.id }.toSet()
        for (load in nodalLoads) {
            if (nodeById[load.nodeId] == null) {
                issues += FrameValidationIssue(
                    FrameValidationCode.MISSING_LOAD_TARGET,
                    "حمل عقدي مرتبط بعقدة غير موجودة: ${load.nodeId}. اختر عقدة موجودة للحمل أو احذف الحمل غير المطلوب.",
                    "A nodal load references a missing joint: ${load.nodeId}. Select an existing joint or remove the unwanted load.",
                    nodeIds = listOf(load.nodeId)
                )
            }
            if (load.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "حمل العقدة ${load.nodeId} بدون حالة حمل. أدخل حالة مثل DL أو LL في خصائص الحمل.",
                    "Joint ${load.nodeId} load has no load case. Enter a case such as DL or LL in load properties.",
                    nodeIds = listOf(load.nodeId)
                )
            }
        }
        for (load in memberLoads) {
            if (load.memberId !in memberIds) {
                issues += FrameValidationIssue(
                    FrameValidationCode.MISSING_LOAD_TARGET,
                    "حمل مرتبط بعضو غير موجود: ${load.memberId}. اختر عضوًا موجودًا للحمل أو احذف الحمل غير المطلوب.",
                    "A load references a missing member: ${load.memberId}. Select an existing member or remove the unwanted load.",
                    memberIds = listOf(load.memberId)
                )
            }
            if (load.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "حمل العضو ${load.memberId} بدون حالة حمل. أدخل حالة مثل DL أو LL في خصائص الحمل.",
                    "Member ${load.memberId} load has no load case. Enter a case such as DL or LL in load properties.",
                    memberIds = listOf(load.memberId)
                )
            }
        }

        val duplicateSlabGroups = slabs
            .filter { it.nodeIds.size == 4 && it.nodeIds.distinct().size == 4 }
            .groupBy { it.nodeIds.sorted() }
            .filterValues { it.size > 1 }
        for ((joints, group) in duplicateSlabGroups) {
            val ids = group.map { it.id }.sorted()
            issues += FrameValidationIssue(
                FrameValidationCode.DUPLICATE_SLAB,
                "البلاطات ${ids.joinToString()} تشغل نفس العقد ${joints.joinToString()}. احذف التكرار أو صحح الأركان.",
                "Slabs ${ids.joinToString()} share the same joints ${joints.joinToString()}. Remove the duplicate panel or correct its corners.",
                nodeIds = joints
            )
        }

        for (panel in slabs) {
            if (panel.nodeIds.size != 4 || panel.nodeIds.distinct().size != 4) {
                issues += FrameValidationIssue(
                    FrameValidationCode.INVALID_SLAB,
                    "البلاطة ${panel.id} يجب أن تحتوي على 4 عقد مختلفة. أعد اختيار أركان البلاطة بالترتيب حول محيطها.",
                    "Slab ${panel.id} must have 4 distinct joints. Reselect its corners in perimeter order.",
                    nodeIds = panel.nodeIds
                )
                continue
            }
            val points = panel.nodeIds.mapNotNull { nodeById[it] }
            if (points.size != 4) {
                issues += FrameValidationIssue(
                    FrameValidationCode.INVALID_SLAB,
                    "البلاطة ${panel.id} مرتبطة بعقدة غير موجودة. أعد اختيار أربع عقد موجودة حول محيطها.",
                    "Slab ${panel.id} references a missing joint. Reselect four existing joints around its perimeter.",
                    nodeIds = panel.nodeIds
                )
                continue
            }
            if (segmentsProperlyIntersect(points[0], points[1], points[2], points[3]) ||
                segmentsProperlyIntersect(points[1], points[2], points[3], points[0])
            ) {
                issues += FrameValidationIssue(
                    FrameValidationCode.DEGENERATE_SLAB,
                    "البلاطة ${panel.id} أضلاعها متقاطعة. أعد اختيار الأركان بالترتيب حول المحيط، لا قطريًا.",
                    "Slab ${panel.id} has crossing edges. Reselect its corners in perimeter order, not diagonally.",
                    nodeIds = panel.nodeIds
                )
            } else if (quadArea(points) <= ZERO_AREA_TOLERANCE_M2) {
                issues += FrameValidationIssue(
                    FrameValidationCode.DEGENERATE_SLAB,
                    "البلاطة ${panel.id} مساحتها صفر. اختر أركانًا غير واقعة على خط واحد ولها مساحة فعلية.",
                    "Slab ${panel.id} has zero area. Choose non-collinear corners enclosing an actual area.",
                    nodeIds = panel.nodeIds
                )
            }
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                if (hypot(b.x - a.x, b.y - a.y) <= ZERO_LENGTH_TOLERANCE_M) {
                    issues += FrameValidationIssue(
                        FrameValidationCode.INVALID_SLAB,
                        "البلاطة ${panel.id} لها ضلع بطول صفر بين العقدتين ${a.id} و${b.id}. اختر أركانًا بإحداثيات مختلفة.",
                        "Slab ${panel.id} has a zero-length edge between joints ${a.id} and ${b.id}. Choose corners at different coordinates.",
                        nodeIds = listOf(a.id, b.id)
                    )
                }
                val pair = if (a.id <= b.id) a.id to b.id else b.id to a.id
                if (pair !in seenMemberPairs) {
                    issues += FrameValidationIssue(
                        FrameValidationCode.SLAB_EDGE_WITHOUT_MEMBER,
                        "ضلع البلاطة ${panel.id} بين العقدتين ${a.id} و${b.id} لا يستند على عضو. أضف العضو الفعلي أو صحح حدود البلاطة.",
                        "Slab ${panel.id} edge between joints ${a.id} and ${b.id} has no supporting member. Add the actual edge member or correct the panel boundary.",
                        nodeIds = listOf(a.id, b.id)
                    )
                }
            }
            if (panel.loadCase.isBlank()) {
                issues += FrameValidationIssue(
                    FrameValidationCode.EMPTY_LOAD_CASE,
                    "البلاطة ${panel.id} بدون حالة حمل. أدخل حالة مثل DL أو LL في خصائص البلاطة.",
                    "Slab ${panel.id} has no load case. Enter a case such as DL or LL in slab properties.",
                    nodeIds = panel.nodeIds
                )
            }
        }

        return issues
    }

    /** Unsigned area of a quadrilateral given in perimeter order (shoelace formula). */
    private fun quadArea(points: List<FrameNode>): Double {
        var twiceArea = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twiceArea += a.x * b.y - b.x * a.y
        }
        return abs(twiceArea) / 2.0
    }

    /** True only for a proper crossing, so shared endpoints never trigger the check. */
    private fun segmentsProperlyIntersect(
        a: FrameNode,
        b: FrameNode,
        c: FrameNode,
        d: FrameNode
    ): Boolean {
        fun cross(o: FrameNode, p: FrameNode, q: FrameNode): Double =
            (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)

        val d1 = cross(c, d, a)
        val d2 = cross(c, d, b)
        val d3 = cross(a, b, c)
        val d4 = cross(a, b, d)
        val straddlesFirst = (d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)
        val straddlesSecond = (d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0)
        return straddlesFirst && straddlesSecond
    }
}
