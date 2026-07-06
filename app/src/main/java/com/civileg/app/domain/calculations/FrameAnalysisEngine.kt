package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * محرك تحليل الهياكل الإنشائية بطريقة مصفوفة الجساءة (Stiffness Matrix Method)
 * 
 * يدعم:
 * - إطارات ثنائية الأبعاد (2D Frames)
 * - عقد بحرية ومقيدة (حرة، مفصلية، بكرة، تثبيت كامل)
 * - أحمال عقدية (قوى + عزوم)
 * - أحمال على الأعضاء (منتظمة، مركزة، عزوم)
 * - حساب القوى الداخلية ورسم المخططات
 */
object FrameAnalysisEngine {

    private const val DOF_PER_NODE = 3  // dx, dy, rz

    // ========================================================================
    // Main Solver
    // ========================================================================

    /**
     * حل الإطار بالكامل
     * @return FrameAnalysisResult يحتوي على إزاحات، قوى داخلية، ومخططات
     */
    fun solveFrame(
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): FrameAnalysisResult {
        try {
            if (nodes.size < 2) return FrameAnalysisResult(errorMessage = "يجب إدخال عقدتين على الأقل")
            if (members.isEmpty()) return FrameAnalysisResult(errorMessage = "يجب إدخال عضو واحد على الأقل")

            val numNodes = nodes.size
            val totalDOF = numNodes * DOF_PER_NODE

            // 1. Build global stiffness matrix
            val Kg = Array(totalDOF) { DoubleArray(totalDOF) }
            for (member in members) {
                assembleMemberStiffness(member, nodes, settings, Kg)
            }

            // 2. Build load vector (with member load equivalent nodal loads)
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
            val fixedEndForces = mutableListOf<Pair<Int, DoubleArray>>() // (memberIdx, FEF[6])
            for (mLoad in memberLoads) {
                val mIdx = members.indexOfFirst { it.id == mLoad.memberId }
                if (mIdx < 0) continue
                val member = members[mIdx]
                val fef = getFixedEndForces(member, mLoad, nodes)
                fixedEndForces.add(Pair(mIdx, fef))
                // Subtract FEF from global load vector (negative because we move to RHS)
                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni >= 0 && nj >= 0) {
                    F[ni * 3] -= fef[0]; F[ni * 3 + 1] -= fef[1]; F[ni * 3 + 2] -= fef[2]
                    F[nj * 3] -= fef[3]; F[nj * 3 + 1] -= fef[4]; F[nj * 3 + 2] -= fef[5]
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
            if (freeDOFs.isEmpty()) return FrameAnalysisResult(errorMessage = "لا توجد درجات حرية - الإطار مقيّد بالكامل")

            val Kff = Array(freeDOFs.size) { i -> DoubleArray(freeDOFs.size) { j -> Kg[freeDOFs[i]][freeDOFs[j]] } }
            val Ff = DoubleArray(freeDOFs.size) { F[freeDOFs[it]] }

            val Uf = solveLinearSystem(Kff, Ff) ?: return FrameAnalysisResult(errorMessage = "فشل حل نظام المعادلات - تحقق من الارتكازات")

            // 5. Full displacement vector
            val U = DoubleArray(totalDOF)
            for (i in freeDOFs.indices) U[freeDOFs[i]] = Uf[i]

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
            var fefIdx = 0

            for ((mIdx, member) in members.withIndex()) {
                val ni = nodes.indexOfFirst { it.id == member.nodeI }
                val nj = nodes.indexOfFirst { it.id == member.nodeJ }
                if (ni < 0 || nj < 0) continue

                val L = member.getLength(nodes)
                val cosA = member.getCosTheta(nodes)
                val sinA = member.getSinTheta(nodes)

                // Member stiffness properties
                val E = getE(member, settings)
                val (A, I) = getSectionProperties(member, settings, L)
                val k = memberLocalStiffness(E, A, I, L)

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

                // Add fixed-end forces for member loads on this member
                var fefThisMember = DoubleArray(6)
                val memberLoadList = memberLoads.filter { it.memberId == member.id }
                for (mLoad in memberLoadList) {
                    val fef = getFixedEndForces(member, mLoad, nodes)
                    for (i in 0..5) fefThisMember[i] += fef[i]
                }

                // Total local forces = k*u + FEF
                val fTotal = DoubleArray(6) { fLocal[it] + fefThisMember[it] }

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
                NodeResult(
                    nodeId = node.id,
                    dx = U[idx * 3],
                    dy = U[idx * 3 + 1],
                    rz = U[idx * 3 + 2],
                    reactionFx = if (0 in node.support.restrainedDOFs.map { it - (idx*3) }) reactions[idx * 3] else 0.0,
                    reactionFy = if (1 in node.support.restrainedDOFs.map { it - (idx*3) }) reactions[idx * 3 + 1] else 0.0,
                    reactionMz = if (2 in node.support.restrainedDOFs.map { it - (idx*3) }) reactions[idx * 3 + 2] else 0.0
                )
            }

            return FrameAnalysisResult(
                nodeResults = nodeResults,
                memberEndForces = memberForces,
                memberDiagrams = memberDiagrams,
                isSolved = true
            )
        } catch (e: Exception) {
            return FrameAnalysisResult(errorMessage = "خطأ في التحليل: ${e.message}")
        }
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
        val (A, I) = getSectionProperties(member, settings, L)

        val kl = memberLocalStiffness(E, A, I, L)
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
     * حساب قوى النهاية الثابتة (Fixed-End Forces) للحمولات على الأعضاء
     */
    private fun getFixedEndForces(
        member: FrameMember,
        load: MemberLoad,
        nodes: List<FrameNode>
    ): DoubleArray {
        val L = member.getLength(nodes)
        val w = load.value
        val a = load.position

        return when (load.loadType) {
            MemberLoadType.UDL -> {
                // FEF for UDL w (kN/m) over full span L
                // Local: [0, -wL/2, -wL²/12, 0, -wL/2, wL²/12]
                doubleArrayOf(0.0, -w * L / 2.0, -w * L * L / 12.0, 0.0, -w * L / 2.0, w * L * L / 12.0)
            }
            MemberLoadType.PointLoad -> {
                // FEF for point load P at distance 'a' from node I
                val b = L - a
                if (a < 1e-12 || b < 1e-12) {
                    doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                } else {
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
            }
            MemberLoadType.Moment -> {
                // FEF for moment M at distance 'a' from node I
                val b = L - a
                doubleArrayOf(
                    0.0,
                    6.0 * w * a * b / (L * L * L),
                    w * b * (2.0 * a - b) / (L * L),
                    0.0,
                    -6.0 * w * a * b / (L * L * L),
                    w * a * (2.0 * b - a) / (L * L)
                )
            }
            MemberLoadType.LinearVarying -> {
                // FEF for triangular load (0 at I, w at J)
                doubleArrayOf(
                    0.0,
                    -w * L * 7.0 / 20.0,
                    -w * L * L / 20.0,
                    0.0,
                    -w * L * 3.0 / 20.0,
                    w * L * L / 30.0
                )
            }
        }
    }

    // ========================================================================
    // Diagram Generation
    // ========================================================================

    /**
     * توليد مخططات القوى الداخلية (عزم، قص، محوري) لعضو واحد
     */
    private fun generateDiagrams(
        member: FrameMember,
        nodes: List<FrameNode>,
        fLocalTotal: DoubleArray, // Total local end forces (k*u + FEF)
        memberLoadList: List<MemberLoad>,
        settings: FrameAnalysisSettings
    ): MemberDiagram {
        val L = member.getLength(nodes)
        val numPoints = 21
        val momentPoints = mutableListOf<DiagramPoint>()
        val shearPoints = mutableListOf<DiagramPoint>()
        val axialPoints = mutableListOf<DiagramPoint>()

        // End forces in local system
        val vI = fLocalTotal[1]  // shear at I (local y)
        val mI = fLocalTotal[2]  // moment at I
        val nI = fLocalTotal[0]  // axial at I
        val vJ = fLocalTotal[4]  // shear at J
        val mJ = fLocalTotal[5]  // moment at J

        for (i in 0..numPoints) {
            val x = L * i / numPoints
            var V = 0.0
            var M = 0.0
            var N = nI // axial is constant along member (no distributed axial load)

            // Start with end force contributions
            V = vI
            M = mI + vI * x

            // Add member load contributions
            for (mLoad in memberLoadList) {
                when (mLoad.loadType) {
                    MemberLoadType.UDL -> {
                        V -= mLoad.value * x
                        M -= mLoad.value * x * x / 2.0
                    }
                    MemberLoadType.PointLoad -> {
                        val a = mLoad.position
                        if (x >= a) {
                            V -= mLoad.value
                            M -= mLoad.value * (x - a)
                        }
                    }
                    MemberLoadType.Moment -> {
                        val a = mLoad.position
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

    // ========================================================================
    // Section Properties Helper
    // ========================================================================

    private fun getE(member: FrameMember, settings: FrameAnalysisSettings): Double {
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val fcu = member.concreteSection?.fcu ?: 25.0
                // E_c = 4700 * sqrt(fcu) MPa = 4700 * sqrt(fcu) * 1e3 kN/m²
                4700.0 * sqrt(fcu) * 1e3
            }
            FrameMaterialType.Steel -> settings.eSteel
        }
    }

    private fun getSectionProperties(
        member: FrameMember,
        settings: FrameAnalysisSettings,
        L: Double
    ): Pair<Double, Double> {
        // Returns (A in m², I in m⁴)
        return when (member.materialType) {
            FrameMaterialType.Concrete -> {
                val cs = member.concreteSection ?: ConcreteSectionProps(250.0, 500.0)
                val b = cs.width / 1000.0  // mm -> m
                val h = cs.depth / 1000.0
                Pair(b * h, b * h * h * h / 12.0)
            }
            FrameMaterialType.Steel -> {
                // Default IPE 300 if no section specified
                val sec = com.civileg.app.utils.SteelTables.getSectionByName(member.steelSectionName ?: "IPE 300")
                    ?: com.civileg.app.utils.SteelTables.ipeSections[13] // IPE 300
                val A = sec.area * 1e-4      // cm² -> m²
                val I = sec.iy * 1e-8         // cm⁴ -> m⁴
                Pair(A, I)
            }
        }
    }

    // ========================================================================
    // Linear Algebra Utilities
    // ========================================================================

    /**
     * حل نظام معادلات خطية Ax = b بطريقة Gauss Elimination مع Pivoting
     */
    fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = A.size
        if (n == 0) return null

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
            if (maxVal < 1e-15) return null // Singular
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
        return x
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
    ): Triple<List<FrameNode>, List<FrameNode>, Triple<List<FrameNode>, List<FrameMember>, List<MemberLoad>>> {
        val numSpans = spans.size
        val numStories = storyHeights.size
        val nodes = mutableListOf<FrameNode>()
        var nodeId = 1

        // Ground nodes
        val groundNodes = mutableListOf<FrameNode>()
        for (i in 0..numSpans) {
            val x = spans.take(i).sum()
            val node = FrameNode(nodeId++, x, 0.0, SupportType.Fixed)
            nodes.add(node)
            groundNodes.add(node)
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
        for (s in 0 until numStories) {
            for (i in 0 until numSpans) {
                val beamIdx = numSpans * numStories + s * numSpans + i
                if (beamIdx < members.size) {
                    loads.add(MemberLoad(members[beamIdx].id, MemberLoadType.UDL, udlPerFloor, loadCase = "DL"))
                }
            }
        }

        return Triple(groundNodes, nodes.toTypedArray().toList(), Triple(nodes, members, loads))
    }
}