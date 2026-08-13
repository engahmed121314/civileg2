// ============================================================================
// 2D FRAME ANALYZER — Direct Stiffness Method
// ============================================================================

import {
  FrameNode, FrameElement, FrameLoad, AnalysisResults,
  MemberEndForces, NodeDisplacement, InternalForcesAtPoint,
  SupportType
} from './types';
import { solveLinearSystem, matMul, matVecMul, matTranspose } from './matrix';

// Default material properties
const E_CONCRETE = 25000;  // MPa (25 GPa)
const E_STEEL = 200000;   // MPa (200 GPa)

interface NodeDOF {
  nodeId: number;
  dofIndex: number; // starting index in global system (0, 3, 6, ...)
  uxDOF: number;
  uyDOF: number;
  rDOF: number;
  fixedDOFs: number[]; // DOFs fixed by support
}

/**
 * Main analysis function — solves the 2D frame and returns all internal forces.
 */
export function analyzeFrame(
  nodes: FrameNode[],
  elements: FrameElement[],
  loads: FrameLoad[]
): AnalysisResults {
  const n = nodes.length;
  if (n < 2) return { displacements: [], memberForces: [], internalForces: new Map(), isStable: false, message: 'Need at least 2 nodes.' };
  if (elements.length < 1) return { displacements: [], memberForces: [], internalForces: new Map(), isStable: false, message: 'Need at least 1 element.' };

  const totalDOF = n * 3;

  // 1. Build node DOF map
  const nodeDOFs = buildNodeDOFMap(nodes);

  // 2. Check stability — need at least 3 non-collinear restrained DOFs
  const fixedCount = nodeDOFs.reduce((s, nd) => s + nd.fixedDOFs.length, 0);
  if (fixedCount < 3) {
    return { displacements: [], memberForces: [], internalForces: new Map(), isStable: false, message: 'Structure is unstable. Need more supports (min 3 restrained DOFs).' };
  }

  // 3. Assemble global stiffness matrix
  const K = assembleGlobalStiffness(nodes, elements, nodeDOFs, totalDOF);

  // 4. Assemble global force vector
  const F = assembleForceVector(totalDOF, nodes, elements, loads, nodeDOFs);

  // 5. Apply boundary conditions and solve
  const freeDOFs = getFreeDOFs(nodeDOFs, totalDOF);
  if (freeDOFs.length === 0) {
    return { displacements: [], memberForces: [], internalForces: new Map(), isStable: false, message: 'All DOFs are fixed — no solution needed.' };
  }

  try {
    const reducedK = extractSubMatrix(K, freeDOFs);
    const reducedF = freeDOFs.map(d => F[d]);
    const reducedU = solveLinearSystem(reducedK, reducedF);

    // 6. Build full displacement vector
    const U = new Array(totalDOF).fill(0);
    for (let i = 0; i < freeDOFs.length; i++) {
      U[freeDOFs[i]] = reducedU[i];
    }

    // 7. Calculate member end forces
    const memberForces = calculateMemberEndForces(nodes, elements, nodeDOFs, U);

    // 8. Calculate internal forces along each member
    const internalForces = calculateInternalForces(nodes, elements, loads, memberForces);

    // 9. Build displacement results
    const displacements: NodeDisplacement[] = nodes.map(node => {
      const dof = nodeDOFs.get(node.id)!;
      return {
        nodeId: node.id,
        ux: U[dof.uxDOF],
        uy: U[dof.uyDOF],
        rotation: U[dof.rDOF],
      };
    });

    return { displacements, memberForces, internalForces, isStable: true, message: 'Analysis completed successfully.' };
  } catch (e: any) {
    return { displacements: [], memberForces: [], internalForces: new Map(), isStable: false, message: `Analysis error: ${e.message}` };
  }
}

// ============================================================================
// INTERNAL HELPERS
// ============================================================================

