'use client';

import React, { useState, useCallback } from 'react';
import { useFrameStore } from '@/lib/structural-analysis/store';
import { AnalysisMode, MaterialType, SupportType, LoadType, LoadDirection, FrameNode, FrameElement, SectionProperties } from '@/lib/structural-analysis/types';
import FrameCanvas from '@/components/frame/FrameCanvas';
import ResultsDiagram from '@/components/frame/ResultsDiagram';
import DesignOutput from '@/components/frame/DesignOutput';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Switch } from '@/components/ui/switch';

export default function FrameAnalysisPage() {
  const store = useFrameStore();
  const [diagramTab, setDiagramTab] = useState<'BMD' | 'SFD' | 'NFD'>('BMD');
  const [rightPanel, setRightPanel] = useState<'properties' | 'results' | 'design'>('properties');

  const selectedNode = store.nodes.find(n => n.id === store.selectedNodeId);
  const selectedElement = store.elements.find(e => e.id === store.selectedElementId);

  const modeButtons: { mode: AnalysisMode; label: string; icon: string; hint: string }[] = [
    { mode: 'select', label: 'Select', icon: '⊹', hint: 'Click nodes/elements' },
    { mode: 'addNode', label: 'Node', icon: '⊕', hint: 'Click canvas to place' },
    { mode: 'addElement', label: 'Element', icon: '⟶', hint: 'Click 2 nodes' },
  ];

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="flex items-center justify-between px-4 py-2 border-b bg-card">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-bold text-sm">
            SA
          </div>
          <div>
            <h1 className="text-base font-bold leading-tight">Frame Analysis & Design</h1>
            <p className="text-xs text-muted-foreground">2D Structural Solver — BMD / SFD / NFD / RFT</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Toolbar */}
          <div className="flex items-center gap-1 bg-muted rounded-lg p-0.5">
            {modeButtons.map(mb => (
              <Button
                key={mb.mode}
                variant={store.mode === mb.mode ? 'default' : 'ghost'}
                size="sm"
                className="h-7 px-2.5 text-xs gap-1"
                onClick={() => store.setMode(mb.mode)}
                title={mb.hint}
              >
                <span>{mb.icon}</span> {mb.label}
              </Button>
            ))}
          </div>

          <Separator orientation="vertical" className="h-6" />

          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" className="h-7 px-2 text-xs" onClick={() => store.loadExample('beam')}>
              Beam
            </Button>
            <Button variant="outline" size="sm" className="h-7 px-2 text-xs" onClick={() => store.loadExample('portal')}>
              Portal
            </Button>
            <Button variant="outline" size="sm" className="h-7 px-2 text-xs" onClick={() => store.loadExample('truss')}>
              Truss
            </Button>
          </div>

          <Separator orientation="vertical" className="h-6" />

          <div className="flex items-center gap-1.5">
            <Label className="text-[10px] text-muted-foreground">Grid</Label>
            <Switch checked={store.showGrid} onCheckedChange={() => store.toggleGrid()} className="scale-75" />
          </div>
          <div className="flex items-center gap-1.5">
            <Label className="text-[10px] text-muted-foreground">Labels</Label>
            <Switch checked={store.showLabels} onCheckedChange={() => store.toggleLabels()} className="scale-75" />
          </div>

          <Separator orientation="vertical" className="h-6" />

          <Button
            size="sm"
            className="h-8 px-4 text-xs font-bold"
            onClick={() => store.runAnalysis()}
            disabled={store.isAnalyzing || store.nodes.length < 2}
          >
            {store.isAnalyzing ? '⏳ Solving...' : '▶ Analyze'}
          </Button>

          <Button variant="outline" size="sm" className="h-7 px-2 text-xs text-destructive" onClick={() => store.clearAll()}>
            Clear
          </Button>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 flex min-h-0">
        {/* Canvas Area */}
        <div className="flex-1 p-2 min-w-0">
          <div className="w-full h-full">
            <FrameCanvas width={800} height={600} />
          </div>
          {store.analysisMessage && (
            <div className={`mt-1 px-3 py-1.5 rounded-md text-xs font-medium ${store.analysisResults?.isStable ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
              {store.analysisMessage}
            </div>
          )}
        </div>

        {/* Right Panel */}
        <div className="w-[340px] border-l flex flex-col bg-card">
          <div className="flex border-b">
            {(['properties', 'results', 'design'] as const).map(tab => (
              <button
                key={tab}
                className={`flex-1 py-2 text-xs font-medium capitalize transition-colors ${rightPanel === tab ? 'text-primary border-b-2 border-primary bg-muted/30' : 'text-muted-foreground hover:text-foreground'}`}
                onClick={() => setRightPanel(tab)}
              >
                {tab === 'properties' ? 'Properties' : tab === 'results' ? 'Diagrams' : 'Design'}
              </button>
            ))}
          </div>

          <ScrollArea className="flex-1">
            {rightPanel === 'properties' && (
              <div className="p-3 space-y-3">
                {/* Node Properties */}
                {selectedNode && <NodePropertiesPanel node={selectedNode} />}
                {/* Element Properties */}
                {selectedElement && <ElementPropertiesPanel element={selectedElement} />}
                {/* Load Panel */}
                {selectedElement && <LoadPanel elementId={selectedElement.id} />}
                {/* Empty State */}
                {!selectedNode && !selectedElement && (
                  <div className="text-center text-muted-foreground text-xs py-8">
                    <p className="text-lg mb-2">🎯</p>
                    <p>Select a node or element to edit its properties.</p>
                    <p className="mt-1">Or use the toolbar to add nodes and elements.</p>
                  </div>
                )}
                {/* Node/Element Lists */}
                <div className="space-y-2 mt-2">
                  {store.nodes.length > 0 && (
                    <div className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">Nodes ({store.nodes.length})</div>
                  )}
                  {store.nodes.map(n => (
                    <div key={n.id} className={`flex items-center gap-2 p-1.5 rounded-md text-xs cursor-pointer hover:bg-muted/50 ${selectedNode?.id === n.id ? 'bg-muted ring-1 ring-primary/20' : ''}`} onClick={() => store.selectNode(n.id)}>
                      <span className={`w-2 h-2 rounded-full ${n.support ? 'bg-red-500' : 'bg-gray-400'}`} />
                      <span className="font-medium w-10">{n.label}</span>
                      <span className="text-muted-foreground">({n.x}, {n.y})</span>
                      {n.support && <Badge variant="outline" className="text-[9px] h-4 px-1">{n.support}</Badge>}
                    </div>
                  ))}
                  {store.elements.length > 0 && (
                    <div className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider mt-2">Elements ({store.elements.length})</div>
                  )}
                  {store.elements.map(e => (
                    <div key={e.id} className={`flex items-center gap-2 p-1.5 rounded-md text-xs cursor-pointer hover:bg-muted/50 ${selectedElement?.id === e.id ? 'bg-muted ring-1 ring-primary/20' : ''}`} onClick={() => store.selectElement(e.id)}>
                      <span className={`w-2 h-2 rounded-sm ${e.material === 'concrete' ? 'bg-amber-500' : 'bg-blue-500'}`} />
                      <span className="font-medium w-10">{e.label}</span>
                      <span className="text-muted-foreground">N{e.nodeI}→N{e.nodeJ}</span>
                      <Badge variant={e.material === 'concrete' ? 'secondary' : 'default'} className="text-[9px] h-4 px-1 ml-auto">
                        {e.material === 'concrete' ? 'RC' : 'S'}
                      </Badge>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {rightPanel === 'results' && (
              <div className="p-3 space-y-3">
                <Tabs value={diagramTab} onValueChange={(v) => setDiagramTab(v as typeof diagramTab)}>
                  <TabsList className="w-full">
                    <TabsTrigger value="BMD" className="text-xs flex-1">BMD</TabsTrigger>
                    <TabsTrigger value="SFD" className="text-xs flex-1">SFD</TabsTrigger>
                    <TabsTrigger value="NFD" className="text-xs flex-1">NFD</TabsTrigger>
                  </TabsList>
                  <TabsContent value="BMD" className="mt-2">
                    <ResultsDiagram type="BMD" height={250} />
                  </TabsContent>
                  <TabsContent value="SFD" className="mt-2">
                    <ResultsDiagram type="SFD" height={250} />
                  </TabsContent>
                  <TabsContent value="NFD" className="mt-2">
                    <ResultsDiagram type="NFD" height={250} />
                  </TabsContent>
                </Tabs>

                {/* Displacement Summary */}
                {store.analysisResults?.isStable && store.analysisResults.displacements.length > 0 && (
                  <Card className="mt-2">
                    <CardHeader className="pb-2 pt-3 px-3">
                      <CardTitle className="text-xs">Node Displacements</CardTitle>
                    </CardHeader>
                    <CardContent className="px-3 pb-3">
                      <div className="overflow-x-auto">
                        <table className="w-full text-[11px]">
                          <thead>
                            <tr className="border-b text-muted-foreground">
                              <th className="text-left p-1">Node</th>
                              <th className="text-right p-1">Δx (mm)</th>
                              <th className="text-right p-1">Δy (mm)</th>
                              <th className="text-right p-1">θ (rad)</th>
                            </tr>
                          </thead>
                          <tbody>
                            {store.analysisResults.displacements.map(d => (
                              <tr key={d.nodeId} className="border-b last:border-0">
                                <td className="p-1 font-medium">N{d.nodeId}</td>
                                <td className="p-1 text-right font-mono">{d.ux.toFixed(3)}</td>
                                <td className="p-1 text-right font-mono">{d.uy.toFixed(3)}</td>
                                <td className="p-1 text-right font-mono">{(d.rotation * 1000).toFixed(4)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </CardContent>
                  </Card>
                )}
              </div>
            )}

            {rightPanel === 'design' && (
              <div className="p-3">
                <DesignOutput />
              </div>
            )}
          </ScrollArea>
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// SUB-PANELS
// ============================================================================

function NodePropertiesPanel({ node }: { node: FrameNode }) {
  const { setSupport, updateNode, removeNode } = useFrameStore();
  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-xs">Node {node.label}</CardTitle>
          <Button variant="ghost" size="sm" className="h-6 w-6 p-0 text-destructive text-xs" onClick={() => removeNode(node.id)}>✕</Button>
        </div>
      </CardHeader>
      <CardContent className="px-3 pb-3 space-y-2">
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-[10px]">X (mm)</Label>
            <Input type="number" className="h-7 text-xs" value={node.x} onChange={e => updateNode(node.id, { x: Number(e.target.value) })} />
          </div>
          <div>
            <Label className="text-[10px]">Y (mm)</Label>
            <Input type="number" className="h-7 text-xs" value={node.y} onChange={e => updateNode(node.id, { y: Number(e.target.value) })} />
          </div>
        </div>
        <div>
          <Label className="text-[10px]">Support Condition</Label>
          <Select value={node.support || 'none'} onValueChange={(v) => setSupport(node.id, v === 'none' ? null : v as SupportType)}>
            <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="none">Free (No Support)</SelectItem>
              <SelectItem value="fixed">Fixed</SelectItem>
              <SelectItem value="hinged">Hinged (Pin)</SelectItem>
              <SelectItem value="roller">Roller</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  );
}

function ElementPropertiesPanel({ element }: { element: FrameElement }) {
  const { updateElement, removeElement } = useFrameStore();
  const sec = element.section;

  const handleMaterialChange = (mat: MaterialType) => {
    if (mat === 'concrete') {
      updateElement(element.id, { material: mat, section: { width: 300, depth: 500, elasticMod: 25000 } });
    } else {
      updateElement(element.id, { material: mat, section: { area: 5000, inertia: 1e8, elasticMod: 200000 } });
    }
  };

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-xs">Element {element.label}</CardTitle>
          <Button variant="ghost" size="sm" className="h-6 w-6 p-0 text-destructive text-xs" onClick={() => removeElement(element.id)}>✕</Button>
        </div>
      </CardHeader>
      <CardContent className="px-3 pb-3 space-y-2">
        <div>
          <Label className="text-[10px]">Material</Label>
          <Select value={element.material} onValueChange={(v) => handleMaterialChange(v as MaterialType)}>
            <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="concrete">Concrete (Reinforced)</SelectItem>
              <SelectItem value="steel">Steel (Structural)</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {element.material === 'concrete' ? (
          <div className="grid grid-cols-3 gap-2">
            <div>
              <Label className="text-[10px]">b (mm)</Label>
              <Input type="number" className="h-7 text-xs" value={sec.width || ''} onChange={e => updateElement(element.id, { section: { ...sec, width: Number(e.target.value) } })} />
            </div>
            <div>
              <Label className="text-[10px]">h (mm)</Label>
              <Input type="number" className="h-7 text-xs" value={sec.depth || ''} onChange={e => updateElement(element.id, { section: { ...sec, depth: Number(e.target.value) } })} />
            </div>
            <div>
              <Label className="text-[10px]">E (MPa)</Label>
              <Input type="number" className="h-7 text-xs" value={sec.elasticMod || ''} onChange={e => updateElement(element.id, { section: { ...sec, elasticMod: Number(e.target.value) } })} />
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            <div>
              <Label className="text-[10px]">A (mm²)</Label>
              <Input type="number" className="h-7 text-xs" value={sec.area || ''} onChange={e => updateElement(element.id, { section: { ...sec, area: Number(e.target.value) } })} />
            </div>
            <div>
              <Label className="text-[10px]">I (mm⁴)</Label>
              <Input type="number" className="h-7 text-xs" value={sec.inertia || ''} onChange={e => updateElement(element.id, { section: { ...sec, inertia: Number(e.target.value) } })} />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function LoadPanel({ elementId }: { elementId: number }) {
  const { addLoad, loads, removeLoad } = useFrameStore();
  const [loadType, setLoadType] = useState<LoadType>('udl');
  const [magnitude, setMagnitude] = useState('-20');
  const [direction, setDirection] = useState<LoadDirection>('perpendicular');
  const [position, setPosition] = useState('0.5');

  const elemLoads = loads.filter(l => l.elementId === elementId);

  const handleAdd = () => {
    addLoad(elementId, loadType, Number(magnitude), direction, loadType === 'udl' ? undefined : Number(position));
  };

  return (
    <Card>
      <CardHeader className="pb-2 pt-3 px-3">
        <CardTitle className="text-xs">Loads ({elemLoads.length})</CardTitle>
      </CardHeader>
      <CardContent className="px-3 pb-3 space-y-2">
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-[10px]">Type</Label>
            <Select value={loadType} onValueChange={(v) => setLoadType(v as LoadType)}>
              <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="udl">UDL (kN/m)</SelectItem>
                <SelectItem value="point">Point (kN)</SelectItem>
                <SelectItem value="moment">Moment (kN.m)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label className="text-[10px]">Direction</Label>
            <Select value={direction} onValueChange={(v) => setDirection(v as LoadDirection)}>
              <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="perpendicular">Perpendicular ↓</SelectItem>
                <SelectItem value="globalY">Global Y ↓</SelectItem>
                <SelectItem value="globalX">Global X →</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <Label className="text-[10px]">Magnitude</Label>
            <Input type="number" className="h-7 text-xs" value={magnitude} onChange={e => setMagnitude(e.target.value)} />
          </div>
          {loadType !== 'udl' && (
            <div>
              <Label className="text-[10px]">Position (0-1)</Label>
              <Input type="number" step="0.1" min="0" max="1" className="h-7 text-xs" value={position} onChange={e => setPosition(e.target.value)} />
            </div>
          )}
        </div>
        <Button size="sm" className="h-7 w-full text-xs" variant="outline" onClick={handleAdd}>+ Add Load</Button>

        {elemLoads.length > 0 && (
          <div className="space-y-1 mt-1">
            {elemLoads.map(l => (
              <div key={l.id} className="flex items-center gap-1 p-1 rounded bg-muted/50 text-[10px]">
                <span className="font-medium">{l.type}</span>
                <span>{Math.abs(l.magnitude)} {l.type === 'udl' ? 'kN/m' : l.type === 'point' ? 'kN' : 'kN.m'}</span>
                <span className="text-muted-foreground">@{(l.position ?? 0.5).toFixed(2)}</span>
                <button className="ml-auto text-destructive hover:underline" onClick={() => removeLoad(l.id)}>✕</button>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}