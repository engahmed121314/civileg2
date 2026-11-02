package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import com.civileg.app.domain.modeler.FrameModelValidator
import com.civileg.app.domain.modeler.FrameValidationCode
import com.civileg.app.utils.SteelTables
import java.util.TreeSet
import kotlin.math.*

/**
 * محرك تحليل الهياكل الإنشائية بطريقة مصفوفة الجساءة (Direct Stiffness Method)
 *
 * يدعم:
 * - إطارات ثنائية الأبعاد (2D Frames)
 * - عقد بحرية ومقيدة (حرة، مفصلية، بكرة، تثبيت كامل، بكرة رأسية)
 * - إطلاق نهايات الأعضاء (member end releases) مع تكثيف ساكن (static condensation)
 * - أحمال عقدية (قوى + عزوم) وأحمال على الأعضاء (منتظمة، مركزة، عزوم، متغيرة خطياً)
 * - الوزن الذاتي (C10)، مغلف توافيق الأحمال (C9)، فحص الاتزان العام (C8)
 * - النماذج المثبتة بالكامل صفر DOF (C6) — تحل بقوى النهاية الثابتة
 * - حساب القوى الداخلية ورسم المخططات بعينات حرجة (C7)
 *
 * ── اتفاقية الإشارة (S1 — مُتحقَّق منها عددياً) ──
 *   getFixedEndForces تُعيد P_eq = القوى التي يبذلها العضو المثبَّت طرفاه على العقد
 *   (= -q0 حيث q0 قوى المؤازرة على العضو):
 *     • تجميع متجه الأحمال:  F += FEF_global          (S1: كانت معكوسة في 9d098f4)
 *     • قوى نهاية العضو:      q_end = k·u_local - FEF
 *     • مخطط العزم:           M(x) = -m_i + v_i·x - (أحمال)   (S2)
 *     • القوة المحورية:       N(x) = -n_i (شد موجب)            (C12)
 *
 * ── المرحلة الثانية (C6..C13) ──
 *   C6  مسار صفر DOF (النماذج المثبتة بالكامل): U=0، q_end=-FEF، R=-F
 *   C7  عينات حرجة TreeSet عند مواقع الأحمال المركزة + a±ε + أرباع البحر
 *   C8  متبقيات الاتزان العام ΣFx/ΣFy/ΣMz (شاملة أحمال الأعضاء كقوى خارجية)
 *   C9  مغلف القوى عبر توافيق DL/LL/WL (من أقصى/أدنى قيم المخططات على طول العضو)
 *   C10 الوزن الذاتي: خرسانة γ=25 kN/m³، حديد (kg/m)×g/1000 — الأفقي UDL،
 *      والمائل/الرأسي حملان عقيديان رأسيان (صواب استاتيكي تام للارتكازات)
 *   C11 تدقيق وحدات E (الخرسانة Ec=4700·√fcu MPa، والحديد eColumn بالفعل kN/m²)
 *   C12 N = -n_i (شد موجب)
 *   C13 اتساق قوى النهايات مع المخططات: V(0)=v_i، M(0)=-m_i، M(L)=+m_j
 *
 * ── إصلاحات المرحلة الأولى (C1..C5, S1..S4) ──
 *   C1  إحداثيات منتهية + معرفات فريدة (مع FrameModelValidator)
 *   C2  سماحية محور نسبية + فحص متبقٍ نسبي للحل العددي
 *   C3  اتصال النموذج (عقد معزولة حرة = خطأ، مثبتة تماماً = غير مؤذية)
 *   C4  مراجع الأعضاء والمقاطع الصالحة
 *   C5  أحمال صالحة (قيم منتهية، مواقع داخل طول العضو)
 *   S3  تثبيت مواقع الأحمال الطرفية داخل العضو (clamp) + صيغة FEF للعزم المركز
 *       مُعاد اشتقاقها والتحقق منها بطريقتين مستقلتين (تكامل مباشر + طريقة القوى)
 *   S4  fallback لمقطع الحديد بالاسم (ipeSections[13] لم يكن IPE 300)
 */
object FrameAnalysisEngine {

    private const val DOF_PER_NODE = 3  // dx, dy, rz

    /** أقل طول مقبول للعضو (م) */
    private const val MIN_MEMBER_LENGTH_M = 1e-6

    /** سماحية المحور النسبية — المصفوفة تتراوح بين ~1e3 و~1e8 kN/m² */
    private const val PIVOT_REL_TOL = 1e-12

    /** الحد الأعلى للمتبقي النسبي ||K·U-F||∞/||F||∞ */
    private const val RESIDUAL_REL_TOL = 1e-6

    /** هامش تثبيت موقع الحمولة داخل العضو [clamp] لتفادي إسقاط الأحمال الطرفية */
    private const val LOAD_POS_EPS = 1e-9

    /** سماحية فحص الاتزان العام النسبية */
    private const val EQUILIBRIUM_REL_TOL = 1e-3

    /** وزن وحدة حجم الخرسانة (kN/m³) — C10 */
    private const val CONCRETE_UNIT_WEIGHT_KN_M3 = 25.0

    /** تسارع الجذبية (m/s²) — C10 */
    private const val G_ACCEL = 9.81

    // ========================================================================
    // Public data for UI (Equations & Solution Steps panel)
    // ========================================================================

    /**
     * خصائص جساءة عضو — تُعرض في لوحة "المعادلات وخطوات الحل" (C11).
     */
    data class MemberStiffnessProps(
        val memberId: Int,
        val E: Double,          // kN/m²  معامل المرونة الفعّال
        val A: Double,          // m²     مساحة المقطع
        val I: Double,          // m⁴     عزم القصور الفعّال (متشقق للخرسانة)
        val Ig: Double,         // m⁴     عزم القصور الكلي (خرسانة فقط، وإلا = I)
        val L: Double,          // m      الطول
        val angleDeg: Double,   // درجة   زاوية الميل مع الأفقي
        val sectionLabel: String,
        val materialType: FrameMaterialType
    )

    /** أقصى/أدنى قيم مخططات عضو واحد لتوفيق أحمال واحد (تُستخدم داخلياً في C9) */
    private data class DiagramExtremes(
        val comboName: String,
        val maxMoment: Double,
        val minMoment: Double,
        val maxAbsMoment: Double,
        val maxShear: Double,
        val minShear: Double,
        val maxAxial: Double,
        val minAxial: Double
    )

    // ========================================================================
    // Main Solver
    // ========================================================================

    /**
     * حل الإطار بالكامل — wrapper يطبّق ميزات المرحلة الثانية C9/C10.
     *
     *  1. يضيف الوزن الذاتي للأعضاء إذا فُعّل (C10).
     *  2. يحسب مغلف القوى عبر توافيق الأحمال (C9).
     *  3. يتحقق من الاتزان العام (C8) داخل solveCore ويُكشف في النتيجة.
     *
     * @return FrameAnalysisResult يحتوي على إزاحات، قوى داخلية، ومخططات
     */
    fun solveFrame(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): FrameAnalysisResult {
        // PERFORMANCE METRIC: Measure solver execution time
        val startTimeNs = System.nanoTime()

        // ── C10: الوزن الذاتي — أحمال UDL للأعضاء الأفقية + أحمال عقدية رأسية للمائلة ──
        val (swMemberLoads, swNodalLoads) = selfWeightLoads(members, nodes, settings)
        val effectiveMemberLoads = memberLoads + swMemberLoads
        val effectiveNodalLoads = nodalLoads + swNodalLoads

        // ── C9: مغلف القوى عبر توافيق الأحمال (حل منفصل لكل توفيق) ──
        val envelopes = computeLoadEnvelopes(
            nodes, members, effectiveNodalLoads, effectiveMemberLoads, settings, startTimeNs
        )

        // ── الحل الأساسي (بدون معاملات توفيق) ──
        val baseResult = solveCore(
            nodes, members, effectiveNodalLoads, effectiveMemberLoads, settings, startTimeNs
        )
        if (!baseResult.isSolved) return baseResult
        if (envelopes != null) return baseResult.copy(loadEnvelopes = envelopes)
        return baseResult
    }

