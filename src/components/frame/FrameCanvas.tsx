'use client';

import React, { useRef, useEffect, useCallback, useState } from 'react';
import { useFrameStore } from '@/lib/structural-analysis/store';
import { InternalForcesAtPoint, FrameNode } from '@/lib/structural-analysis/types';

interface FrameCanvasProps {
  width: number;
  height: number;
}

const NODE_RADIUS = 8;
const SNAP_DISTANCE = 30;
const COLORS = {
  grid: '#e5e7eb',
  element: '#374151',
  elementHighlight: '#2563eb',
  node: '#111827',
  nodeSupport: '#dc2626',
  load: '#ea580c',
  dimension: '#6b7280',
  deformed: '#2563eb',
  bmd: '#2563eb',
  sfd: '#dc2626',
  nfd: '#059669',
  text: '#374151',
  background: '#ffffff',
};

export default function FrameCanvas({ width, height }: FrameCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [canvasSize, setCanvasSize] = useState({ w: width, h: height });
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [scale, setScale] = useState(0.08);
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });
  const [pendingNode, setPendingNode] = useState<number | null>(null);
  const [hoveredNodeId, setHoveredNodeId] = useState<number | null>(null);
  const [hoveredElemId, setHoveredElemId] = useState<number | null>(null);

  const {
    nodes, elements, loads, mode, showGrid, showLabels,
    analysisResults, selectedElementId, selectedNodeId,
    addNode, addElement, selectElement, selectNode,
  } = useFrameStore();

  // Coordinate transforms
  const toScreen = useCallback((x: number, y: number) => ({
    sx: canvasSize.w / 2 + (x + pan.x) * scale,
    sy: canvasSize.h / 2 - (y + pan.y) * scale, // flip Y
  }), [canvasSize, pan, scale]);

  const toWorld = useCallback((sx: number, sy: number) => ({
    x: (sx - canvasSize.w / 2) / scale - pan.x,
    y: -(sy - canvasSize.h / 2) / scale - pan.y, // flip Y
  }), [canvasSize, pan, scale]);

  // Find nearest node
  const findNearestNode = useCallback((wx: number, wy: number) => {
    let nearest: FrameNode | null = null;
    let minDist = SNAP_DISTANCE / scale;
    for (const n of nodes) {
      const d = Math.sqrt((n.x - wx) ** 2 + (n.y - wy) ** 2);
      if (d < minDist) { minDist = d; nearest = n; }
    }
    return nearest;
  }, [nodes, scale]);

  // Find nearest element
  const findNearestElement = useCallback((wx: number, wy: number) => {
    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    let nearest = -1;
    let minDist = 20 / scale;

    for (const elem of elements) {
      const nI = nodeMap.get(elem.nodeI);
      const nJ = nodeMap.get(elem.nodeJ);
      if (!nI || !nJ) continue;

      const dx = nJ.x - nI.x;
      const dy = nJ.y - nI.y;
      const len2 = dx * dx + dy * dy;
      let t = ((wx - nI.x) * dx + (wy - nI.y) * dy) / (len2 || 1);
      t = Math.max(0, Math.min(1, t));
      const px = nI.x + t * dx;
      const py = nI.y + t * dy;
      const d = Math.sqrt((wx - px) ** 2 + (wy - py) ** 2);
      if (d < minDist) { minDist = d; nearest = elem.id; }
    }
    return nearest;
  }, [nodes, elements, scale]);

  // Resize observer
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const ro = new ResizeObserver(entries => {
      const { width: w, height: h } = entries[0].contentRect;
      if (w > 0 && h > 0) setCanvasSize({ w: Math.floor(w), h: Math.floor(h) });
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, []);

  // Mouse handlers
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const { x, y } = toWorld(sx, sy);

    if (e.button === 1 || (e.button === 0 && e.altKey)) {
      setIsPanning(true);
      setPanStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
      return;
    }

    if (e.button === 2) { // Right click - delete
      const node = findNearestNode(x, y);
      if (node) { useFrameStore.getState().removeNode(node.id); return; }
      const elemId = findNearestElement(x, y);
      if (elemId >= 0) { useFrameStore.getState().removeElement(elemId); return; }
      return;
    }

    if (mode === 'addNode') {
      addNode(Math.round(x / 10) * 10, Math.round(y / 10) * 10);
    } else if (mode === 'addElement') {
      const node = findNearestNode(x, y);
      if (node) {
        if (pendingNode === null) {
          setPendingNode(node.id);
        } else if (node.id !== pendingNode) {
          addElement(pendingNode, node.id, 'concrete', { width: 300, depth: 500, elasticMod: 25000 });
          setPendingNode(null);
        }
      }
    } else if (mode === 'select') {
      const node = findNearestNode(x, y);
      if (node) { selectNode(node.id); return; }
      const elemId = findNearestElement(x, y);
      if (elemId >= 0) { selectElement(elemId); } else { selectElement(null); selectNode(null); }
    }
  }, [mode, addNode, addElement, selectElement, selectNode, findNearestNode, findNearestElement, toWorld, pendingNode, pan]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (isPanning) {
      setPan({ x: e.clientX - panStart.x, y: e.clientY - panStart.y });
      return;
    }
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const sx = e.clientX - rect.left;
    const sy = e.clientY - rect.top;
    const { x, y } = toWorld(sx, sy);
    const node = findNearestNode(x, y);
    setHoveredNodeId(node?.id ?? null);
    if (!node) {
      const elemId = findNearestElement(x, y);
      setHoveredElemId(elemId >= 0 ? elemId : null);
    } else {
      setHoveredElemId(null);
    }
  }, [isPanning, panStart, toWorld, findNearestNode, findNearestElement]);

  const handleMouseUp = useCallback(() => { setIsPanning(false); }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const factor = e.deltaY > 0 ? 0.9 : 1.1;
    setScale(s => Math.max(0.01, Math.min(2, s * factor)));
  }, []);

  // Drawing
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = canvasSize.w * dpr;
    canvas.height = canvasSize.h * dpr;
    ctx.scale(dpr, dpr);

    // Clear
    ctx.fillStyle = COLORS.background;
    ctx.fillRect(0, 0, canvasSize.w, canvasSize.h);

    // Grid
    if (showGrid) {
      ctx.strokeStyle = COLORS.grid;
      ctx.lineWidth = 0.5;
      const gridSize = 500; // mm in world
      const gridScreen = gridSize * scale;
      if (gridScreen > 10) {
        const originX = canvasSize.w / 2 + pan.x * scale;
        const originY = canvasSize.h / 2 - pan.y * scale;
        // Vertical lines
        for (let sx = originX % gridScreen; sx < canvasSize.w; sx += gridScreen) {
          ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, canvasSize.h); ctx.stroke();
        }
        // Horizontal lines
        for (let sy = originY % gridScreen; sy < canvasSize.h; sy += gridScreen) {
          ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(canvasSize.w, sy); ctx.stroke();
        }
      }
    }

    // Origin crosshair
    const o = toScreen(0, 0);
    ctx.strokeStyle = '#cbd5e1';
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 5]);
    ctx.beginPath(); ctx.moveTo(o.sx, 0); ctx.lineTo(o.sx, canvasSize.h); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, o.sy); ctx.lineTo(canvasSize.w, o.sy); ctx.stroke();
    ctx.setLineDash([]);

    const nodeMap = new Map(nodes.map(n => [n.id, n]));

    // Draw elements
    for (const elem of elements) {
      const nI = nodeMap.get(elem.nodeI);
      const nJ = nodeMap.get(elem.nodeJ);
      if (!nI || !nJ) continue;

      const pI = toScreen(nI.x, nI.y);
      const pJ = toScreen(nJ.x, nJ.y);

      const isSelected = elem.id === selectedElementId || elem.id === hoveredElemId;
      const isConcrete = elem.material === 'concrete';

      // Element line
      ctx.strokeStyle = isSelected ? COLORS.elementHighlight : COLORS.element;
      ctx.lineWidth = isSelected ? 3 : isConcrete ? 3 : 2;
      ctx.beginPath(); ctx.moveTo(pI.sx, pI.sy); ctx.lineTo(pJ.sx, pJ.sy); ctx.stroke();

      // Element label
      if (showLabels) {
        const mx = (pI.sx + pJ.sx) / 2;
        const my = (pI.sy + pJ.sy) / 2;
        const dx = nJ.x - nI.x;
        const dy = nJ.y - nI.y;
        const len = Math.sqrt(dx * dx + dy * dy);
        ctx.save();
        ctx.translate(mx, my);
        ctx.rotate(-Math.atan2(dy, dx));
        ctx.fillStyle = COLORS.text;
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        const matLabel = isConcrete ? 'RC' : 'S';
        ctx.fillText(`${elem.label} (${matLabel})`, 0, -8);
        ctx.restore();
      }

      // Load arrows
      const elemLoads = loads.filter(l => l.elementId === elem.id);
      for (const load of elemLoads) {
        const numArrows = load.type === 'udl' ? 8 : 1;
        const angle = Math.atan2(-(nJ.y - nI.y), nJ.x - nI.x); // perpendicular direction

        for (let i = 0; i < numArrows; i++) {
          const t = (i + 0.5) / numArrows;
          const px = pI.sx + t * (pJ.sx - pI.sx);
          const py = pI.sy + t * (pJ.sy - pI.sy);

          const arrowLen = 25;
          const ex = px + Math.sin(angle) * arrowLen;
          const ey = py - Math.cos(angle) * arrowLen;

          ctx.strokeStyle = COLORS.load;
          ctx.fillStyle = COLORS.load;
          ctx.lineWidth = 1.5;
          ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(ex, ey); ctx.stroke();

          // Arrowhead
          const headSize = 6;
          const ha = Math.atan2(ey - py, ex - px);
          ctx.beginPath();
          ctx.moveTo(ex, ey);
          ctx.lineTo(ex - headSize * Math.cos(ha - 0.4), ey - headSize * Math.sin(ha - 0.4));
          ctx.lineTo(ex - headSize * Math.cos(ha + 0.4), ey - headSize * Math.sin(ha + 0.4));
          ctx.closePath(); ctx.fill();
        }

        // Load label
        const lmx = (pI.sx + pJ.sx) / 2 + 15;
        const lmy = (pI.sy + pJ.sy) / 2 - 20;
        ctx.fillStyle = COLORS.load;
        ctx.font = 'bold 11px sans-serif';
        const typeLabel = load.type === 'udl' ? `${Math.abs(load.magnitude)} kN/m` :
                         load.type === 'point' ? `${Math.abs(load.magnitude)} kN` :
                         `${load.magnitude} kN.m`;
        ctx.fillText(typeLabel, lmx, lmy);
      }
    }

    // Draw support symbols
    for (const node of nodes) {
      if (!node.support) continue;
      const p = toScreen(node.x, node.y);
      const sz = 16;

      ctx.strokeStyle = COLORS.nodeSupport;
      ctx.fillStyle = COLORS.nodeSupport;
      ctx.lineWidth = 2;

      if (node.support === 'fixed') {
        // Fixed: hatched triangle
        ctx.beginPath();
        ctx.moveTo(p.sx - sz, p.sy + sz * 1.5);
        ctx.lineTo(p.sx + sz, p.sy + sz * 1.5);
        ctx.lineTo(p.sx, p.sy);
        ctx.closePath(); ctx.stroke();
        // Ground line
        ctx.beginPath(); ctx.moveTo(p.sx - sz - 5, p.sy + sz * 1.5); ctx.lineTo(p.sx + sz + 5, p.sy + sz * 1.5); ctx.stroke();
        // Hatch
        for (let i = -2; i <= 2; i++) {
          const hx = p.sx + i * 8;
          ctx.beginPath(); ctx.moveTo(hx, p.sy + sz * 1.5); ctx.lineTo(hx - 5, p.sy + sz * 1.5 + 8); ctx.stroke();
        }
      } else if (node.support === 'hinged') {
        // Pin: triangle
        ctx.beginPath();
        ctx.moveTo(p.sx, p.sy);
        ctx.lineTo(p.sx - sz, p.sy + sz * 1.2);
        ctx.lineTo(p.sx + sz, p.sy + sz * 1.2);
        ctx.closePath(); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(p.sx - sz - 5, p.sy + sz * 1.2); ctx.lineTo(p.sx + sz + 5, p.sy + sz * 1.2); ctx.stroke();
        ctx.beginPath(); ctx.arc(p.sx, p.sy + sz * 1.2 + 5, 4, 0, Math.PI * 2); ctx.stroke();
      } else if (node.support === 'roller') {
        // Roller: triangle + circle
        ctx.beginPath();
        ctx.moveTo(p.sx, p.sy);
        ctx.lineTo(p.sx - sz, p.sy + sz);
        ctx.lineTo(p.sx + sz, p.sy + sz);
        ctx.closePath(); ctx.stroke();
        ctx.beginPath(); ctx.arc(p.sx - 7, p.sy + sz + 6, 5, 0, Math.PI * 2); ctx.stroke();
        ctx.beginPath(); ctx.arc(p.sx + 7, p.sy + sz + 6, 5, 0, Math.PI * 2); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(p.sx - sz - 5, p.sy + sz + 13); ctx.lineTo(p.sx + sz + 5, p.sy + sz + 13); ctx.stroke();
      }
    }

    // Draw nodes
    for (const node of nodes) {
      const p = toScreen(node.x, node.y);
      const isSelected = node.id === selectedNodeId || node.id === hoveredNodeId;
      const isPending = node.id === pendingNode;

      ctx.beginPath();
      ctx.arc(p.sx, p.sy, isSelected ? NODE_RADIUS + 3 : NODE_RADIUS, 0, Math.PI * 2);
      ctx.fillStyle = isPending ? '#f59e0b' : isSelected ? '#2563eb' : COLORS.node;
      ctx.fill();
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.stroke();

      // Node label
      if (showLabels) {
        ctx.fillStyle = COLORS.text;
        ctx.font = 'bold 11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(node.label, p.sx, p.sy - 14);
        if (node.support) {
          ctx.fillStyle = COLORS.nodeSupport;
          ctx.font = '10px sans-serif';
          ctx.fillText(node.support.toUpperCase(), p.sx, p.sy + 22);
        }
      }
    }

    // Deformed shape (if analysis done)
    if (analysisResults?.isStable && analysisResults.displacements.length > 0) {
      const deformScale = scale * 50; // amplification
      ctx.strokeStyle = COLORS.deformed;
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 4]);

      const dispMap = new Map(analysisResults.displacements.map(d => [d.nodeId, d]));

      for (const elem of elements) {
        const nI = nodeMap.get(elem.nodeI);
        const nJ = nodeMap.get(elem.nodeJ);
        if (!nI || !nJ) continue;

        const dI = dispMap.get(elem.nodeI);
        const dJ = dispMap.get(elem.nodeJ);
        if (!dI || !dJ) continue;

        const pI = toScreen(nI.x + dI.ux * 50, nI.y + dI.uy * 50);
        const pJ = toScreen(nJ.x + dJ.ux * 50, nJ.y + dJ.uy * 50);

        ctx.beginPath(); ctx.moveTo(pI.sx, pI.sy); ctx.lineTo(pJ.sx, pJ.sy); ctx.stroke();
      }
      ctx.setLineDash([]);
    }

    // Pending element line
    if (pendingNode !== null) {
      const n = nodeMap.get(pendingNode);
      if (n) {
        const p = toScreen(n.x, n.y);
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 2;
        ctx.setLineDash([5, 5]);
        ctx.beginPath(); ctx.arc(p.sx, p.sy, NODE_RADIUS + 8, 0, Math.PI * 2); ctx.stroke();
        ctx.setLineDash([]);
      }
    }

    // Scale indicator
    ctx.fillStyle = COLORS.dimension;
    ctx.font = '10px sans-serif';
    ctx.fillText(`Scale: 1:${(1 / scale).toFixed(0)} | Zoom: ${(scale * 100).toFixed(0)}%`, 10, canvasSize.h - 10);

  }, [nodes, elements, loads, mode, showGrid, showLabels, analysisResults, selectedElementId, selectedNodeId, hoveredElemId, hoveredNodeId, pendingNode, canvasSize, toScreen, pan, scale]);

  return (
    <div ref={containerRef} className="w-full h-full min-h-[400px] relative cursor-crosshair border rounded-lg overflow-hidden bg-white">
      <canvas
        ref={canvasRef}
        style={{ width: canvasSize.w, height: canvasSize.h }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onContextMenu={(e) => e.preventDefault()}
      />
      <div className="absolute top-2 left-2 bg-white/90 backdrop-blur-sm rounded-md px-2 py-1 text-xs text-muted-foreground border">
        Mode: <span className="font-medium text-foreground">{mode === 'addNode' ? 'Click to add nodes' : mode === 'addElement' ? 'Click 2 nodes to connect' : 'Click to select'}</span>
      </div>
    </div>
  );
}