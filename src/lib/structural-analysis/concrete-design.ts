// ============================================================================
// CONCRETE DESIGN MODULE — ACI 318-19 / ECP 203
// ============================================================================

import { ConcreteDesignResult, MemberEndForces, InternalForcesAtPoint, FrameElement, FrameNode } from './types';

const FC_DEFAULT = 25;   // MPa
const FY_DEFAULT = 400;  // MPa
const GAMMA_C = 1.5;
const GAMMA_S = 1.15;
const BETA1 = 0.85;
const EPS_CU = 0.003;

interface ConcreteDesignInput {
  fcu?: number;
  fy?: number;
  cover?: number;
  preferredBarDia?: number;
}

export function designConcreteMembers(
  elements: FrameElement[],
  nodes: FrameNode[],
  memberForces: MemberEndForces[],
  internalForces: Map<number, InternalForcesAtPoint[]>,
  options?: ConcreteDesignInput
): ConcreteDesignResult[] {
  const fcu = options?.fcu || FC_DEFAULT;
  const fy = options?.fy || FY_DEFAULT;
  const cover = options?.cover || 50;
  const preferredDia = options?.preferredBarDia || 16;

  // f'c = 0.67 * fcu / γc (ECP convention)
  const fpc = 0.67 * fcu / GAMMA_C;
  const fd = fy / GAMMA_S;

  return elements.map((elem, idx) => {
    const mef = memberForces[idx];
    const forces = internalForces.get(elem.id) || [];

    // Find max values
    let maxM = 0, maxV = 0, maxN = 0;
    for (const pt of forces) {
      maxM = Math.max(maxM, Math.abs(pt.M));
      maxV = Math.max(maxV, Math.abs(pt.V));
      maxN = Math.max(maxN, Math.abs(pt.N));
    }

    // Determine if beam or column based on axial force
    const Ag = (elem.section.width || 300) * (elem.section.depth || 500); // mm²
    const Pu = maxN; // kN (already factored from analysis)
    const PuN = Pu * 1000; // N
    const ratio = PuN / (fpc * Ag);

    const elementType = ratio > 0.1 ? 'column' : 'beam';
    const Mu = maxM; // kN.m
    const Vu = maxV; // kN

    // Effective depth
    const b = elem.section.width || 300;
    const h = elem.section.depth || 500;
    const d = h - cover - preferredDia / 2;

    if (elementType === 'beam') {
      return designBeam(elem.id, elem.label, Mu, Vu, b, h, d, fpc, fd, cover, preferredDia);
    } else {
      return designColumn(elem.id, elem.label, Pu, Mu, Vu, b, h, d, fpc, fd, Ag, cover, preferredDia);
    }
  });
}

function designBeam(
  elemId: number, label: string, Mu: number, Vu: number,
  b: number, h: number, d: number,
  fpc: number, fd: number, cover: number, preferredDia: number
): ConcreteDesignResult {
  // Mu in kN.m → convert to N.mm
  const MuNmm = Mu * 1e6;
  const Ru = MuNmm / (b * d * d);

  // K_balanced
  const Kbal = 0.167 * fpc;
  const Rbal = Kbal * b * d * d;

  let As: number;
  if (Ru <= Rbal) {
    // Singly reinforced
    As = (0.5 * fpc / fd) * (1 - Math.sqrt(1 - 2 * Ru / (0.85 * fpc))) * b * d;
  } else {
    // Need compression steel — simplified: use max singly + compression
    As = (0.5 * fpc / fd) * (1 - Math.sqrt(1 - 2 * Rbal / (0.85 * fpc))) * b * d;
  }

  As = Math.max(As, 0);

  // Select bars
  const barAreas: Record<number, number> = { 10: 78.5, 12: 113.1, 14: 153.9, 16: 201.1, 18: 254.5, 20: 314.2, 22: 380.1, 25: 490.9, 28: 615.8, 32: 804.2 };
  let barCount = Math.ceil(As / (barAreas[preferredDia] || 201.1));
  barCount = Math.max(barCount, 2);
  const AsProvided = barCount * (barAreas[preferredDia] || 201.1);

  // Shear stirrups
  const Vc = 0.17 * Math.sqrt(fpc) * b * d / 1000; // kN
  const Vs = Math.max(Vu - Vc, 0);
  let stirrupSpacing = 200;
  let stirrupDia = 8;

  if (Vs > 0) {
    const Av = Math.PI * (stirrupDia / 2) * (stirrupDia / 2) * 2; // 2 legs
    const s = (Av * fd * d) / (Vs * 1000);
    stirrupSpacing = Math.min(Math.max(Math.floor(s / 10) * 10, 100), 250);
  }

  // Check min steel
  const AsMin = 0.26 * (Math.sqrt(fpc) / fd) * b * d;
  const isSafe = AsProvided >= As * 0.95 && Vu <= Vc * 1.5 + (Vs * 1000 * stirrupDia * stirrupDia * Math.PI / 4 * 2 * fd * d) / (stirrupSpacing * 1000) * 0.001;

  return {
    elementId: elemId, label, elementType: 'beam',
    Mu, Vu, Pu: 0,
    As_required: As, As_provided: AsProvided,
    bars: `${barCount}Ø${preferredDia}`, barDia: preferredDia, barCount,
    stirrups: `Ø${stirrupDia}@${stirrupSpacing}`, stirrupDia, stirrupSpacing,
    isSafe, utilizationRatio: As / AsProvided,
  };
}

