package com.civileg.app.domain.modeler

/** Expected user mistakes are values, never exceptions, fake IDs or partial mutations. */
sealed class FrameMutationResult<out T> {
    data class Applied<T>(val value: T) : FrameMutationResult<T>()
    data class Rejected(val issues: List<FrameValidationIssue>) : FrameMutationResult<Nothing>()
    object Unchanged : FrameMutationResult<Nothing>()
}

/** Full proposed edit batch. Construction may be incomplete, but not internally malformed. */
data class FrameEditCandidate(
    val nodes: List<com.civileg.app.domain.entities.FrameNode>,
    val members: List<com.civileg.app.domain.entities.FrameMember>,
    val nodalLoads: List<com.civileg.app.domain.entities.NodalLoad>,
    val memberLoads: List<com.civileg.app.domain.entities.MemberLoad>,
    val slabs: List<com.civileg.app.domain.entities.SlabPanel>
)

/** Built once per drag. Pointer updates never call the whole-model O(n²) validator.
 * Node list scan O(n), incident members/loads/slabs only. No list-difference membership scans.
 */
class FrameNodeMoveValidation(private val initial: FrameEditCandidate, private val nodeId: Int) {
    private val nodeById=initial.nodes.associateBy { it.id }
    private val incident=initial.members.filter { it.nodeI==nodeId || it.nodeJ==nodeId }
    private val incidentById=incident.associateBy { it.id }
    private val incidentIds=incidentById.keys
    private val loads=initial.memberLoads.filter { it.memberId in incidentIds }
    private val slabs=initial.slabs.filter { nodeId in it.nodeIds }
    fun validate(x:Double,y:Double):List<FrameValidationIssue> {
        fun bad(ar:String,en:String)=listOf(FrameValidationIssue(FrameValidationCode.INVALID_NUMBER,ar,en,nodeIds=listOf(nodeId)))
        val original=nodeById[nodeId] ?: return bad("العقدة غير موجودة. أعد اختيارها.","The joint no longer exists. Select it again.")
        if(!x.isFinite() || !y.isFinite()) return bad("إحداثيات العقدة $nodeId: أدخل قيمًا محدودة بالمتر.","Joint $nodeId coordinates: enter finite values in metres.")
        // Existing coincident faults can be moved apart incrementally, never made newly coincident.
        if(initial.nodes.any { n -> n.id!=nodeId && kotlin.math.hypot(n.x-x,n.y-y)<=FrameModelValidator.DUPLICATE_TOLERANCE_M &&
                kotlin.math.hypot(n.x-original.x,n.y-original.y)>FrameModelValidator.DUPLICATE_TOLERANCE_M })
            return bad("العقدة $nodeId: الموقع يطابق عقدة أخرى. اختر إحداثيات مختلفة.","Joint $nodeId: this position coincides with another joint. Choose different coordinates.")
        fun length(m:com.civileg.app.domain.entities.FrameMember,nx:Double,ny:Double):Double {
            val other=nodeById[if(m.nodeI==nodeId) m.nodeJ else m.nodeI] ?: return Double.NaN
            return kotlin.math.hypot(other.x-nx,other.y-ny)
        }
        val lengths=incident.associate { it.id to length(it,x,y) }
        if(incident.any { m -> val d=lengths.getValue(m.id); val prior=length(m,original.x,original.y)
                (!d.isFinite() || d<=FrameModelValidator.ZERO_LENGTH_TOLERANCE_M) && prior.isFinite() && prior>FrameModelValidator.ZERO_LENGTH_TOLERANCE_M })
            return bad("العقدة $nodeId: النقل يجعل عضوًا بطول صفر. اختر موقعًا آخر.","Joint $nodeId: this move creates a zero-length member. Choose another position.")
        if(loads.any { load -> val m=incidentById.getValue(load.memberId)
                FrameInputValidation.positionValid(load.loadType,load.position.toString(),length(m,original.x,original.y)) &&
                !FrameInputValidation.positionValid(load.loadType,load.position.toString(),lengths.getValue(m.id)) })
            return bad("العقدة $nodeId: النقل يجعل موقع حمل خارج العضو. عدّل الحمل أولًا أو اختر موقعًا آخر.","Joint $nodeId: this move puts a load outside its member. Edit the load first or choose another position.")
        fun validPanel(panel:com.civileg.app.domain.entities.SlabPanel,nx:Double,ny:Double):Boolean {
            if(panel.nodeIds.size!=4 || panel.nodeIds.distinct().size!=4) return false
            val points=panel.nodeIds.map { id -> nodeById[id]?.let { if(id==nodeId) it.copy(x=nx,y=ny) else it } ?: return false }
            fun cross(a:com.civileg.app.domain.entities.FrameNode,b:com.civileg.app.domain.entities.FrameNode,c:com.civileg.app.domain.entities.FrameNode)=
                (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
            fun intersects(a:Int,b:Int,c:Int,d:Int):Boolean {
                val u=cross(points[a],points[b],points[c]);val v=cross(points[a],points[b],points[d])
                val w=cross(points[c],points[d],points[a]);val z=cross(points[c],points[d],points[b])
                return ((u>0 && v<0)||(u<0 && v>0)) && ((w>0 && z<0)||(w<0 && z>0))
            }
            val area=kotlin.math.abs(points.indices.sumOf { i -> val j=(i+1)%4;points[i].x*points[j].y-points[j].x*points[i].y })/2.0
            return area.isFinite() && area>FrameModelValidator.ZERO_AREA_TOLERANCE_M2 && !intersects(0,1,2,3) && !intersects(1,2,3,0) && points.indices.all { i ->
                val j=(i+1)%4;kotlin.math.hypot(points[i].x-points[j].x,points[i].y-points[j].y)>FrameModelValidator.ZERO_LENGTH_TOLERANCE_M }
        }
        if(slabs.any { validPanel(it,original.x,original.y) && !validPanel(it,x,y) })
            return bad("العقدة $nodeId: النقل يفسد حدود البلاطة. اختر موقعًا يحافظ على ترتيب الأركان ومساحتها.","Joint $nodeId: this move invalidates a slab boundary. Preserve its corner order and area.")
        return emptyList()
    }
}

object FrameMutationValidation {
    private val constructionOnly = setOf(FrameValidationCode.TOO_FEW_JOINTS,
        FrameValidationCode.NO_MEMBERS, FrameValidationCode.DISCONNECTED_JOINT, FrameValidationCode.NO_SUPPORT)

