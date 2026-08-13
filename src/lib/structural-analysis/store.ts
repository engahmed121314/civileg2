// ============================================================================
// ZUSTAND STORE — Frame Structural Analysis State
// ============================================================================

import { create } from 'zustand';
import {
  FrameNode, FrameElement, FrameLoad, AnalysisResults,
  ConcreteDesignResult, SteelDesignResult,
  AnalysisMode, MaterialType, SupportType, LoadType, LoadDirection,
  SectionProperties
} from '@/lib/structural-analysis/types';
import { analyzeFrame } from '@/lib/structural-analysis/frame-analyzer';
import { designConcreteMembers } from '@/lib/structural-analysis/concrete-design';
import { designSteelMembers } from '@/lib/structural-analysis/steel-design';

interface FrameStore {
  // Data
  nodes: FrameNode[];
  elements: FrameElement[];
  loads: FrameLoad[];
  nextNodeId: number;
  nextElementId: number;
  nextLoadId: number;

  // UI State
  mode: AnalysisMode;
  selectedElementId: number | null;
  selectedNodeId: number | null;
  showGrid: boolean;
  showLabels: boolean;

  // Analysis
  analysisResults: AnalysisResults | null;
  concreteResults: ConcreteDesignResult[] | null;
  steelResults: SteelDesignResult[] | null;
  isAnalyzing: boolean;
  analysisMessage: string | null;

  // Actions
  setMode: (mode: AnalysisMode) => void;
  addNode: (x: number, y: number) => void;
  addElement: (nodeI: number, nodeJ: number, material: MaterialType, section: SectionProperties) => void;
  addLoad: (elementId: number, type: LoadType, magnitude: number, direction: LoadDirection, position?: number) => void;
  removeNode: (id: number) => void;
  removeElement: (id: number) => void;
  removeLoad: (id: number) => void;
  updateNode: (id: number, updates: Partial<FrameNode>) => void;
  updateElement: (id: number, updates: Partial<FrameElement>) => void;
  setSupport: (nodeId: number, support: SupportType | null) => void;
  selectElement: (id: number | null) => void;
  selectNode: (id: number | null) => void;
  toggleGrid: () => void;
  toggleLabels: () => void;
  runAnalysis: () => void;
  clearAll: () => void;
  loadExample: (type: 'portal' | 'beam' | 'truss') => void;
}