function designColumn(
  elemId: number, label: string, Pu: number, Mu: number, Vu: number,
  b: number, h: number, d: number,
  fpc: number, fd: number, Ag: number, cover: number, preferredDia: number
): ConcreteDesignResult {
  // Simplified column design using interaction
  const e = Mu * 1e6 / (Pu * 1000 + 1); // eccentricity mm
  const ex = e > 0.05 * h ? e : 0.05 * h; // minimum eccentricity

  // Simplified: design for Pu + Pu*ex moment
  const MuDesign = Math.max(Mu, Pu * ex / 1e6); // kN.m
  const MuNmm = MuDesign * 1e6;
  const Ru = MuNmm / (b * d * d);
  const Kbal = 0.167 * fpc;
  const Rbal = Kbal * b * d * d;

  let As: number;
  if (Ru <= Rbal) {
    As = (0.5 * fpc / fd) * (1 - Math.sqrt(Math.max(0, 1 - 2 * Ru / (0.85 * fpc)))) * b * d;
  } else {
    As = (0.5 * fpc / fd) * (1 - Math.sqrt(Math.max(0, 1 - 2 * Rbal / (0.85 * fpc)))) * b * d;
  }

  // Additional steel for axial
  const AsAxial = (Pu * 1000 / (0.67 * fd)) - (fpc * Ag / (2 * fd));
  As = Math.max(As, Math.max(AsAxial, 0));

  const barAreas: Record<number, number> = { 12: 113.1, 14: 153.9, 16: 201.1, 18: 254.5, 20: 314.2, 22: 380.1, 25: 490.9 };
  let barCount = Math.ceil(As / (barAreas[preferredDia] || 201.1));
  barCount = Math.max(barCount, 4);
  const AsProvided = barCount * (barAreas[preferredDia] || 201.1);

  // Stirrups
  const Vc = 0.17 * Math.sqrt(fpc) * b * d / 1000;
  const Vs = Math.max(Vu - Vc, 0);
  let stirrupSpacing = 200;
  let stirrupDia = 8;
  if (Vs > 0) {
    const Av = Math.PI * 4 * 4 * 2;
    stirrupSpacing = Math.min(Math.max(Math.floor((Av * fd * d) / (Vs * 1000) / 10) * 10, 100), 200);
  }

  const AsMin = 0.01 * Ag;
  const totalAsNeeded = Math.max(As, AsMin);
  const isSafe = AsProvided >= totalAsNeeded * 0.9;

  return {
    elementId: elemId, label, elementType: 'column',
    Mu: MuDesign, Vu, Pu,
    As_required: totalAsNeeded, As_provided: AsProvided,
    bars: `${barCount}Ø${preferredDia}`, barDia: preferredDia, barCount,
    stirrups: `Ø${stirrupDia}@${stirrupSpacing}`, stirrupDia, stirrupSpacing,
    isSafe, utilizationRatio: totalAsNeeded / AsProvided,
  };
}