    /** Existing malformed drafts may be repaired incrementally; only NEW defects reject an edit.
     * No supports/member count requirement while drawing. Solving still uses the full validator.
     */
    fun introduced(before: FrameEditCandidate, after: FrameEditCandidate): List<FrameValidationIssue> {
        fun issues(c: FrameEditCandidate) = FrameModelValidator.validate(c.nodes,c.members,c.nodalLoads,c.memberLoads,c.slabs)
            .filter { it.code !in constructionOnly }
        // Wording/locale/numeric formatting is not defect identity. Preserve multiplicity.
        fun identity(issue: FrameValidationIssue) = Triple(issue.code,issue.nodeIds.sorted(),issue.memberIds.sorted())
        val previous = issues(before).groupingBy(::identity).eachCount().toMutableMap()
        val result = issues(after).filter { issue ->
            val key=identity(issue)
            val count = previous[key] ?: 0
            if(count > 0) { previous[key] = count - 1; false } else true
        }.toMutableList()
        fun bad(ar: String, en: String) { result += FrameValidationIssue(FrameValidationCode.INVALID_NUMBER, ar, en) }
        if (after.nodes.filter { it !in before.nodes }.any { it.id <= 0 } || after.members.filter { it !in before.members }.any { it.id <= 0 } || after.slabs.filter { it !in before.slabs }.any { it.id <= 0 })
            bad("تعذر تخصيص رقم موجب. لم تُحفظ العملية. أعد فتح النموذج أو راجع أرقام العناصر.", "A positive ID could not be allocated. Nothing was changed. Reopen the model or review item IDs.")
        if (after.nodes.filter { it !in before.nodes }.any { !it.x.isFinite() || !it.y.isFinite() })
            bad("إحداثيات العقدة: أدخل قيمًا محدودة بالمتر. لم يتغير النموذج.", "Joint coordinates: enter finite values in metres. The model was not changed.")
        if (after.nodalLoads.filter { it !in before.nodalLoads }.any { !it.fx.isFinite() || !it.fy.isFinite() || !it.mz.isFinite() || it.loadCase.isBlank() })
            bad("الحمل العقدي: أدخل قيمًا محدودة وحالة حمل غير فارغة.", "Nodal load: enter finite values and a nonblank load case.")
        if (after.memberLoads.filter { it !in before.memberLoads }.any { !it.value.isFinite() || it.loadCase.isBlank() ||
                after.members.find { m -> m.id == it.memberId }?.let { m -> !FrameInputValidation.positionValid(it.loadType,it.position.toString(),m.getLength(after.nodes)) } != false })
            bad("حمل العضو: راجع العضو والقيمة والموقع وحالة الحمل. لم يتغير النموذج.", "Member load: check the target, value, position and load case. The model was not changed.")
        if (after.slabs.filter { it !in before.slabs }.any { !it.thicknessMm.isFinite() || it.thicknessMm <= 0 || !it.loadKNm2.isFinite() || it.loadCase.isBlank() })
            bad("البلاطة: أدخل سمكًا موجبًا وحملًا محدودًا وحالة حمل غير فارغة.", "Slab: enter positive thickness, finite load and a nonblank load case.")
        after.members.filter { it !in before.members }.forEach { member ->
            if (member.materialType == com.civileg.app.domain.entities.FrameMaterialType.Concrete && member.concreteSection == null)
                bad("العضو ${member.id}: أدخل خصائص المقطع الخرساني قبل الإضافة.", "Member ${member.id}: enter concrete section properties before adding it.")
            member.concreteSection?.takeIf { member.materialType == com.civileg.app.domain.entities.FrameMaterialType.Concrete }?.let { c ->
                if (!c.width.isFinite() || c.width <= 0 || !c.depth.isFinite() || c.depth <= 0 || !c.fcu.isFinite() || c.fcu <= 0 || !c.fy.isFinite() || c.fy <= 0 || !c.cover.isFinite() || c.cover < 0 || c.effectiveDepth <= 0)
                    bad("خصائص المقطع غير صالحة. راجع الأبعاد والمقاومات والغطاء.", "Invalid section properties. Check dimensions, strengths and cover.")
            }
            val steel = member.steelSectionName
            if (member.materialType == com.civileg.app.domain.entities.FrameMaterialType.Steel &&
                !com.civileg.app.domain.calculations.SteelFrameDesign.isAutoSection(steel) &&
                com.civileg.app.utils.SteelTables.getSectionByName(steel.orEmpty()) == null)
                bad("العضو ${member.id}: اختر قطاعًا حديديًا موجودًا أو الوضع التلقائي.", "Member ${member.id}: choose an existing steel section or Auto.")
        }
        return result
    }
}