function buildNodeDOFMap(nodes: FrameNode[]): Map<number, NodeDOF> {
  const map = new Map<number, NodeDOF>();
  nodes.forEach((node, idx) => {
    const base = idx * 3;
    const fixedDOFs: number[] = [];

    if (node.support === 'fixed') {
      fixedDOFs.push(base, base + 1, base + 2); // ux, uy, rotation
    } else if (node.support === 'hinged') {
      fixedDOFs.push(base, base + 1); // ux, uy only (rotation free)
    } else if (node.support === 'roller') {
      // Roller allows horizontal movement, fixes vertical
      fixedDOFs.push(base + 1); // uy only
    }

    map.set(node.id, {
      nodeId: node.id,
      dofIndex: base,
      uxDOF: base,
      uyDOF: base + 1,
      rDOF: base + 2,
      fixedDOFs,
    });
  });
  return map;
}

function getElementProps(elem: FrameElement): { E: number; A: number; I: number } {
  if (elem.material === 'concrete') {
    const w = elem.section.width || 300;
    const h = elem.section.depth || 500;
    return {
      E: elem.section.elasticMod || E_CONCRETE,
      A: w * h,                      // mm²
      I: (w * Math.pow(h, 3)) / 12, // mm⁴
    };
  } else {
    return {
      E: elem.section.elasticMod || E_STEEL,
      A: elem.section.area || 5000,    // mm²
      I: elem.section.inertia || 1e8,  // mm⁴
    };
  }
}

/**
 * 6×6 element stiffness matrix in LOCAL coordinates.
 * DOF order: [u1, v1, θ1, u2, v2, θ2]
 * Units: lengths in mm, forces in kN, E in MPa
 */
function localStiffnessMatrix(E: number, A: number, I: number, L: number): number[][] {
  const Lmm = L;
  const k: number[][] = Array.from({ length: 6 }, () => new Array(6).fill(0));

  const EA_L = (E * A) / Lmm;
  const EI_L3 = (E * I) / (Lmm * Lmm * Lmm);
  const EI_L2 = (E * I) / (Lmm * Lmm);

  // Axial terms
  k[0][0] = EA_L;   k[0][3] = -EA_L;
  k[3][0] = -EA_L;  k[3][3] = EA_L;

  // Shear/bending terms
  k[1][1] = 12 * EI_L3;
  k[1][2] = 6 * EI_L2;
  k[1][4] = -12 * EI_L3;
  k[1][5] = 6 * EI_L2;

  k[2][1] = 6 * EI_L2;
  k[2][2] = 4 * (E * I) / Lmm;
  k[2][4] = -6 * EI_L2;
  k[2][5] = 2 * (E * I) / Lmm;

  k[4][1] = -12 * EI_L3;
  k[4][2] = -6 * EI_L2;
  k[4][4] = 12 * EI_L3;
  k[4][5] = -6 * EI_L2;

  k[5][1] = 6 * EI_L2;
  k[5][2] = 2 * (E * I) / Lmm;
  k[5][4] = -6 * EI_L2;
  k[5][5] = 4 * (E * I) / Lmm;

  return k;
}

/**
 * 6×6 transformation matrix from local to global coordinates.
 */
function transformationMatrix(c: number, s: number): number[][] {
  return [
    [c,  s,  0, 0, 0, 0],
    [-s, c,  0, 0, 0, 0],
    [0,  0,  1, 0, 0, 0],
    [0,  0,  0, c,  s,  0],
    [0,  0,  0, -s, c,  0],
    [0,  0,  0, 0,  0,  1],
  ];
}

