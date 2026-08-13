// ============================================================================
// STEEL SECTION DESIGN MODULE — AISC 360 / ECP 205
// ============================================================================

import { SteelDesignResult, MemberEndForces, InternalForcesAtPoint, FrameElement } from './types';

const FY_STEEL = 250; // MPa (S250)
const E_STEEL = 200000;

// Standard steel section library (simplified)
const STEEL_SECTIONS = [
  // IPE sections: [name, h(mm), b(mm), tw(mm), tf(mm), A(mm²), I(mm⁴), Zx(mm³)]
  ['IPE 80',    80,  46,  3.8, 5.2,  764,    8.49e4,  1.99e3],
  ['IPE 100',  100,  55,  4.1, 5.7,  1032,  17.1e4,  3.71e3],
  ['IPE 120',  120,  64,  4.4, 6.3,  1320,  31.7e4,  5.88e3],
  ['IPE 140',  140,  73,  4.7, 6.9,  1640,  54.7e4,  8.82e3],
  ['IPE 160',  160,  82,  5.0, 7.4,  2010,  86.9e4,  12.4e3],
  ['IPE 180',  180,  91,  5.3, 8.0,  2390,  1.317e6, 1.66e4],
  ['IPE 200',  200, 100,  5.6, 8.5,  2850,  1.943e6, 2.21e4],
  ['IPE 220',  220, 110,  5.9, 9.2,  3340,  2.772e6, 2.95e4],
  ['IPE 240',  240, 120,  6.2, 9.8,  3910,  3.892e6, 3.87e4],
  ['IPE 270',  270, 135,  6.6, 10.2, 4590,  5.79e6,  5.35e4],
  ['IPE 300',  300, 150,  7.1, 10.7,  5380,  8.36e6,  7.21e4],
  ['IPE 330',  330, 160,  7.5, 11.5,  6260,  1.177e7, 9.66e4],
  ['IPE 360',  360, 170,  8.0, 12.7,  7270,  1.627e7, 1.28e5],
  ['IPE 400',  400, 180,  8.6, 13.5,  8450,  2.313e7, 1.70e5],
  ['IPE 450',  450, 190,  9.4, 14.6,  9880,  3.371e7, 2.24e5],
  ['IPE 500',  500, 200, 10.2, 16.0,  11600, 4.820e7, 2.94e5],
  ['IPE 550',  550, 210, 11.1, 17.2,  13400, 6.720e7, 3.82e5],
  ['IPE 600',  600, 220, 12.0, 19.0,  15600, 9.210e7, 5.07e5],
  // HEA sections
  ['HEA 100',  96,  100, 5.0, 8.0,  2120,  3.49e5,  7.47e3],
  ['HEA 120',  114, 120, 5.0, 8.0,  2530,  6.06e5,  1.10e4],
  ['HEA 140',  133, 140, 5.5, 8.5,  3100,  1.03e6,  1.61e4],
  ['HEA 160',  152, 160, 6.0, 9.0,  3880,  1.67e6,  2.31e4],
  ['HEA 180',  171, 180, 6.0, 9.5,  4530,  2.53e6,  3.11e4],
  ['HEA 200',  190, 200, 6.5, 10.0,  5390,  3.69e6,  4.13e4],
  ['HEA 220',  210, 220, 7.0, 11.0,  6430,  5.41e6,  5.58e4],
  ['HEA 240',  230, 240, 7.5, 12.0,  7680,  7.76e6,  7.37e4],
  ['HEA 260',  250, 260, 7.5, 12.5,  8680,  1.05e7,  9.19e4],
  ['HEA 280',  270, 280, 8.0, 13.0,  9760,  1.37e7,  1.12e5],
  ['HEA 300',  290, 300, 8.5, 14.0,  11200, 1.83e7,  1.38e5],
  // HEB sections
  ['HEB 100',  100, 100, 6.0, 10.0,  2620,  4.49e5,  9.38e3],
  ['HEB 120',  120, 120, 6.5, 11.0,  3400,  8.64e5,  1.51e4],
  ['HEB 140',  140, 140, 7.0, 12.0,  4310,  1.51e6,  2.28e4],
  ['HEB 160',  160, 160, 8.0, 13.0,  5430,  2.49e6,  3.28e4],
  ['HEB 180',  180, 180, 8.5, 14.0,  6530,  3.83e6,  4.53e4],
  ['HEB 200',  200, 200, 9.0, 15.0,  7810,  5.70e6,  6.03e4],
  ['HEB 220',  220, 220, 9.5, 16.0,  9100,  8.09e6,  7.89e4],
  ['HEB 240',  240, 240, 10.0, 17.0,  10600,  1.12e7,  1.02e5],
  ['HEB 260',  260, 260, 10.0, 17.5,  11800,  1.49e7,  1.28e5],
  ['HEB 280',  280, 280, 10.5, 18.0,  13100,  1.93e7,  1.57e5],
  ['HEB 300',  300, 300, 11.0, 19.0,  14900,  2.56e7,  1.94e5],
  ['HEB 320',  320, 300, 11.5, 20.5,  16100,  3.09e7,  2.23e5],
  ['HEB 340',  340, 300, 12.0, 21.5,  17100,  3.66e7,  2.55e5],
  ['HEB 360',  360, 300, 12.5, 22.5,  18100,  4.32e7,  2.90e5],
  ['HEB 400',  400, 300, 13.5, 24.0,  19800,  5.77e7,  3.47e5],
  ['HEB 450',  450, 300, 14.0, 26.0,  21800,  7.99e7,  4.33e5],
  ['HEB 500',  500, 300, 14.5, 28.0,  23900,  1.07e8,  5.39e5],
  ['HEB 550',  550, 300, 15.0, 30.0,  26400,  1.37e8,  6.60e5],
  ['HEB 600',  600, 300, 15.5, 32.0,  28500,  1.72e8,  7.91e5],
] as const;

