'use client';

import React, { useRef, useEffect, useState } from 'react';
import { useFrameStore } from '@/lib/structural-analysis/store';
import { InternalForcesAtPoint } from '@/lib/structural-analysis/types';

type DiagramType = 'BMD' | 'SFD' | 'NFD';

interface DiagramProps {
  type: DiagramType;
  height?: number;
}

const DIAGRAM_COLORS: Record<DiagramType, string> = {
  BMD: '#2563eb',
  SFD: '#dc2626',
  NFD: '#059669',
};

export default function ResultsDiagram({ type, height = 300 }: DiagramProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 600, h: height });
  const [tooltip, setTooltip] = useState<{ x: number; y: number; value: string; visible: boolean }>({ x: 0, y: 0, value: '', visible: false });

  const { nodes, elements, analysisResults, selectedElementId } = useFrameStore();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const ro = new ResizeObserver(entries => {
      const { width: w, height: h } = entries[0].contentRect;
      if (w > 0 && h > 0) setSize({ w: Math.floor(w), h: Math.floor(h) });
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !analysisResults?.isStable) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size.w * dpr;
    canvas.height = size.h * dpr;
    ctx.scale(dpr, dpr);

    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, size.w, size.h);

    const margin = { left: 60, right: 30, top: 40, bottom: 40 };
    const plotW = size.w - margin.left - margin.right;
    const baseline = type === 'NFD' ? (size.h - margin.top - margin.bottom) / 2 + margin.top : size.h - margin.bottom;
    const drawH = type === 'NFD' ? (size.h - margin.top - margin.bottom) / 2 - 10 : size.h - margin.top - margin.bottom - 10;

    const nodeMap = new Map(nodes.map(n => [n.id, n]));

    // Find max absolute value across all elements
    let maxAbs = 1;
    const elemIds = selectedElementId ? [selectedElementId] : elements.map(e => e.id);
    for (const eid of elemIds) {
      const forces = analysisResults.internalForces.get(eid);
      if (!forces) continue;
      for (const pt of forces) {
        const val = type === 'BMD' ? pt.M : type === 'SFD' ? pt.V : pt.N;
        maxAbs = Math.max(maxAbs, Math.abs(val));
      }
    }

    // Title
    ctx.fillStyle = DIAGRAM_COLORS[type];
    ctx.font = 'bold 14px sans-serif';
    ctx.textAlign = 'center';
    const titles = { BMD: 'Bending Moment Diagram (BMD)', SFD: 'Shear Force Diagram (SFD)', NFD: 'Axial Force Diagram (NFD)' };
    ctx.fillText(titles[type], size.w / 2, 22);

    const units = { BMD: 'kN.m', SFD: 'kN', NFD: 'kN' };

    // Baseline
    ctx.strokeStyle = '#d1d5db';
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath(); ctx.moveTo(margin.left, baseline); ctx.lineTo(size.w - margin.right, baseline); ctx.stroke();
    ctx.setLineDash([]);

    // Baseline label
    ctx.fillStyle = '#6b7280';
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('0', margin.left - 15, baseline + 4);

    // Scale labels
    ctx.fillStyle = '#374151';
    ctx.font = '10px sans-serif';
    ctx.textAlign = 'right';
    const posLabel = `${maxAbs.toFixed(1)} ${units[type]}`;
    const negLabel = `${(-maxAbs).toFixed(1)} ${units[type]}`;
    if (type === 'NFD') {
      ctx.fillText(posLabel, margin.left - 5, margin.top + 12);
      ctx.fillText(negLabel, margin.left - 5, size.h - margin.bottom);
    } else {
      ctx.fillText(negLabel, margin.left - 5, margin.top + 12);
      ctx.fillText(posLabel, margin.left - 5, size.h - margin.bottom - 5);
    }

    // Grid lines
    ctx.strokeStyle = '#f3f4f6';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 4; i++) {
      const y = margin.top + (drawH * i) / 4;
      ctx.beginPath(); ctx.moveTo(margin.left, y); ctx.lineTo(size.w - margin.right, y); ctx.stroke();
    }

    // Draw diagrams for each element
    let currentX = margin.left;
    const totalLength = elemIds.reduce((sum, eid) => {
      const elem = elements.find(e => e.id === eid);
      if (!elem) return sum;
      const nI = nodeMap.get(elem.nodeI);
      const nJ = nodeMap.get(elem.nodeJ);
      if (!nI || !nJ) return sum;
      return sum + Math.sqrt((nJ.x - nI.x) ** 2 + (nJ.y - nI.y) ** 2);
    }, 0) || 1;

    for (const eid of elemIds) {
      const elem = elements.find(e => e.id === eid);
      if (!elem) continue;
      const nI = nodeMap.get(elem.nodeI);
      const nJ = nodeMap.get(elem.nodeJ);
      if (!nI || !nJ) continue;

      const L = Math.sqrt((nJ.x - nI.x) ** 2 + (nJ.y - nI.y) ** 2);
      const elemW = (L / totalLength) * plotW;
      const forces = analysisResults.internalForces.get(eid);
      if (!forces || forces.length === 0) { currentX += elemW; continue; }

      const color = DIAGRAM_COLORS[type];

      // Fill area
      ctx.fillStyle = color + '20';
      ctx.beginPath();
      ctx.moveTo(currentX, baseline);

      for (const pt of forces) {
        const px = currentX + (pt.x / L) * elemW;
        const val = type === 'BMD' ? pt.M : type === 'SFD' ? pt.V : pt.N;
        const py = baseline - (val / maxAbs) * (drawH / 2);
        ctx.lineTo(px, py);
      }
      ctx.lineTo(currentX + elemW, baseline);
      ctx.closePath();
      ctx.fill();

      // Line
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.beginPath();
      for (let i = 0; i < forces.length; i++) {
        const pt = forces[i];
        const px = currentX + (pt.x / L) * elemW;
        const val = type === 'BMD' ? pt.M : type === 'SFD' ? pt.V : pt.N;
        const py = baseline - (val / maxAbs) * (drawH / 2);
        if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
      }
      ctx.stroke();

      // Max/min annotations
      let maxVal = -Infinity, maxIdx = 0, minVal = Infinity, minIdx = 0;
      for (let i = 0; i < forces.length; i++) {
        const val = type === 'BMD' ? forces[i].M : type === 'SFD' ? forces[i].V : forces[i].N;
        if (val > maxVal) { maxVal = val; maxIdx = i; }
        if (val < minVal) { minVal = val; minIdx = i; }
      }

      const annotate = (idx: number, val: number, above: boolean) => {
        const px = currentX + (forces[idx].x / L) * elemW;
        const py = baseline - (val / maxAbs) * (drawH / 2);
        ctx.fillStyle = color;
        ctx.font = 'bold 10px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(`${val.toFixed(1)}`, px, above ? py - 8 : py + 14);
        // Dot
        ctx.beginPath(); ctx.arc(px, py, 3, 0, Math.PI * 2); ctx.fill();
      };

      if (maxVal !== minVal) {
        annotate(maxIdx, maxVal, maxVal > 0);
        annotate(minIdx, minVal, minVal < 0);
      }

      // Element separator
      ctx.strokeStyle = '#9ca3af';
      ctx.lineWidth = 1;
      ctx.setLineDash([3, 3]);
      ctx.beginPath(); ctx.moveTo(currentX + elemW, margin.top); ctx.lineTo(currentX + elemW, size.h - margin.bottom); ctx.stroke();
      ctx.setLineDash([]);

      // Element label
      ctx.fillStyle = '#374151';
      ctx.font = '10px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(elem.label, currentX + elemW / 2, size.h - 8);

      currentX += elemW;
    }

  }, [nodes, elements, analysisResults, selectedElementId, type, size]);

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!analysisResults?.isStable) return;
    // Simplified tooltip
    const rect = canvasRef.current?.getBoundingClientRect();
    if (rect) {
      setTooltip({ x: e.clientX - rect.left + 10, y: e.clientY - rect.top - 10, value: '', visible: false });
    }
  };

  if (!analysisResults?.isStable) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground text-sm border rounded-lg bg-muted/20">
        Run analysis to see diagrams
      </div>
    );
  }

  return (
    <div ref={containerRef} className="w-full relative" style={{ height }}>
      <canvas ref={canvasRef} style={{ width: size.w, height: size.h }} onMouseMove={handleMouseMove} />
    </div>
  );
}