function assembleGlobalStiffness(
  nodes: FrameNode[],
  elements: FrameElement[],
  nodeDOFs: Map<number, NodeDOF>,
  totalDOF: number
): number[][] {
  const K: number[][] = Array.from({ length: totalDOF }, () => new Array(totalDOF).fill(0));

  const nodeMap = new Map(nodes.map(n => [n.id, n]));

  for (const elem of elements) {
    const nI = nodeMap.get(elem.nodeI)!;
    const nJ = nodeMap.get(elem.nodeJ)!;

    const dx = nJ.x - nI.x;
    const dy = nJ.y - nI.y;
    const L = Math.sqrt(dx * dx + dy * dy);
    if (L < 1e-6) continue;

    const c = dx / L;
    const s = dy / L;

    const { E, A, I } = getElementProps(elem);
    const kLocal = localStiffnessMatrix(E, A, I, L);
    const T = transformationMatrix(c, s);
    const TT = matTranspose(T);

    // k_global = T^T * k_local * T
    const kGlobal = matMul(matMul(TT, kLocal), T);

    // DOF mapping
    const dofI = nodeDOFs.get(elem.nodeI)!;
    const dofJ = nodeDOFs.get(elem.nodeJ)!;
    const elemDOFs = [
      dofI.uxDOF, dofI.uyDOF, dofI.rDOF,
      dofJ.uxDOF, dofJ.uyDOF, dofJ.rDOF,
    ];

    // Assemble into global K
    for (let i = 0; i < 6; i++) {
      for (let j = 0; j < 6; j++) {
        K[elemDOFs[i]][elemDOFs[j]] += kGlobal[i][j];
      }
    }
  }

  return K;
}

function assembleForceVector(
  totalDOF: number,
  nodes: FrameNode[],
  elements: FrameElement[],
  loads: FrameLoad[],
  nodeDOFs: Map<number, NodeDOF>
): number[] {
  const F = new Array(totalDOF).fill(0);
  const nodeMap = new Map(nodes.map(n => [n.id, n]));

  for (const load of loads) {
    const elem = elements.find(e => e.id === load.elementId);
    if (!elem) continue;

    const nI = nodeMap.get(elem.nodeI)!;
    const nJ = nodeMap.get(elem.nodeJ)!;
    const dx = nJ.x - nI.x;
    const dy = nJ.y - nI.y;
    const L = Math.sqrt(dx * dx + dy * dy);
    if (L < 1e-6) continue;

    const c = dx / L;
    const s = dy / L;
    const { E, A, I } = getElementProps(elem);

    // Fixed-end forces in local coordinates (sign convention: +N tension, +V up, +M CCW)
    let fLocal = [0, 0, 0, 0, 0, 0]; // [N1, V1, M1, N2, V2, M2]

    if (load.type === 'udl') {
      const w = -load.magnitude; // positive magnitude = downward, so negate for local convention
      const startFrac = 0;
      const endFrac = load.positionEnd ?? 1.0;
      const Le = L * (endFrac - startFrac);
      const a = L * startFrac;
      const b = a + Le;

      // UDL fixed-end forces
      const wa = w * Le;
      const wa2 = wa * Le;
      const wa3 = wa2 * Le;
      const L2 = L * L;
      const L3 = L2 * L;

      const V1 = wa * (b * b * (2 * a + b)) / (2 * L3);
      const V2 = wa * (a * a * (a + 2 * b)) / (2 * L3);
      const M1 = wa * b * b * (4 * a * L - b * b - 2 * L * L) / (12 * L2 * L);
      const M2 = wa * a * a * (4 * b * L - a * a - 2 * L * L) / (12 * L2 * L);

      fLocal = [0, V1, M1, 0, V2, M2];
    } else if (load.type === 'point') {
      const P = -load.magnitude; // positive = downward
      const aFrac = load.position ?? 0.5;
      const a = L * Math.max(0, Math.min(1, aFrac));
      const b = L - a;
      const L2 = L * L;
      const L3 = L2 * L;

      const V1 = P * b * b * (3 * a + b) / L3;
      const V2 = P * a * a * (a + 3 * b) / L3;
      const M1 = P * a * b * b / L2;
      const M2 = -P * a * a * b / L2;

      fLocal = [0, V1, M1, 0, V2, M2];
    } else if (load.type === 'moment') {
      const M = load.magnitude;
      const aFrac = load.position ?? 0.5;
      const a = L * Math.max(0, Math.min(1, aFrac));
      const b = L - a;
      const L2 = L * L;

      const V1 = -6 * M * a * b / (L2 * L);
      const V2 = 6 * M * a * b / (L2 * L);
      const M1 = M * b * (2 * a - b) / L2;
      const M2 = M * a * (2 * b - a) / L2;

      fLocal = [0, V1, M1, 0, V2, M2];
    }

    // Handle load direction
    if (load.direction === 'globalX' || load.direction === 'globalY' || load.direction === 'perpendicular') {
      // fLocal is in local coords; need to check if we should use it directly
      // For perpendicular-to-member loads, the local formulation is correct
      // For global X/Y loads, we need to resolve into local
      if (load.direction === 'globalY') {
        // fLocal already assumes downward (local Y direction when member is horizontal)
        // For non-horizontal members, we need to transform
        // The fixed-end forces are already in local coords, so we transform to global
      } else if (load.direction === 'globalX') {
        // Horizontal global force: resolve into local
        const P = load.magnitude;
        fLocal = [P * c + P * s, -P * s + P * c, 0, P * c + P * s, -P * s + P * c, 0];
        // Simplification: for axial loads, just apply as N
        fLocal = [P, 0, 0, 0, 0, 0]; // pure axial at node I end
      }
    } else if (load.direction === 'localX') {
      fLocal = [load.magnitude, 0, 0, 0, 0, 0]; // pure axial
    } else if (load.direction === 'localY') {
      // Already handled above (perpendicular to member)
    }

    // Transform equivalent nodal forces from local to global
    const T = transformationMatrix(c, s);
    const fGlobal = matVecMul(T, fLocal);

    // Add to global force vector (negative because these are reactions)
    const dofI = nodeDOFs.get(elem.nodeI)!;
    const dofJ = nodeDOFs.get(elem.nodeJ)!;
    const elemDOFs = [dofI.uxDOF, dofI.uyDOF, dofI.rDOF, dofJ.uxDOF, dofJ.uyDOF, dofJ.rDOF];

    for (let i = 0; i < 6; i++) {
      F[elemDOFs[i]] -= fGlobal[i]; // negative: equivalent nodal loads
    }
  }

  return F;
}