    /**
     * الحل الأساسي: validation → assembly → partitioning → solve → diagrams،
     * مع فحص الاتزان العام (C8) ومسار صفر DOF (C6).
     */
    private fun solveCore(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        startTimeNs: Long
    ): FrameAnalysisResult {
        // ── InputGuard (ADR-010) — أخطاء المبرمج: نموذج فارغ يفشل بصوت عالٍ ──
        InputGuard.notEmpty("nodes", nodes)
        InputGuard.notEmpty("members", members)
        InputGuard.notNull("settings", settings)

        try {
            // ── C1..C5 + Frame Modeler validation ──
            // عقدة منفصلة عن كل الأعضاء لكنها مثبتة تثبيتاً كاملاً (كل درجاتها
            // مقيدة): لا تُدخل شذوذاً في نظام المعادلات — تُعتبر إنذار نمذجة
            // غير قاتل للحل، بينما العقدة المنفصلة الحرة قاتلة (C3).
            val validationIssues = FrameModelValidator.validate(
                nodes = nodes,
                members = members,
                nodalLoads = nodalLoads,
                memberLoads = memberLoads
            ).filterNot { issue ->
                issue.code == FrameValidationCode.DISCONNECTED_JOINT &&
                    issue.nodeIds.all { id ->
                        nodes.firstOrNull { it.id == id }
                            ?.support?.restrainedDOFs?.size == DOF_PER_NODE
                    }
            }
            if (validationIssues.isNotEmpty()) {
                val shown = validationIssues.take(5).joinToString("\n") { it.message }
                val suffix = if (validationIssues.size > 5) {
                    "\n+${validationIssues.size - 5} ..."
                } else {
                    ""
                }
                return FrameAnalysisResult(errorMessage = shown + suffix)
            }

            // ── C1/C4/C5: فحوص تكميلية لا يغطيها FrameModelValidator ──
            supplementaryValidation(nodes, members, nodalLoads, memberLoads)?.let {
                return FrameAnalysisResult(errorMessage = it)
            }

            val numNodes = nodes.size
            val totalDOF = numNodes * DOF_PER_NODE

            // 1. Build global stiffness matrix
            val Kg = Array(totalDOF) { DoubleArray(totalDOF) }
            for (member in members) {
                assembleMemberStiffness(member, nodes, settings, Kg)
            }

            // 2. Build load vector (nodal loads + equivalent loads from member loads)
            val F = DoubleArray(totalDOF)
            for (load in nodalLoads) {
                val nodeIdx = nodes.indexOfFirst { it.id == load.nodeId }
                if (nodeIdx >= 0) {
                    F[nodeIdx * 3] += load.fx
                    F[nodeIdx * 3 + 1] += load.fy
                    F[nodeIdx * 3 + 2] += load.mz
                }
            }
            // Add fixed-end forces from member loads
            for (mLoad in memberLoads) {
                val mIdx = members.indexOfFirst { it.id == mLoad.memberId }
                if (mIdx < 0) continue
                val member = members[mIdx]
                val fefLocal = getFixedEndForces(member, mLoad, nodes, settings)

                // CRITICAL FIX: FEF must be transformed from LOCAL to GLOBAL before assembly into F
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)

                // local: [fi_x, fi_y, mi_z, fj_x, fj_y, mj_z]
                // global: [Fi_X, Fi_Y, Mi_Z, Fj_X, Fj_Y, Mj_Z]
                val fefGlobal = DoubleArray(6)
                fefGlobal[0] = fefLocal[0] * cosA - fefLocal[1] * sinA
                fefGlobal[1] = fefLocal[0] * sinA + fefLocal[1] * cosA
                fefGlobal[2] = fefLocal[2]
                fefGlobal[3] = fefLocal[3] * cosA - fefLocal[4] * sinA
                fefGlobal[4] = fefLocal[3] * sinA + fefLocal[4] * cosA
                fefGlobal[5] = fefLocal[5]

                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni >= 0 && nj >= 0) {
                    // S1 FIX (تحقق عددي): P_eq = +FEF_global — القوى التي يبذلها
                    // العضو المثبَّت على العقد. (كانت معكوسة الإشارة في 9d098f4
                    // فأعطت عزوم نهايات خاطئة wL²/6 واتزاناً عاماً مختلاً 2wL.)
                    F[ni * 3] += fefGlobal[0]; F[ni * 3 + 1] += fefGlobal[1]; F[ni * 3 + 2] += fefGlobal[2]
                    F[nj * 3] += fefGlobal[3]; F[nj * 3 + 1] += fefGlobal[4]; F[nj * 3 + 2] += fefGlobal[5]
                }
            }

            // 3. Identify restrained DOFs
            val restrainedDOFs = mutableListOf<Int>()
            for ((idx, node) in nodes.withIndex()) {
                for (dof in node.support.restrainedDOFs) {
                    restrainedDOFs.add(idx * 3 + dof)
                }
            }

            // 4. Partition and solve
            val freeDOFs = (0 until totalDOF).filter { it !in restrainedDOFs }

            // Member-end releases can leave a node rotation with zero stiffness.
            // Such DOFs are inactive (the connected member is hinged there); they
            // must not enter the stiffness system or it becomes singular.
            val inactiveDOFs = freeDOFs.filter { abs(Kg[it][it]) < 1e-9 }
            if (inactiveDOFs.any { abs(F[it]) > 1e-9 }) {
                return FrameAnalysisResult(
                    errorMessage = "يوجد حمل على درجة حرية غير فعالة - تحقق من المفاصل / Load on an inactive DOF - check member end releases"
                )
            }
            val activeFreeDOFs = freeDOFs.filter { it !in inactiveDOFs }

            // ── C6: مسار صفر DOF فعّال (الإطار مقيّد بالكامل) ──
            // هندسياً: U = 0 → q_end = -FEF، R = -F — مسار صالح تماماً.
            if (activeFreeDOFs.isEmpty()) {
                val U_zero = DoubleArray(totalDOF) // كلها صفر
                return finalizeFullyRestrainedSolution(
                    nodes, members, nodalLoads, memberLoads, settings, Kg, F, U_zero, startTimeNs
                )
            }

            val Kff = Array(activeFreeDOFs.size) { i -> DoubleArray(activeFreeDOFs.size) { j -> Kg[activeFreeDOFs[i]][activeFreeDOFs[j]] } }
            val Ff = DoubleArray(activeFreeDOFs.size) { F[activeFreeDOFs[it]] }

            val Uf = solveLinearSystem(Kff, Ff) ?: return FrameAnalysisResult(
                errorMessage = "فشل حل نظام المعادلات (ميكانيزم/عدم استقرار) - تحقق من الارتكازات والوصلات / Singular stiffness system (mechanism) - check supports & connectivity"
            )

            if (Uf.any { it.isNaN() || it.isInfinite() }) {
                return FrameAnalysisResult(errorMessage = "نتائج غير منطقية (NaN/Inf) - تحقق من استقرار المنشأ / Non-finite solution - check stability")
            }

            // C2: فحص المتبقيات النسبي ||Kff·Uf - Ff||∞ / ||Ff||∞
            val residualError = relativeResidual(Kff, Uf, Ff)
            if (residualError != null && residualError > RESIDUAL_REL_TOL) {
                return FrameAnalysisResult(
                    errorMessage = "الحل العددي غير مستقر (متبقٍ نسبي = %.2e) - المنشأ قريب من الميكانيزم / Ill-conditioned system (rel. residual = %.2e)".format(residualError, residualError)
                )
            }

            // 5. Full displacement vector
            val U = DoubleArray(totalDOF)
            for (i in activeFreeDOFs.indices) U[activeFreeDOFs[i]] = Uf[i]

            // 6. Compute reactions
            val reactions = DoubleArray(totalDOF)
            for (i in 0 until totalDOF) {
                var sum = 0.0
                for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
                reactions[i] = sum - F[i]
            }

            // 7. Member end forces
            val memberForces = mutableListOf<MemberEndForces>()
            val memberDiagrams = mutableListOf<MemberDiagram>()

            for (member in members) {
                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni < 0 || nj < 0) continue