export function designSteelMembers(
  elements: FrameElement[],
  memberForces: MemberEndForces[],
  internalForces: Map<number, InternalForcesAtPoint[]>,
  fy?: number
): SteelDesignResult[] {
  const Fy = (fy || FY_STEEL);

  return elements.map((elem, idx) => {
    const mef = memberForces[idx];
    const forces = internalForces.get(elem.id) || [];

    let maxM = 0, maxV = 0, maxN = 0;
    for (const pt of forces) {
      maxM = Math.max(maxM, Math.abs(pt.M));
      maxV = Math.max(maxV, Math.abs(pt.V));
      maxN = Math.max(maxN, Math.abs(pt.N));
    }

    // Find optimal section
    let best: typeof STEEL_SECTIONS[number] | null = null;
    let bestUR = Infinity;

    for (const section of STEEL_SECTIONS) {
      const [name, h, b, tw, tf, A, I, Zx] = section;
      const Zxmm = Zx; // mm³
      const Imm = I;   // mm⁴

      // Axial capacity (no buckling simplification)
      const Pn = 0.9 * Fy * A / 1000; // kN
      // Flexural capacity
      const Mn = 0.9 * Fy * Zxmm / 1e6; // kN.m
      // Shear capacity
      const Vn = 0.6 * Fy * (h - 2 * tf) * tw / 1000; // kN (web area)

      // Combined interaction (simplified AISC)
      const ratioP = maxN / Math.max(Pn, 0.001);
      const ratioM = maxM / Math.max(Mn, 0.001);
      const ratioV = maxV / Math.max(Vn, 0.001);
      const UR = ratioP + ratioM + 0.5 * ratioV; // simplified interaction

      if (UR <= 1.0 && UR < bestUR) {
        bestUR = UR;
        best = section;
      }
    }

    // If no section passes, pick the largest
    if (!best) {
      best = STEEL_SECTIONS[STEEL_SECTIONS.length - 1];
      bestUR = (maxN / (0.9 * Fy * best[5] / 1000)) +
               (maxM / (0.9 * Fy * best[7] / 1e6)) +
               0.5 * (maxV / (0.6 * Fy * (best[1] - 2 * best[4]) * best[3] / 1000));
    }

    const [name, h, b, tw, tf, A, I, Zx] = best;
    const Pn = 0.9 * Fy * A / 1000;
    const Mn = 0.9 * Fy * Zx / 1e6;
    const Vn = 0.6 * Fy * (h - 2 * tf) * tw / 1000;

    return {
      elementId: elem.id,
      label: elem.label,
      Pu: maxN,
      Mu: maxM,
      Vu: maxV,
      selectedSection: name,
      sectionArea: A / 100,           // cm²
      sectionInertia: I / 1e4,        // cm⁴
      sectionZx: Zx / 1e3,            // cm³
      axialCapacity: Pn,
      flexuralCapacity: Mn,
      shearCapacity: Vn,
      utilizationRatio: Math.min(bestUR, 2.0),
      isSafe: bestUR <= 1.0,
    };
  });
}

export function getSteelSectionLibrary(): { name: string; h: number; b: number; A: number; I: number; Zx: number }[] {
  return STEEL_SECTIONS.map(([name, h, b, , , A, I, Zx]) => ({ name, h, b, A, I, Zx }));
}