function calculateMemberEndForces(
  nodes: FrameNode[],
  elements: FrameElement[],
  nodeDOFs: Map<number, NodeDOF>,
  U: number[]
): MemberEndForces[] {
  const nodeMap = new Map(nodes.map(n => [n.id, n]));
  const results: MemberEndForces[] = [];

  for (const elem of elements) {
    const nI = nodeMap.get(elem.nodeI)!;
    const nJ = nodeMap.get(elem.nodeJ)!;
    const dx = nJ.x - nI.x;
    const dy = nJ.y - nI.y;
    const L = Math.sqrt(dx * dx + dy * dy);
    if (L < 1e-6) continue;

    const c = dx / L;
    const s = dy / L;
    const { E, A, I } = getElementProps(elem);

    const kLocal = localStiffnessMatrix(E, A, I, L);
    const T = transformationMatrix(c, s);

    // Extract global displacements for this element
    const dofI = nodeDOFs.get(elem.nodeI)!;
    const dofJ = nodeDOFs.get(elem.nodeJ)!;
    const uGlobal = [
      U[dofI.uxDOF], U[dofI.uyDOF], U[dofI.rDOF],
      U[dofJ.uxDOF], U[dofJ.uyDOF], U[dofJ.rDOF],
    ];

    // Transform to local
    const uLocal = matVecMul(T, uGlobal);

    // Local end forces (without loads — loads handled separately in internal forces)
    const fLocal = matVecMul(kLocal, uLocal);

    // Note: these are the "corrective" forces. Actual member forces include
    // the fixed-end forces. We'll compute actual internal forces in calculateInternalForces.

    results.push({
      elementId: elem.id,
      Ni: fLocal[0], Vi: fLocal[1], Mi: fLocal[2],
      Nj: fLocal[3], Vj: fLocal[4], Mj: fLocal[5],
    });
  }

  return results;
}