export const useFrameStore = create<FrameStore>((set, get) => ({
  nodes: [],
  elements: [],
  loads: [],
  nextNodeId: 1,
  nextElementId: 1,
  nextLoadId: 1,
  mode: 'select',
  selectedElementId: null,
  selectedNodeId: null,
  showGrid: true,
  showLabels: true,
  analysisResults: null,
  concreteResults: null,
  steelResults: null,
  isAnalyzing: false,
  analysisMessage: null,

  setMode: (mode) => set({ mode, selectedElementId: null, selectedNodeId: null }),

  addNode: (x, y) => {
    const { nextNodeId } = get();
    const node: FrameNode = { id: nextNodeId, x, y, support: null, label: `N${nextNodeId}` };
    set({ nodes: [...get().nodes, node], nextNodeId: nextNodeId + 1 });
  },

  addElement: (nodeI, nodeJ, material, section) => {
    const { nextElementId, elements } = get();
    if (nodeI === nodeJ) return;
    // Check duplicate
    if (elements.some(e => (e.nodeI === nodeI && e.nodeJ === nodeJ) || (e.nodeI === nodeJ && e.nodeJ === nodeI))) return;

    const elem: FrameElement = {
      id: nextElementId, nodeI, nodeJ, material, section,
      label: `E${nextElementId}`,
    };
    set({ elements: [...elements, elem], nextElementId: nextElementId + 1 });
  },

  addLoad: (elementId, type, magnitude, direction, position) => {
    const { nextLoadId, loads } = get();
    const load: FrameLoad = { id: nextLoadId, elementId, type, magnitude, direction, position };
    set({ loads: [...loads, load], nextLoadId: nextLoadId + 1 });
  },

  removeNode: (id) => {
    const { elements, loads } = get();
    const elemIds = elements.filter(e => e.nodeI === id || e.nodeJ === id).map(e => e.id);
    set({
      nodes: get().nodes.filter(n => n.id !== id),
      elements: elements.filter(e => !elemIds.includes(e.id)),
      loads: loads.filter(l => !elemIds.includes(l.elementId)),
    });
  },

  removeElement: (id) => set({
    elements: get().elements.filter(e => e.id !== id),
    loads: get().loads.filter(l => l.elementId !== id),
  }),

  removeLoad: (id) => set({ loads: get().loads.filter(l => l.id !== id) }),

  updateNode: (id, updates) => set({
    nodes: get().nodes.map(n => n.id === id ? { ...n, ...updates } : n),
  }),

  updateElement: (id, updates) => set({
    elements: get().elements.map(e => e.id === id ? { ...e, ...updates } : e),
  }),

  setSupport: (nodeId, support) => set({
    nodes: get().nodes.map(n => n.id === nodeId ? { ...n, support } : n),
  }),

  selectElement: (id) => set({ selectedElementId: id, selectedNodeId: null, mode: id ? 'select' : get().mode }),
  selectNode: (id) => set({ selectedNodeId: id, selectedElementId: null, mode: id ? 'select' : get().mode }),
  toggleGrid: () => set(s => ({ showGrid: !s.showGrid })),
  toggleLabels: () => set(s => ({ showLabels: !s.showLabels })),

  runAnalysis: () => {
    const { nodes, elements, loads } = get();
    set({ isAnalyzing: true, analysisMessage: null, analysisResults: null, concreteResults: null, steelResults: null });

    try {
      const results = analyzeFrame(nodes, elements, loads);

      if (!results.isStable) {
        set({ isAnalyzing: false, analysisMessage: results.message });
        return;
      }

      // Design check
      const hasConcrete = elements.some(e => e.material === 'concrete');
      const hasSteel = elements.some(e => e.material === 'steel');

      let concreteResults: ConcreteDesignResult[] | null = null;
      let steelResults: SteelDesignResult[] | null = null;

      if (hasConcrete && results.memberForces.length > 0) {
        concreteResults = designConcreteMembers(
          elements.filter(e => e.material === 'concrete'),
          nodes,
          results.memberForces.filter((_, i) => elements[i].material === 'concrete'),
          results.internalForces
        );
      }

      if (hasSteel && results.memberForces.length > 0) {
        steelResults = designSteelMembers(
          elements.filter(e => e.material === 'steel'),
          results.memberForces.filter((_, i) => elements[i].material === 'steel'),
          results.internalForces
        );
      }

      set({
        isAnalyzing: false,
        analysisResults: results,
        concreteResults,
        steelResults,
        analysisMessage: results.message,
      });
    } catch (e: any) {
      set({ isAnalyzing: false, analysisMessage: `Error: ${e.message}` });
    }
  },

  clearAll: () => set({
    nodes: [], elements: [], loads: [],
    nextNodeId: 1, nextElementId: 1, nextLoadId: 1,
    analysisResults: null, concreteResults: null, steelResults: null,
    analysisMessage: null, selectedElementId: null, selectedNodeId: null,
  }),

  loadExample: (type) => {
    const s = get();
    s.clearAll();

    if (type === 'beam') {
      // Simply supported beam: 2 nodes, 1 element, UDL
      s.addNode(0, 0); s.addNode(6000, 0);
      s.setSupport(1, 'hinged'); s.setSupport(2, 'roller');
      s.addElement(1, 2, 'concrete', { width: 300, depth: 500, elasticMod: 25000 });
      s.addLoad(1, 'udl', -20, 'perpendicular');
    } else if (type === 'portal') {
      // Portal frame: 4 nodes, 3 elements
      s.addNode(0, 4000);     // N1 bottom-left
      s.addNode(0, 0);       // N2 top-left
      s.addNode(6000, 0);    // N3 top-right
      s.addNode(6000, 4000); // N4 bottom-right
      s.setSupport(1, 'fixed'); s.setSupport(4, 'fixed');
      s.addElement(1, 2, 'concrete', { width: 300, depth: 500, elasticMod: 25000 }); // Left column
      s.addElement(2, 3, 'concrete', { width: 300, depth: 600, elasticMod: 25000 }); // Beam
      s.addElement(3, 4, 'concrete', { width: 300, depth: 500, elasticMod: 25000 }); // Right column
      s.addLoad(2, 'udl', -25, 'perpendicular'); // UDL on beam
    } else if (type === 'truss') {
      // Simple truss
      s.addNode(0, 0); s.addNode(3000, 0); s.addNode(6000, 0);
      s.addNode(1500, 2500); s.addNode(4500, 2500);
      s.setSupport(1, 'hinged'); s.setSupport(3, 'roller');
      s.addElement(1, 2, 'steel', { area: 2000, inertia: 1e8, elasticMod: 200000 });
      s.addElement(2, 3, 'steel', { area: 2000, inertia: 1e8, elasticMod: 200000 });
      s.addElement(1, 4, 'steel', { area: 1500, inertia: 5e7, elasticMod: 200000 });
      s.addElement(2, 4, 'steel', { area: 1500, inertia: 5e7, elasticMod: 200000 });
      s.addElement(2, 5, 'steel', { area: 1500, inertia: 5e7, elasticMod: 200000 });
      s.addElement(3, 5, 'steel', { area: 1500, inertia: 5e7, elasticMod: 200000 });
      s.addElement(4, 5, 'steel', { area: 2000, inertia: 1e8, elasticMod: 200000 });
      s.addLoad(2, 'point', -50, 'globalY', 0.5); // Load at mid-span of bottom chord
      s.addLoad(4, 'point', -30, 'globalY', 0.5);
    }
  },
}));