                val L = member.getLength(nodes)
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)

                // Member stiffness properties
                val E = getE(member, settings)
                val (A, I) = getSectionProperties(member, settings)
                val k = effectiveMemberStiffness(member, memberLocalStiffness(E, A, I, L))

                // Local end displacements
                val uGlobalI = doubleArrayOf(U[ni * 3], U[ni * 3 + 1], U[ni * 3 + 2])
                val uGlobalJ = doubleArrayOf(U[nj * 3], U[nj * 3 + 1], U[nj * 3 + 2])
                val uLocal = globalToLocalDisplacements(uGlobalI, uGlobalJ, cosA, sinA)

                // Local member forces = k * u_local
                val fLocal = DoubleArray(6) {
                    var sum = 0.0
                    for (j in 0..5) sum += k[it][j] * uLocal[j]
                    sum
                }

                // Add fixed-end forces for member loads on this member.
                // FEF باتفاقية P_eq (قوى على العقد) → قوى النهاية الكلية:
                // q_end = k·u - FEF  (S1)
                val fefThisMember = DoubleArray(6)
                val memberLoadList = memberLoads.filter { it.memberId == member.id }
                for (mLoad in memberLoadList) {
                    val fef = getFixedEndForces(member, mLoad, nodes, settings)
                    for (i in 0..5) fefThisMember[i] += fef[i]
                }
                val fTotal = DoubleArray(6) { fLocal[it] - fefThisMember[it] }

                if (fTotal.any { it.isNaN() || it.isInfinite() }) {
                    return FrameAnalysisResult(
                        errorMessage = "قوى غير منتهية للعضو #${member.id} - تحقق من خصائص المقطع / Non-finite forces for member #${member.id}"
                    )
                }

                // Convert to global
                val fiX = fTotal[0] * cosA - fTotal[1] * sinA
                val fiY = fTotal[0] * sinA + fTotal[1] * cosA
                val fjX = fTotal[3] * cosA - fTotal[4] * sinA
                val fjY = fTotal[3] * sinA + fTotal[4] * cosA

                memberForces.add(MemberEndForces(
                    memberId = member.id,
                    fi_x = fiX, fi_y = fiY, mi_z = fTotal[2],
                    fj_x = fjX, fj_y = fjY, mj_z = fTotal[5]
                ))

                // Generate diagrams
                val diagram = generateDiagrams(member, nodes, fTotal, memberLoadList, settings)
                memberDiagrams.add(diagram)
            }

            // 8. Node results
            val nodeResults = nodes.mapIndexed { idx, node ->
                val localRestrained = node.support.restrainedDOFs
                NodeResult(
                    nodeId = node.id,
                    dx = U[idx * 3],
                    dy = U[idx * 3 + 1],
                    rz = U[idx * 3 + 2],
                    reactionFx = if (0 in localRestrained) reactions[idx * 3] else 0.0,
                    reactionFy = if (1 in localRestrained) reactions[idx * 3 + 1] else 0.0,
                    reactionMz = if (2 in localRestrained) reactions[idx * 3 + 2] else 0.0
                )
            }

            val durationMs = (System.nanoTime() - startTimeNs) / 1e6
            println("Frame analysis solved in ${"%.2f".format(durationMs)} ms ($numNodes nodes, ${members.size} members, $totalDOF DOFs)")

            // ── C8: فحص الاتزان العام — ΣR + ΣF_applied = 0 (شاملة أحمال الأعضاء) ──
            val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(
                nodes, members, nodalLoads, memberLoads, nodeResults
            )
            val appliedMag = sqrt(eqFx * eqFx + eqFy * eqFy + eqMz * eqMz)
            if (appliedMag > EQUILIBRIUM_REL_TOL * maxOf(1.0,
                    nodalLoads.sumOf { abs(it.fx) + abs(it.fy) + abs(it.mz) } +
                    memberLoads.sumOf { abs(it.value) })) {
                return FrameAnalysisResult(
                    errorMessage = "الاتزان العام غير محقق (resFx=${"%.2e".format(eqFx)}, resFy=${"%.2e".format(eqFy)}, resMz=${"%.2e".format(eqMz)}) / Global equilibrium violated"
                )
            }

            return FrameAnalysisResult(
                nodeResults = nodeResults,
                memberEndForces = memberForces,
                memberDiagrams = memberDiagrams,
                isSolved = true,
                equilibriumFx = eqFx,
                equilibriumFy = eqFy,
                equilibriumMz = eqMz
            )
        } catch (e: Exception) {
            return FrameAnalysisResult(errorMessage = "خطأ في التحليل: ${e.message}")
        }
    }

    // ========================================================================
    // C1/C4/C5 — Supplementary validation (not covered by FrameModelValidator)
    // ========================================================================

    /**
     * فحوص تكميلية: إحداثيات منتهية، مقاطع صالحة، قيم أحمال منتهية،
     * ومواقع الأحمال المركزة داخل طول العضو. يُعيد رسالة خطأ أو null.
     */
    private fun supplementaryValidation(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>
    ): String? {
        // C1: إحداثيات منتهية
        for (n in nodes) {
            if (!n.x.isFinite() || !n.y.isFinite()) {
                return "إحداثيات العقدة #${n.id} غير صالحة (NaN/∞) / Node #${n.id} has non-finite coordinates"
            }
        }
        // C4: مقاطع صالحة
        for (m in members) {
            when (m.materialType) {
                FrameMaterialType.Concrete -> {
                    val cs = m.concreteSection
                    if (cs == null || !cs.width.isFinite() || !cs.depth.isFinite() ||
                        cs.width <= 0.0 || cs.depth <= 0.0 ||
                        !cs.fcu.isFinite() || cs.fcu <= 0.0) {
                        return "مقطع خرساني غير صالح للعضو #${m.id} (b=${cs?.width}, h=${cs?.depth}, fcu=${cs?.fcu}) / Invalid concrete section on member #${m.id}"
                    }
                }
                FrameMaterialType.Steel -> {
                    val name = m.steelSectionName
                    if (name.isNullOrBlank() || SteelTables.getSectionByName(name) == null) {
                        return "مقطع حديدي غير معروف للعضو #${m.id}: \"$name\" / Unknown steel section on member #${m.id}"
                    }
                }
            }
        }
        // C5: أحمال عقدية صالحة
        for (nl in nodalLoads) {
            if (!nl.fx.isFinite() || !nl.fy.isFinite() || !nl.mz.isFinite()) {
                return "قيم حمل عقدي غير صالحة عند العقدة #${nl.nodeId} / Non-finite nodal load at node #${nl.nodeId}"
            }
        }
        // C5: أحمال أعضاء صالحة + مواقع داخل الطول
        for (ml in memberLoads) {
            if (!ml.value.isFinite()) {
                return "قيمة حمل غير صالحة على العضو #${ml.memberId} / Non-finite load value on member #${ml.memberId}"
            }
            if (ml.loadType == MemberLoadType.PointLoad || ml.loadType == MemberLoadType.Moment) {
                val member = members.find { it.id == ml.memberId } ?: continue
                val L = member.getLength(nodes)
                if (ml.position < -1e-9 || ml.position > L + 1e-9) {
                    return "موقع الحمل (${ml.position} م) خارج طول العضو #${ml.memberId} ($L م) / Load position out of range on member #${ml.memberId}"
                }
            }
            // المرحلة الثالثة: شبه المنحرف يتطلب 0 < a ≤ L/2 (a = L/2 هو المثلث
            // المتماثل؛ a > L/2 يعني هضبة سالبة الطول — شكل هندسي غير صالح)
            if (ml.loadType == MemberLoadType.Trapezoidal) {
                val member = members.find { it.id == ml.memberId } ?: continue
                val L = member.getLength(nodes)
                if (ml.position <= 0.0 || ml.position > L / 2.0 + 1e-9) {
                    return "طول ارتفاع شبه المنحرف (${ml.position} م) غير صالح للعضو #${ml.memberId} (المطلوب 0 < a ≤ L/2 = ${"%.3f".format(L / 2.0)} م) / Trapezoidal rise out of range on member #${ml.memberId}"
                }
            }
        }
        return null
    }

    // ========================================================================
    // C6: Finalize fully-restrained solution (zero active DOFs)
    // ========================================================================

    /**
     * C6: معالجة النماذج المثبتة بالكامل — U=0 لكن قوى النهايات = -FEF،
     * ردود الأفعال = -F. مسار صالح هندسياً بالكامل.
     */
    private fun finalizeFullyRestrainedSolution(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>,
        F: DoubleArray,
        U: DoubleArray,
        startTimeNs: Long
    ): FrameAnalysisResult {
        val nodeIndexMap = nodes.withIndex().associate { it.value.id to it.index }
        val totalDOF = nodes.size * 3

        // C6: في مسار صفر DOF يكون U=0 → K·U=0 → R = -F
        val reactions = DoubleArray(totalDOF)
        for (i in 0 until totalDOF) {
            var sum = 0.0
            for (j in 0 until totalDOF) sum += Kg[i][j] * U[j]
            reactions[i] = sum - F[i]
        }

        // قوى النهايات: q_end = k·u_local - FEF = -FEF (لأن u=0)
        val memberForces = mutableListOf<MemberEndForces>()
        val memberDiagrams = mutableListOf<MemberDiagram>()
        for (member in members) {
            if (nodeIndexMap[member.nodeI] == null || nodeIndexMap[member.nodeJ] == null) continue
            val cosA = member.getCosTheta(nodes)
            val sinA = member.getSinTheta(nodes)

            // FEF هذا العضو (محلي، بعد التكثيف للعضويات المفصلية)
            var fefThisMember = DoubleArray(6)
            val memberLoadList = memberLoads.filter { it.memberId == member.id }
            for (mLoad in memberLoadList) {
                val fef = getFixedEndForces(member, mLoad, nodes, settings)
                for (i in 0..5) fefThisMember[i] += fef[i]
            }
            // q_end = -FEF (لأن u_local = 0)
            val fTotal = DoubleArray(6) { -fefThisMember[it] }

            val fiX = fTotal[0] * cosA - fTotal[1] * sinA
            val fiY = fTotal[0] * sinA + fTotal[1] * cosA
            val fjX = fTotal[3] * cosA - fTotal[4] * sinA
            val fjY = fTotal[3] * sinA + fTotal[4] * cosA

            memberForces.add(MemberEndForces(
                memberId = member.id,
                fi_x = fiX, fi_y = fiY, mi_z = fTotal[2],
                fj_x = fjX, fj_y = fjY, mj_z = fTotal[5]
            ))
            memberDiagrams.add(generateDiagrams(member, nodes, fTotal, memberLoadList, settings))
        }

        val nodeResults = nodes.mapIndexed { idx, node ->
            val localRestrained = node.support.restrainedDOFs
            NodeResult(
                nodeId = node.id,
                dx = U[idx * 3], dy = U[idx * 3 + 1], rz = U[idx * 3 + 2],
                reactionFx = if (0 in localRestrained) reactions[idx * 3] else 0.0,
                reactionFy = if (1 in localRestrained) reactions[idx * 3 + 1] else 0.0,
                reactionMz = if (2 in localRestrained) reactions[idx * 3 + 2] else 0.0
            )
        }

        // C8: فحص الاتزان العام في مسار صفر DOF (شاملة الأحمال العقدية والأعضاء)
        val (eqFx, eqFy, eqMz) = computeGlobalEquilibrium(
            nodes, members, nodalLoads, memberLoads, nodeResults
        )

        val durationMs = (System.nanoTime() - startTimeNs) / 1e6
        println("Frame analysis (fully restrained, C6) solved in ${"%.2f".format(durationMs)} ms")

        return FrameAnalysisResult(
            nodeResults = nodeResults,
            memberEndForces = memberForces,
            memberDiagrams = memberDiagrams,
            isSolved = true,
            equilibriumFx = eqFx,
            equilibriumFy = eqFy,
            equilibriumMz = eqMz
        )
    }

    // ========================================================================
    // C8: Global equilibrium check (ΣFx, ΣFy, ΣMz residuals)
    // ========================================================================

    /**
     * C8: متبقيات الاتزان العام (يجب أن تكون ≈ 0):
     *   resFx = ΣFx,reaction + ΣFx,applied
     *   resFy = ΣFy,reaction + ΣFy,applied
     *   resMz = ΣMz,reaction + Σ(Mz,applied + x·Fy − y·Fx)
     *
     * الأحمال المطبقة تشمل العقدية وأحمال الأعضاء (قوى خارجية حقيقية) —
     * يُحسب مجموع أحمال الأعضاء بتحليل اتجاهها المحلي (0,-w) إلى العالمي.
     */
    private fun computeGlobalEquilibrium(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        nodeResults: List<NodeResult>
    ): Triple<Double, Double, Double> {
        // مجموع ردود الأفعال (مع ذراعها حول نقطة الأصل)
        var sumRx = 0.0
        var sumRy = 0.0
        var sumMz = 0.0
        for ((idx, node) in nodes.withIndex()) {
            val rx = nodeResults[idx].reactionFx
            val ry = nodeResults[idx].reactionFy
            val mz = nodeResults[idx].reactionMz
            sumRx += rx
            sumRy += ry
            sumMz += mz + node.x * ry - node.y * rx
        }
        // مجموع الأحمال العقدية
        var sumFx = 0.0
        var sumFy = 0.0
        var sumMzApplied = 0.0
        for (nl in nodalLoads) {
            val node = nodes.find { it.id == nl.nodeId } ?: continue
            sumFx += nl.fx
            sumFy += nl.fy
            sumMzApplied += nl.mz + node.x * nl.fy - node.y * nl.fx
        }
        // C8: أحمال الأعضاء — قوى خارجية تُضاف بمجموعها الكلي (محلي → عامي)
        for (ml in memberLoads) {
            val member = members.find { it.id == ml.memberId } ?: continue
            val iNode = nodes.find { it.id == member.nodeI } ?: continue
            val cosA = member.getCosTheta(nodes)
            val sinA = member.getSinTheta(nodes)
            val L = member.getLength(nodes)
            // حمل في الاتجاه المحلي (0, -w) → العالمي: (w·sinA, -w·cosA)
            when (ml.loadType) {
                MemberLoadType.UDL -> {
                    val fx = ml.value * L * sinA
                    val fy = -ml.value * L * cosA
                    val px = iNode.x + 0.5 * L * cosA
                    val py = iNode.y + 0.5 * L * sinA
                    sumFx += fx; sumFy += fy
                    sumMzApplied += px * fy - py * fx
                }
                MemberLoadType.PointLoad -> {
                    val a = ml.position.coerceIn(0.0, L)
                    val fx = ml.value * sinA
                    val fy = -ml.value * cosA
                    val px = iNode.x + a * cosA
                    val py = iNode.y + a * sinA
                    sumFx += fx; sumFy += fy
                    sumMzApplied += px * fy - py * fx
                }
                MemberLoadType.Moment -> {
                    // عزم نقي (z المحلي = z العامي لإطار ثنائي الأبعاد)
                    sumMzApplied += ml.value
                }
                MemberLoadType.LinearVarying -> {
                    // مثلثي (0 عند I، w عند J): الكلية wL/2 عند 2L/3 من I
                    val fx = ml.value * L * 0.5 * sinA
                    val fy = -ml.value * L * 0.5 * cosA
                    val px = iNode.x + (2.0 / 3.0) * L * cosA
                    val py = iNode.y + (2.0 / 3.0) * L * sinA
                    sumFx += fx; sumFy += fy
                    sumMzApplied += px * fy - py * fx
                }
                MemberLoadType.Trapezoidal -> {
                    // المرحلة الثالثة: شبه منحرف متماثل — الكلية w(L−a) عند منتصف
                    // البحر (مركز الثقل لعزم متماثل) كما في UDL تماماً.
                    val totalT = ml.value * (L - ml.position)
                    val fx = totalT * sinA
                    val fy = -totalT * cosA
                    val px = iNode.x + 0.5 * L * cosA
                    val py = iNode.y + 0.5 * L * sinA
                    sumFx += fx; sumFy += fy
                    sumMzApplied += px * fy - py * fx
                }
            }
        }
        val resFx = sumRx + sumFx
        val resFy = sumRy + sumFy
        val resMz = sumMz + sumMzApplied
        return Triple(resFx, resFy, resMz)
    }

    // ========================================================================
    // C10 + C9: Self-weight loads + Load combinations envelope
    // ========================================================================

    /**
     * C10: أحمال الوزن الذاتي للأعضاء.
     *
     * خرسانة: w = γ·b·h = 25 × (b×h بالمتر المربع) kN/m
     * حديد:   w = (kg/m) × g/1000 = (kg/m) × 9.81/1000 kN/m
     *
     * تحليل فيزيائي للجاذبية (w هابط رأسياً لكل متر طولي من العضو):
     *   - العرضية w·cosθ: UDL محلي هابط — يُلتقط انحناء الوزن الذاتي للكمرات
     *     والعوارض المائلة بدقة (الأفقي: w كاملة، الرأسي: صفر).
     *   - المحورية w·sinθ·L: حملان عقيديان على طول محور العضو (نصف لكل طرف)
     *     لأن المحرك لا يدعم الأحمال المحورية الموزعة — صواب استاتيكي تام
     *     للارتكازات وردود الأفعال، ومخطط N قريب من الثابت (تقريب موثّق).
     */
    private fun selfWeightLoads(
        members: List<FrameMember>,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): Pair<List<MemberLoad>, List<NodalLoad>> {
        if (!settings.includeSelfWeight) return emptyList<MemberLoad>() to emptyList<NodalLoad>()
        val udlLoads = mutableListOf<MemberLoad>()
        val nodalLoads = mutableListOf<NodalLoad>()
        for (m in members) {
            val wPerM = when (m.materialType) {
                FrameMaterialType.Concrete -> {
                    val cs = m.concreteSection ?: continue
                    CONCRETE_UNIT_WEIGHT_KN_M3 * (cs.width / 1000.0) * (cs.depth / 1000.0)
                }
                FrameMaterialType.Steel -> {
                    val sec = SteelTables.getSectionByName(m.steelSectionName ?: "IPE 300") ?: continue
                    sec.weight * G_ACCEL / 1000.0   // kg/m × m/s² / 1000 = kN/m
                }
            }
            if (!wPerM.isFinite() || wPerM <= 0.0) continue
            val L = m.getLength(nodes)
            if (!L.isFinite() || L <= 0.0) continue
            val cosT = m.getCosTheta(nodes)
            val sinT = m.getSinTheta(nodes)
            // المركّبة العرضية w·cosθ: UDL محلي هابط — انحناء الوزن الذاتي
            // للعوارض المائلة (rafters) يُلتقط فعلياً (كان يُهمل قبل هذا).
            val transverse = wPerM * cosT
            if (transverse > 1e-9) {
                udlLoads.add(MemberLoad(
                    memberId = m.id,
                    loadType = MemberLoadType.UDL,
                    value = transverse,
                    position = 0.0,
                    loadCase = "DL"
                ))
            }
            // المركّبة المحورية w·sinθ·L على طول محور العضو — نصفان عقيديان
            // (الأفقي: صفر، الرأسي: نصف الوزن هابطاً عند كل طرف — كما كان).
            val axialHalfLocalX = -wPerM * sinT * L / 2.0
            if (abs(axialHalfLocalX) > 1e-9) {
                nodalLoads.add(NodalLoad(
                    m.nodeI, fx = axialHalfLocalX * cosT, fy = axialHalfLocalX * sinT, mz = 0.0, loadCase = "DL"
                ))
                nodalLoads.add(NodalLoad(
                    m.nodeJ, fx = axialHalfLocalX * cosT, fy = axialHalfLocalX * sinT, mz = 0.0, loadCase = "DL"
                ))
            }
        }
        return udlLoads to nodalLoads
    }

    /**
     * C9: مغلف القوى الداخلية عبر جميع توافيق الأحمال.
     * يحل النموذج لكل توفيق (DL/LL/WL × معامل) ويحدد لكل عضو أقصى/أدنى
     * (M, V, N) من قيم المخططات على طول العضو — لا من عزوم النهايات فقط،
     * لأن كمرية بسيطة عزوم نهاياتها صفر وعزم منتصفها هو الحاكم.
     * يعيد null إذا لم تكن توافيق مُعرّفة أو لم يوجد ما يُحسب.
     */
    private fun computeLoadEnvelopes(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        startTimeNs: Long
    ): List<MemberForceEnvelope>? {
        val combos = settings.loadCombinations
        if (combos.isEmpty()) return null

        val perMember = HashMap<Int, MutableList<DiagramExtremes>>()
        for (combo in combos) {
            // صفّ الأحمال حسب التوفيق
            val scaledNodal = nodalLoads.map { nl ->
                val f = combinationFactor(nl.loadCase, combo)
                nl.copy(fx = nl.fx * f, fy = nl.fy * f, mz = nl.mz * f)
            }.filter { it.fx != 0.0 || it.fy != 0.0 || it.mz != 0.0 }

            val scaledMember = memberLoads.map { ml ->
                val f = combinationFactor(ml.loadCase, combo)
                ml.copy(value = ml.value * f)
            }.filter { it.value != 0.0 }

            if (scaledNodal.isEmpty() && scaledMember.isEmpty()) continue

            // حل الإطار لهذا التوفيق (بدون إعادة حساب المغلف — توافيق فارغة)
            val comboSettings = settings.copy(loadCombinations = emptyList())
            val result = solveCore(nodes, members, scaledNodal, scaledMember, comboSettings, startTimeNs)
            if (!result.isSolved) continue

            for (diag in result.memberDiagrams) {
                if (diag.momentDiagram.isEmpty() || diag.shearDiagram.isEmpty() || diag.axialDiagram.isEmpty()) continue
                val maxM = diag.momentDiagram.maxOf { it.value }
                val minM = diag.momentDiagram.minOf { it.value }
                perMember.getOrPut(diag.memberId) { mutableListOf() }.add(
                    DiagramExtremes(
                        comboName = combo.name,
                        maxMoment = maxM,
                        minMoment = minM,
                        maxAbsMoment = maxOf(abs(maxM), abs(minM)),
                        maxShear = diag.shearDiagram.maxOf { it.value },
                        minShear = diag.shearDiagram.minOf { it.value },
                        maxAxial = diag.axialDiagram.maxOf { it.value },
                        minAxial = diag.axialDiagram.minOf { it.value }
                    )
                )
            }
        }

        if (perMember.isEmpty()) return emptyList()

        // بناء المغلف لكل عضو — التوفيق الحاكم = الأعلى |M|
        return members.map { m ->
            val list = perMember[m.id]
            if (list.isNullOrEmpty()) {
                MemberForceEnvelope(m.id, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "—")
            } else {
                MemberForceEnvelope(
                    memberId = m.id,
                    maxMoment = list.maxOf { it.maxMoment },
                    minMoment = list.minOf { it.minMoment },
                    maxShear = list.maxOf { it.maxShear },
                    minShear = list.minOf { it.minShear },
                    maxAxial = list.maxOf { it.maxAxial },
                    minAxial = list.minOf { it.minAxial },
                    governingCombination = list.maxByOrNull { it.maxAbsMoment }?.comboName ?: "—"
                )
            }
        }
    }

    private fun combinationFactor(loadCase: String, combo: LoadCombinationFrame): Double =
        when (loadCase.uppercase()) {
            "DL" -> combo.dlFactor
            "LL" -> combo.llFactor
            "WL" -> combo.wlFactor
            else -> combo.dlFactor
        }

    // ========================================================================
    // Stiffness Matrix Assembly
    // ========================================================================

    private fun assembleMemberStiffness(
        member: FrameMember,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings,
        Kg: Array<DoubleArray>
    ) {
        val ni = nodes.indexOfFirst { it.id == member.nodeI }
        val nj = nodes.indexOfFirst { it.id == member.nodeJ }
        if (ni < 0 || nj < 0) return

        val L = member.getLength(nodes)
        if (L < 1e-12) return
        val cosA = member.getCosTheta(nodes)
        val sinA = member.getSinTheta(nodes)
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings)

        val kl = effectiveMemberStiffness(member, memberLocalStiffness(E, A, I, L))
        val T = transformationMatrix(cosA, sinA)
        val Tt = transpose(T, 6, 6)
        val klT = multiply(Tt, kl, 6, 6, 6)
        val KgMember = multiply(klT, T, 6, 6, 6)

        // Assemble into global
        val dofs = intArrayOf(ni * 3, ni * 3 + 1, ni * 3 + 2, nj * 3, nj * 3 + 1, nj * 3 + 2)
        for (i in 0..5) {
            for (j in 0..5) {
                Kg[dofs[i]][dofs[j]] += KgMember[i][j]
            }
        }
    }

    /**
     * مصفوفة الجساءة المحلية للعضو (6x6)
     * DOFs: [u_i, v_i, θ_i, u_j, v_j, θ_j]
     */
    private fun memberLocalStiffness(E: Double, A: Double, I: Double, L: Double): Array<DoubleArray> {
        val k = Array(6) { DoubleArray(6) }
        val c = E * A / L
        val a = 12.0 * E * I / (L * L * L)
        val b = 6.0 * E * I / (L * L)
        val d = 4.0 * E * I / L
        val e = 2.0 * E * I / L

        k[0][0] = c;  k[0][3] = -c
        k[1][1] = a;  k[1][2] = b;   k[1][4] = -a;  k[1][5] = b
        k[2][1] = b;  k[2][2] = d;   k[2][4] = -b;  k[2][5] = e
        k[3][0] = -c; k[3][3] = c
        k[4][1] = -a; k[4][2] = -b;  k[4][4] = a;   k[4][5] = -b
        k[5][1] = b;  k[5][2] = e;   k[5][4] = -b;  k[5][5] = d
        return k
    }

    private fun releasedDofs(member: FrameMember): IntArray {
        val released = mutableListOf<Int>()
        if (member.releaseI) released.add(2)
        if (member.releaseJ) released.add(5)
        return released.toIntArray()
    }

    private fun effectiveMemberStiffness(
        member: FrameMember,
        k: Array<DoubleArray>
    ): Array<DoubleArray> {
        val released = releasedDofs(member)
        if (released.isEmpty()) return k
        return condenseMember(member, k, DoubleArray(6)).first
    }

    private fun condenseFixedEndForces(
        member: FrameMember,
        settings: FrameAnalysisSettings,
        nodes: List<FrameNode>,
        fef: DoubleArray
    ): DoubleArray {
        if (!member.releaseI && !member.releaseJ) return fef
        val L = member.getLength(nodes)
        if (L < 1e-12) return fef
        val k = memberLocalStiffness(
            getE(member, settings),
            getSectionProperties(member, settings).first,
            getSectionProperties(member, settings).second,
            L
        )
        return condenseMember(member, k, fef).second
    }

    /**
     * Static condensation for member-end moment releases.
     * Released rotational DOFs are eliminated from both the local stiffness
     * matrix and the fixed-end force vector so their end moment is exactly zero.
     */
    private fun condenseMember(
        member: FrameMember,
        k: Array<DoubleArray>,
        fef: DoubleArray
    ): Pair<Array<DoubleArray>, DoubleArray> {
        val released = releasedDofs(member)
        if (released.isEmpty()) return k to fef

        val retained = (0 until 6).filter { it !in released }
        val rc = retained.size
        val cc = released.size

        val kcc = Array(cc) { i -> DoubleArray(cc) { j -> k[released[i]][released[j]] } }
        val krc = Array(rc) { i -> DoubleArray(cc) { j -> k[retained[i]][released[j]] } }
        val kcr = Array(cc) { i -> DoubleArray(rc) { j -> k[released[i]][retained[j]] } }
        val krr = Array(rc) { i -> DoubleArray(rc) { j -> k[retained[i]][retained[j]] } }

        val invKcc = invertSmall(kcc) ?: return k to fef
        val krcInv = multiply(krc, invKcc, rc, cc, cc)
        val kStar = Array(rc) { i ->
            DoubleArray(rc) { j ->
                krr[i][j] - (0 until cc).sumOf { m -> krcInv[i][m] * kcr[m][j] }
            }
        }
        val fefRetained = DoubleArray(rc) { retained[it].let { dof -> fef[dof] } }
        val fefReleased = DoubleArray(cc) { released[it].let { dof -> fef[dof] } }
        val fefStar = DoubleArray(rc) { i ->
            fefRetained[i] - (0 until cc).sumOf { m -> krcInv[i][m] * fefReleased[m] }
        }

        val kEff = Array(6) { DoubleArray(6) }
        for (i in 0 until rc) {
            for (j in 0 until rc) kEff[retained[i]][retained[j]] = kStar[i][j]
        }
        val fefEff = DoubleArray(6)
        for (i in 0 until rc) fefEff[retained[i]] = fefStar[i]
        return kEff to fefEff
    }

    private fun invertSmall(a: Array<DoubleArray>): Array<DoubleArray>? {
        val n = a.size
        if (n == 0) return emptyArray()
        val inv = Array(n) { DoubleArray(n) }
        for (col in 0 until n) {
            val b = DoubleArray(n) { if (it == col) 1.0 else 0.0 }
            val x = solveLinearSystem(a, b) ?: return null
            for (row in 0 until n) inv[row][col] = x[row]
        }
        return inv
    }

    /**
     * مصفوفة التحويل من المحلي للعام (6x6)
     */
    private fun transformationMatrix(c: Double, s: Double): Array<DoubleArray> {
        return arrayOf(
            doubleArrayOf(c, s, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(-s, c, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, c, s, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, -s, c, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )
    }

    // ========================================================================
    // Fixed-End Forces
    // ========================================================================

    /**
     * قوى النهاية الثابتة للحمولات على الأعضاء — باتفاقية P_eq (القوى التي
     * يبذلها العضو المثبَّت طرفاه على العقد) = -q0 حيث q0 قوى المؤازرة على العضو.
     *
     * القيمة الموجبة للقوى تعني حمولة هابطة (اتجاه -y المحلي) وللعزوم CCW+.
     *
     * S3: فرع العزم المركز أُعيد اشتقاقه من شروط الإزاحة والاتزان وتحقَّق منه
     * بطريقتين مستقلتين (تكامل مباشر لمعادلة العزم + طريقة القوى). الحمولات
     * الطرفية تُثبَّت داخل العضو (clamp) فتنتقل طبيعياً للعقدة بدل إسقاطها.
     */
    private fun getFixedEndForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): DoubleArray {
        val L = member.getLength(nodes)
        val w = load.value

        val raw = when (load.loadType) {
            MemberLoadType.UDL -> {
                // q0 = [0, +wL/2, +wL²/12, 0, +wL/2, -wL²/12]  →  P_eq = -q0
                doubleArrayOf(0.0, -w * L / 2.0, -w * L * L / 12.0, 0.0, -w * L / 2.0, w * L * L / 12.0)
            }
            MemberLoadType.PointLoad -> {
                // S3: تثبيت الموقع داخل العضو حتى تنتقل الحمولة الطرفية للعقدة
                val a = load.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                val b = L - a
                val P = w
                doubleArrayOf(
                    0.0,
                    -P * b * b * (L + 2 * a) / (L * L * L),
                    -P * a * b * b / (L * L),
                    0.0,
                    -P * a * a * (L + 2 * b) / (L * L * L),
                    P * a * a * b / (L * L)
                )
            }
            MemberLoadType.Moment -> {
                // S3 (مُعاد اشتقاقه والتحقق بطريقتين مستقلتين): عزم M (CCW+) عند a من I
                //   q0 = [0, +6Mab/L³, +Mb(3a-L)/L², 0, -6Mab/L³, +Ma(2L-3a)/L²]
                //   P_eq = -q0
                // تحقق الحدود: a→L يعطي [0,0,0,0,0,+M] (عزم عُقدي عند J) ✓
                //              a→0 يعطي [0,0,+M,0,0,0] (عزم عُقدي عند I) ✓
                val a = load.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                val b = L - a
                val M = w
                doubleArrayOf(
                    0.0,
                    -6.0 * M * a * b / (L * L * L),
                    M * b * (L - 3.0 * a) / (L * L),
                    0.0,
                    6.0 * M * a * b / (L * L * L),
                    -M * a * (2.0 * L - 3.0 * a) / (L * L)
                )
            }
            MemberLoadType.LinearVarying -> {
                // حمولة مثلثية (0 عند I، w عند J، هابطة):
                //   q0 = [0, +3wL/20, +wL²/30, 0, +7wL/20, -wL²/20]  →  P_eq = -q0
                doubleArrayOf(
                    0.0,
                    -w * L * 3.0 / 20.0,
                    -w * L * L / 30.0,
                    0.0,
                    -w * L * 7.0 / 20.0,
                    w * L * L / 20.0
                )
            }
            MemberLoadType.Trapezoidal -> {
                // المرحلة الثالثة (P4-1): شبه منحرف متماثل — الشكل الكلاسيكي الدقيق
                // لتوزيع أحمال البلاطات ثنائية الاتجاه على كمرات الحواف.
                //   p(x) = w·min(1, min(x, L−x)/a)،  a = position ∈ (0, L/2]
                //   a = L/2 → مثلث متماثل (ذروة w عند منتصف البحر) — حمل الكمرات القصيرة
                //   a → 0  → UDL شدته w
                //
                // الاشتقاق (تحقق ثلاثي: مجموع ريمان لنواة الحمل المركزي المُتحقَّقة
                // + تكامل عددي + الحدود الكلاسيكية 5wL²/96 و wL²/12 —
                // scripts/verify_trapezoid.py):
                //   W    = w·(L−a)                       (الحمل الكلي)
                //   Mfix = w·(L²/12 − a²/6 + a³/(12L))   (عزم النهاية المثبتة)
                //   P_eq = [0, −W/2, −Mfix, 0, −W/2, +Mfix]  (متماثل: قصّان متساويان،
                //                                            عزمان متعاكسان)
                // الحدود: a→L/2 يعطي 5wL²/96 (المثلث المتماثل الكلاسيكي) ✓
                //         a→0 يعطي wL²/12 و wL/2 (UDL) ✓
                val a = load.position
                val total = w * (L - a)
                val mFix = w * (L * L / 12.0 - a * a / 6.0 + a * a * a / (12.0 * L))
                doubleArrayOf(
                    0.0,
                    -total / 2.0,
                    -mFix,
                    0.0,
                    -total / 2.0,
                    mFix
                )
            }
        }
        return condenseFixedEndForces(member, settings, nodes, raw)
    }

    // ========================================================================
    // Diagram Generation
    // ========================================================================

    /**
     * توليد مخططات القوى الداخلية (عزم، قص، محوري) لعضو واحد
     *
     * C7: عينات حرجة — مواقع الأحمال المركزة + a±ε + أرباع البحر + طرفا العضو
     *     (تفادي تفويت ذروة حمل مركز خارج شبكة العينات المنتظمة)
     * S2: M(x) = -m_i + v_i·x - (مساهمات الأحمال) — كانت +m_i فأعطت عزوم
     *     نهايات معكوسة الإشارة
     * C12: N(x) = -n_i (شد موجب — n_i سالب في الشد)
     */
    private fun generateDiagrams(
        member: FrameMember,
        nodes: List<FrameNode>,
        fLocalTotal: DoubleArray, // Total local end forces (q_end = k·u - FEF)
        memberLoadList: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): MemberDiagram {
        val L = member.getLength(nodes)

        // ── C7: بناء مجموعة مواقع العينات ──
        val sampleSet = TreeSet<Double>()
        sampleSet.add(0.0)
        sampleSet.add(L)
        // نقاط منتظمة (كثافة 21 نقطة)
        for (i in 1 until 21) sampleSet.add(L * i / 20.0)
        // مواقع الأحمال المركزة + جوارها (a±ε لالتقاط قفزة القص/العزم من الجهتين)
        for (mLoad in memberLoadList) {
            if (mLoad.loadType == MemberLoadType.PointLoad || mLoad.loadType == MemberLoadType.Moment) {
                val a = mLoad.position.coerceIn(0.0, L)
                sampleSet.add(a)
                sampleSet.add((a - LOAD_POS_EPS * 10).coerceIn(0.0, L))   // a⁻ε
                sampleSet.add((a + LOAD_POS_EPS * 10).coerceIn(0.0, L))   // a⁺ε
            }
            if (mLoad.loadType == MemberLoadType.Trapezoidal) {
                // المرحلة الثالثة: نقطتا انكسار توزّع شبه المنحرف (بداية/نهاية الهضبة)
                val aT = mLoad.position.coerceIn(LOAD_POS_EPS, L / 2.0)
                sampleSet.add(aT)
                sampleSet.add(L - aT)
            }
        }
        // نقاط ربع البحر
        sampleSet.add(L / 4.0)
        sampleSet.add(L / 2.0)
        sampleSet.add(3.0 * L / 4.0)
        val samples = sampleSet.toList()

        val momentPoints = ArrayList<DiagramPoint>(samples.size)
        val shearPoints = ArrayList<DiagramPoint>(samples.size)
        val axialPoints = ArrayList<DiagramPoint>(samples.size)

        // End forces in local system
        val vI = fLocalTotal[1]  // shear at I (local y)
        val mI = fLocalTotal[2]  // moment at I
        val nI = fLocalTotal[0]  // axial at I

        for (x in samples) {
            // C12: N(x) = -n_i (شد موجب)
            val N = -nI

            // S2: الإشارة الصحيحة لعزم النهاية — M(0) = -m_i
            var V = vI
            var M = -mI + vI * x

            // Add member load contributions (نفس اتفاقية P_eq: قوى موجبة = هابطة)
            for (mLoad in memberLoadList) {
                when (mLoad.loadType) {
                    MemberLoadType.UDL -> {
                        V -= mLoad.value * x
                        M -= mLoad.value * x * x / 2.0
                    }
                    MemberLoadType.PointLoad -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                        if (x >= a) {
                            V -= mLoad.value
                            M -= mLoad.value * (x - a)
                        }
                    }
                    MemberLoadType.Moment -> {
                        val a = mLoad.position.coerceIn(LOAD_POS_EPS, L - LOAD_POS_EPS)
                        if (x >= a) {
                            M -= mLoad.value
                        }
                    }
                    MemberLoadType.LinearVarying -> {
                        // Triangular load: 0 at I, w at J
                        val wAtX = mLoad.value * x / L
                        V -= wAtX * x / 2.0
                        M -= mLoad.value * x * x * x / (6.0 * L)
                    }
                    MemberLoadType.Trapezoidal -> {
                        // المرحلة الثالثة: مساهمات شبه المنحرف المتماثل
                        //   p(ξ) = w·min(1, min(ξ, L−ξ)/a)
                        // Vc(x) = الحمل التراكمي يسار المقطع، Mc(x) = ∫p(ξ)(x−ξ)dξ
                        // (صيغ ثلاثية الفروع متصلة عند x=a وx=L−a — مُتحقَّقة
                        //  بتكامل عددي، scripts/verify_trapezoid.py §2)
                        val wT = mLoad.value
                        val aT = mLoad.position
                        val totalT = wT * (L - aT)
                        if (x <= aT) {
                            V -= wT * x * x / (2.0 * aT)
                            M -= wT * x * x * x / (6.0 * aT)
                        } else if (x <= L - aT) {
                            V -= wT * (x - aT / 2.0)
                            M -= wT * (x * x / 2.0 - aT * x / 2.0 + aT * aT / 6.0)
                        } else {
                            val u = L - x
                            V -= totalT - wT * u * u / (2.0 * aT)
                            M -= wT * L * (L - aT) / 2.0 - wT * (L - aT) * u + wT * u * u * u / (6.0 * aT)
                        }
                    }
                }
            }

            momentPoints.add(DiagramPoint(x, M))
            shearPoints.add(DiagramPoint(x, V))
            axialPoints.add(DiagramPoint(x, N))
        }

        return MemberDiagram(
            memberId = member.id,
            momentDiagram = momentPoints,
            shearDiagram = shearPoints,
            axialDiagram = axialPoints,
            maxMoment = momentPoints.maxOfOrNull { abs(it.value) } ?: 0.0,
            maxShear = shearPoints.maxOfOrNull { abs(it.value) } ?: 0.0,
            maxAxial = axialPoints.maxOfOrNull { abs(it.value) } ?: 0.0
        )
    }

    /** واجهة عامة لعرض FEF في لوحة المعادلات (P_eq المحلي) */
    fun memberLoadEquivalentForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): DoubleArray = getFixedEndForces(member, load, nodes, settings)

    // ========================================================================
    // Section Properties Helpers (public for UI equations panel)
    // ========================================================================

    /**
     * خصائص جساءة عضو لعرضها في لوحة المعادلات (أو null إن كانت غير صالحة) — C11.
     */
    fun getMemberStiffnessProps(
        member: FrameMember,
        nodes: List<FrameNode>,
        settings: FrameAnalysisSettings
    ): MemberStiffnessProps? {
        val L = member.getLength(nodes)
        if (!L.isFinite() || L < MIN_MEMBER_LENGTH_M) return null
        val E = getE(member, settings)
        val (A, I) = getSectionProperties(member, settings)
        if (!E.isFinite() || E <= 0.0 || !A.isFinite() || A <= 0.0 || !I.isFinite() || I <= 0.0) return null
        val ig = when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection ?: return null
                val b = cs.width / 1000.0
                val h = cs.depth / 1000.0
                b * h * h * h / 12.0
            }
            FrameMaterialType.Steel -> I
        }
        val angleDeg = Math.toDegrees(atan2(member.getSinTheta(nodes), member.getCosTheta(nodes)))
        val label = when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection
                if (cs != null) "b×h = ${cs.width.toInt()}×${cs.depth.toInt()} mm" else "—"
            }
            FrameMaterialType.Steel -> member.steelSectionName?.takeIf { it.isNotBlank() } ?: "IPE 300"
        }
        return MemberStiffnessProps(
            memberId = member.id, E = E, A = A, I = I, Ig = ig, L = L,
            angleDeg = angleDeg, sectionLabel = label, materialType = member.materialType
        )
    }

    private fun getE(member: FrameMember, settings: FrameAnalysisSettings): Double {
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val fcu = member.concreteSection?.fcu ?: 25.0
                // E_c = 4700 * sqrt(fcu) MPa = 4700 * sqrt(fcu) * 1e3 kN/m²
                4700.0 * sqrt(fcu) * 1e3
            }
            // eSteel في الكيانات بالفعل kN/m² (200e6 = 200 GPa) —
            // الضرب في 1e3 (كما في 9d098f4) كان يجعل الحديد أجسأ 1000 مرة
            FrameMaterialType.Steel -> settings.eSteel
        }
    }

    private fun getSectionProperties(
        member: FrameMember,
        settings: FrameAnalysisSettings
    ): Pair<Double, Double> {
        // Returns (A in m², I in m⁴) with cracked inertia per ACI 318 §6.6 / ECP 203
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
                val b = cs.width / 1000.0  // mm -> m
                val h = cs.depth / 1000.0
                val Ig = b * h * h * h / 12.0
                // Cracked inertia: 0.35Ig for beams, 0.70Ig for columns (ACI 318-19 §6.6.3.1.1)
                val crackedFactor = when (member.memberType) {
                    FrameMemberType.Beam -> 0.35
                    FrameMemberType.Column -> 0.70
                    else -> 0.50
                }
                Pair(b * h, Ig * crackedFactor)
            }
            FrameMaterialType.Steel -> {
                // S4 FIX: fallback بالاسم — ipeSections[13] لم يكن IPE 300 فعلياً
                val sec = SteelTables.getSectionByName(member.steelSectionName ?: "IPE 300")
                    ?: SteelTables.getSectionByName("IPE 300")
                    ?: SteelTables.ipeSections.first()
                val A = sec.area * 1e-4      // cm² -> m²
                val I = sec.iy * 1e-8        // cm⁴ -> m⁴ (European IPE: Iy = strong axis)
                Pair(A, I)
            }
        }
    }

    // ========================================================================
    // Linear Algebra Utilities
    // ========================================================================

    /**
     * حل نظام معادلات خطية Ax = b بطريقة Gauss Elimination مع Partial Pivoting
     *
     * C2: سماحية محور نسبية (pivot < tol × scale ⇒ شاذ) بدل حد مطلق ثابت،
     * لأن مدخلات المصفوفة تتراوح بين ~1e3 و~1e8 kN/m² حسب المقاطع.
     */
    fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = A.size
        if (n == 0) return null

        // C2: مقياس المصفوفة للسماحية النسبية
        var scale = 0.0
        for (i in 0 until n) {
            for (j in 0 until n) {
                val v = abs(A[i][j])
                if (v > scale) scale = v
                if (!A[i][j].isFinite()) return null
            }
        }
        if (scale <= 0.0) return null
        val pivotTol = scale * PIVOT_REL_TOL

        // Augmented matrix
        val aug = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] } }

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxVal = abs(aug[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            if (maxVal < pivotTol) return null // Singular
            if (maxRow != col) {
                val tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp
            }
            // Eliminate
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col..n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = aug[i][n]
            for (j in i + 1 until n) sum -= aug[i][j] * x[j]
            x[i] = sum / aug[i][i]
        }
        if (x.any { it.isNaN() || it.isInfinite() }) return null
        return x
    }

    /**
     * C2: المتبقٍ النسبي ||A·x - b||∞ / ||b||∞ — يعيد null إذا كان b صفرياً
     */
    private fun relativeResidual(A: Array<DoubleArray>, x: DoubleArray, b: DoubleArray): Double? {
        val n = b.size
        var bNorm = 0.0
        for (v in b) if (abs(v) > bNorm) bNorm = abs(v)
        if (bNorm < 1e-12) return null // لا أحمال — لا معنى للفحص
        var maxRes = 0.0
        for (i in 0 until n) {
            var ax = 0.0
            for (j in 0 until n) ax += A[i][j] * x[j]
            val res = abs(ax - b[i])
            if (res > maxRes) maxRes = res
        }
        return maxRes / bNorm
    }

    private fun transpose(m: Array<DoubleArray>, rows: Int, cols: Int): Array<DoubleArray> {
        return Array(cols) { i -> DoubleArray(rows) { j -> m[j][i] } }
    }

    private fun multiply(A: Array<DoubleArray>, B: Array<DoubleArray>, m: Int, n: Int, p: Int): Array<DoubleArray> {
        val C = Array(m) { DoubleArray(p) }
        for (i in 0 until m) {
            for (j in 0 until p) {
                var sum = 0.0
                for (k in 0 until n) sum += A[i][k] * B[k][j]
                C[i][j] = sum
            }
        }
        return C
    }

    private fun globalToLocalDisplacements(
        uI: DoubleArray, uJ: DoubleArray, c: Double, s: Double
    ): DoubleArray {
        return doubleArrayOf(
            c * uI[0] + s * uI[1],
            -s * uI[0] + c * uI[1],
            uI[2],
            c * uJ[0] + s * uJ[1],
            -s * uJ[0] + c * uJ[1],
            uJ[2]
        )
    }

    // ========================================================================
    // Preset Frame Templates
    // ========================================================================

    /**
     * قالب إطار بسيط (عمودين + كمر واحد)
     */
    fun createSimplePortalFrame(
        span: Double = 6.0,
        height: Double = 4.0,
        udl: Double = 20.0
    ): Triple<List<FrameNode>, List<FrameMember>, List<MemberLoad>> {
        // ── InputGuard (ADR-010) ──
        InputGuard.positive("span", span)
        InputGuard.positive("height", height)
        InputGuard.nonNegative("udl", udl)
        val nodes = listOf(
            FrameNode(1, 0.0, 0.0, SupportType.Fixed),
            FrameNode(2, span, 0.0, SupportType.Fixed),
            FrameNode(3, span, height, SupportType.Free),
            FrameNode(4, 0.0, height, SupportType.Free)
        )
        val concreteSec = ConcreteSectionProps(300.0, 600.0)
        val members = listOf(
            FrameMember(1, 1, 4, FrameMaterialType.Concrete, FrameMemberType.Column, concreteSec, name = "عمود أيسر"),
            FrameMember(2, 2, 3, FrameMaterialType.Concrete, FrameMemberType.Column, concreteSec, name = "عمود أيمن"),
            FrameMember(3, 4, 3, FrameMaterialType.Concrete, FrameMemberType.Beam, ConcreteSectionProps(250.0, 500.0), name = "كمر سقف")
        )
        val loads = listOf(
            MemberLoad(3, MemberLoadType.UDL, udl, loadCase = "DL")
        )
        return Triple(nodes, members, loads)
    }

    /**
     * قالب إطار متعدد الطوابق
     */
    fun createMultiStoryFrame(
        spans: List<Double> = listOf(5.0, 5.0),
        storyHeights: List<Double> = listOf(3.5, 3.0),
        udlPerFloor: Double = 15.0
    ): Triple<List<FrameNode>, List<FrameMember>, List<MemberLoad>> {
        // ── InputGuard (ADR-010) ──
        InputGuard.notEmpty("spans", spans)
        InputGuard.notEmpty("storyHeights", storyHeights)
        InputGuard.nonNegative("udlPerFloor", udlPerFloor)
        val numSpans = spans.size
        val numStories = storyHeights.size
        val nodes = mutableListOf<FrameNode>()
        var nodeId = 1

        // Ground nodes
        for (i in 0..numSpans) {
            val x = spans.take(i).sum()
            val node = FrameNode(nodeId++, x, 0.0, SupportType.Fixed)
            nodes.add(node)
        }

        // Upper floor nodes
        for (s in 0 until numStories) {
            val y = storyHeights.take(s + 1).sum()
            for (i in 0..numSpans) {
                val x = spans.take(i).sum()
                nodes.add(FrameNode(nodeId++, x, y, SupportType.Free))
            }
        }

        // Members
        val members = mutableListOf<FrameMember>()
        var memberId = 1
        val colSec = ConcreteSectionProps(300.0, 600.0)
        val beamSec = ConcreteSectionProps(250.0, 500.0)

        // Columns
        for (s in 0 until numStories) {
            for (i in 0..numSpans) {
                val lowerNode = s * (numSpans + 1) + i + 1
                val upperNode = (s + 1) * (numSpans + 1) + i + 1
                members.add(FrameMember(
                    memberId++, lowerNode, upperNode,
                    FrameMaterialType.Concrete, FrameMemberType.Column, colSec,
                    name = "عمود $i طابق ${s + 1}"
                ))
            }
        }

        // Beams
        for (s in 0 until numStories) {
            for (i in 0 until numSpans) {
                val leftNode = (s + 1) * (numSpans + 1) + i + 1
                val rightNode = leftNode + 1
                members.add(FrameMember(
                    memberId++, leftNode, rightNode,
                    FrameMaterialType.Concrete, FrameMemberType.Beam, beamSec,
                    name = "كمر $i طابق ${s + 1}"
                ))
            }
        }

        // Loads (UDL on beams)
        val loads = mutableListOf<MemberLoad>()
        // Beams are added after columns: columns first, then beams
        // Number of columns = (numSpans + 1) * numStories
        // Beams start at index = (numSpans + 1) * numStories
        val numColumns = (numSpans + 1) * numStories
        for (s in 0 until numStories) {
            for (i in 0 until numSpans) {
                val beamIdx = numColumns + s * numSpans + i
                if (beamIdx < members.size) {
                    loads.add(MemberLoad(members[beamIdx].id, MemberLoadType.UDL, udlPerFloor, loadCase = "DL"))
                }
            }
        }

        return Triple(nodes, members, loads)
    }
}