function calculateInternalForces(
  nodes: FrameNode[],
  elements: FrameElement[],
  loads: FrameLoad[],
  memberEndForces: MemberEndForces[]
): Map<number, InternalForcesAtPoint[]> {
  const result = new Map<number, InternalForcesAtPoint[]>();
  const nodeMap = new Map(nodes.map(n => [n.id, n]));

  for (let ei = 0; ei < elements.length; ei++) {
    const elem = elements[ei];
    const mef = memberEndForces[ei];

    const nI = nodeMap.get(elem.nodeI)!;
    const nJ = nodeMap.get(elem.nodeJ)!;
    const dx = nJ.x - nI.x;
    const dy = nJ.y - nI.y;
    const L = Math.sqrt(dx * dx + dy * dy);
    if (L < 1e-6) continue;

    // Start with member end forces (these are the "actual" forces including corrective)
    // Ni = axial at I, Vi = shear at I, Mi = moment at I
    let N0 = mef.Ni;
    let V0 = mef.Vi;
    let M0 = mef.Mi;

    // Add fixed-end forces from loads on this element
    const elemLoads = loads.filter(l => l.elementId === elem.id);
    let fef_N0 = 0, fef_V0 = 0, fef_M0 = 0;

    for (const load of elemLoads) {
      if (load.type === 'udl') {
        const w = -load.magnitude;
        const endFrac = load.positionEnd ?? 1.0;
        const Le = L * (endFrac);
        const a = 0;
        const b = a + Le;
        const L2 = L * L;
        const L3 = L2 * L;
        const wa = w * Le;
        const wa2 = wa * Le;
        const wa3 = wa2 * Le;

        fef_V0 += wa * (b * b * (2 * a + b)) / (2 * L3);
        fef_M0 += wa * b * b * (4 * a * L - b * b - 2 * L * L) / (12 * L2 * L);
      } else if (load.type === 'point') {
        const P = -load.magnitude;
        const aFrac = load.position ?? 0.5;
        const a = L * Math.max(0, Math.min(1, aFrac));
        const b = L - a;
        const L2 = L * L;
        const L3 = L2 * L;

        fef_V0 += P * b * b * (3 * a + b) / L3;
        fef_M0 += P * a * b * b / L2;
      } else if (load.type === 'moment') {
        const M = load.magnitude;
        const aFrac = load.position ?? 0.5;
        const a = L * Math.max(0, Math.min(1, aFrac));
        const b = L - a;
        const L2 = L * L;

        fef_V0 += -6 * M * a * b / (L2 * L);
        fef_M0 += M * b * (2 * a - b) / L2;
      }
    }

    N0 += fef_N0;
    V0 += fef_V0;
    M0 += fef_M0;

    // Generate points along the member
    const numPoints = 50;
    const points: InternalForcesAtPoint[] = [];

    for (let i = 0; i <= numPoints; i++) {
      const x = (i / numPoints) * L;
      let N = N0;
      let V = V0;
      let M = M0 + V0 * x;

      // Add UDL contributions
      for (const load of elemLoads) {
        if (load.type === 'udl') {
          const w = -load.magnitude;
          const endFrac = load.positionEnd ?? 1.0;
          const Le = L * (endFrac);
          if (x <= Le) {
            V += w * x;
            M += w * x * x / 2;
          } else {
            V += w * Le;
            M += w * Le * Le / 2;
          }
        } else if (load.type === 'point') {
          const P = -load.magnitude;
          const aFrac = load.position ?? 0.5;
          const a = L * Math.max(0, Math.min(1, aFrac));
          if (x >= a) {
            V += P;
            M += P * (x - a);
          }
        } else if (load.type === 'moment') {
          const Mo = load.magnitude;
          const aFrac = load.position ?? 0.5;
          const a = L * Math.max(0, Math.min(1, aFrac));
          if (x >= a) {
            M += Mo;
          }
        }
      }

      points.push({ x, N, V, M });
    }

    result.set(elem.id, points);
  }

  return result;
}

function getFreeDOFs(nodeDOFs: Map<number, NodeDOF>, totalDOF: number): number[] {
  const fixed = new Set<number>();
  nodeDOFs.forEach(nd => nd.fixedDOFs.forEach(d => fixed.add(d)));
  return Array.from({ length: totalDOF }, (_, i) => i).filter(d => !fixed.has(d));
}

function extractSubMatrix(K: number[][], rows: number[]): number[][] {
  return rows.map(r => rows.map(c => K[r][c]));
}