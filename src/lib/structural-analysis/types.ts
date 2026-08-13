// ============================================================================
// 2D FRAME STRUCTURAL ANALYSIS — TYPE DEFINITIONS
// ============================================================================

export type SupportType = 'fixed' | 'hinged' | 'roller';
export type MaterialType = 'concrete' | 'steel';
export type LoadType = 'point' | 'udl' | 'moment';
export type LoadDirection = 'globalX' | 'globalY' | 'localX' | 'localY' | 'perpendicular';
export type AnalysisMode = 'select' | 'addNode' | 'addElement' | 'addLoad' | 'addSupport';

export interface Vec2 {
  x: number;
  y: number;
}

export interface FrameNode {
  id: number;
  x: number;       // mm
  y: number;       // mm
  support: SupportType | null;
  label: string;
}

export interface SectionProperties {
  // Concrete
  width?: number;     // mm (b)
  depth?: number;     // mm (h)
  // Steel
  sectionName?: string;
  area?: number;      // mm²
  inertia?: number;   // mm⁴ (I)
  elasticMod?: number; // MPa (E)
}

export interface FrameElement {
  id: number;
  nodeI: number;      // start node id
  nodeJ: number;      // end node id
  material: MaterialType;
  section: SectionProperties;
  label: string;
}

export interface FrameLoad {
  id: number;
  elementId: number;
  type: LoadType;
  magnitude: number;   // kN or kN/m or kN.m
  direction: LoadDirection;
  position?: number;   // for point loads: distance from nodeI (mm), 0-1 = fraction
  positionEnd?: number; // for UDL: end position fraction (default 1.0)
}

export interface NodeDisplacement {
  nodeId: number;
  ux: number;  // mm
  uy: number;  // mm
  rotation: number; // rad
}

export interface MemberEndForces {
  elementId: number;
  // Local coordinate forces at node I
  Ni: number;   // axial (kN) +tension
  Vi: number;   // shear (kN)
  Mi: number;   // moment (kN.m)
  // Local coordinate forces at node J
  Nj: number;
  Vj: number;
  Mj: number;
}

export interface InternalForcesAtPoint {
  x: number;    // position along member (0 to L) in mm
  N: number;    // axial force (kN)
  V: number;    // shear force (kN)
  M: number;    // bending moment (kN.m)
}

export interface AnalysisResults {
  displacements: NodeDisplacement[];
  memberForces: MemberEndForces[];
  internalForces: Map<number, InternalForcesAtPoint[]>;  // elementId -> points
  isStable: boolean;
  message: string;
}

export interface ConcreteDesignResult {
  elementId: number;
  label: string;
  elementType: 'beam' | 'column';
  Mu: number;          // max moment (kN.m)
  Vu: number;          // max shear (kN)
  Pu: number;          // axial force (kN)
  As_required: number; // mm²
  As_provided: number; // mm²
  bars: string;        // e.g. "3Ø16"
  barDia: number;      // mm
  barCount: number;
  stirrups: string;    // e.g. "Ø8@150"
  stirrupDia: number;
  stirrupSpacing: number; // mm
  isSafe: boolean;
  utilizationRatio: number;
}

export interface SteelDesignResult {
  elementId: number;
  label: string;
  Pu: number;
  Mu: number;
  Vu: number;
  selectedSection: string;
  sectionArea: number;     // cm²
  sectionInertia: number;  // cm⁴
  sectionZx: number;       // cm³
  axialCapacity: number;   // kN
  flexuralCapacity: number; // kN.m
  shearCapacity: number;   // kN
  utilizationRatio: number;
  isSafe: boolean;
}