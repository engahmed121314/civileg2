'use client';

import { useFrameStore } from '@/lib/structural-analysis/store';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export default function DesignOutput() {
  const { concreteResults, steelResults, elements } = useFrameStore();

  const hasConcrete = elements.some(e => e.material === 'concrete');
  const hasSteel = elements.some(e => e.material === 'steel');

  return (
    <div className="space-y-4">
      {/* Concrete Design */}
      {hasConcrete && concreteResults && concreteResults.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Concrete Reinforcement Design (ACI/ECP)</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-muted/50">
                    <th className="text-left p-2 font-medium">Element</th>
                    <th className="text-center p-2 font-medium">Type</th>
                    <th className="text-right p-2 font-medium">Mu (kN.m)</th>
                    <th className="text-right p-2 font-medium">Vu (kN)</th>
                    <th className="text-right p-2 font-medium">Pu (kN)</th>
                    <th className="text-center p-2 font-medium">Main Bars</th>
                    <th className="text-center p-2 font-medium">Stirrups</th>
                    <th className="text-center p-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {concreteResults.map(r => (
                    <tr key={r.elementId} className="border-b last:border-0">
                      <td className="p-2 font-medium">{r.label}</td>
                      <td className="p-2 text-center">
                        <Badge variant={r.elementType === 'column' ? 'default' : 'secondary'} className="text-[10px]">
                          {r.elementType === 'column' ? 'Column' : 'Beam'}
                        </Badge>
                      </td>
                      <td className="p-2 text-right">{r.Mu.toFixed(1)}</td>
                      <td className="p-2 text-right">{r.Vu.toFixed(1)}</td>
                      <td className="p-2 text-right">{r.Pu.toFixed(1)}</td>
                      <td className="p-2 text-center font-mono">{r.bars}</td>
                      <td className="p-2 text-center font-mono">{r.stirrups}</td>
                      <td className="p-2 text-center">
                        <Badge variant={r.isSafe ? 'default' : 'destructive'} className="text-[10px]">
                          {r.isSafe ? `✓ ${(r.utilizationRatio * 100).toFixed(0)}%` : '✗ FAIL'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Steel Design */}
      {hasSteel && steelResults && steelResults.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Steel Section Design (AISC/ECP)</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-muted/50">
                    <th className="text-left p-2 font-medium">Element</th>
                    <th className="text-center p-2 font-medium">Section</th>
                    <th className="text-right p-2 font-medium">Pu (kN)</th>
                    <th className="text-right p-2 font-medium">Mu (kN.m)</th>
                    <th className="text-right p-2 font-medium">Vu (kN)</th>
                    <th className="text-right p-2 font-medium">ΦPn (kN)</th>
                    <th className="text-right p-2 font-medium">ΦMn (kN.m)</th>
                    <th className="text-center p-2 font-medium">U.R.</th>
                    <th className="text-center p-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {steelResults.map(r => (
                    <tr key={r.elementId} className="border-b last:border-0">
                      <td className="p-2 font-medium">{r.label}</td>
                      <td className="p-2 text-center font-mono font-medium">{r.selectedSection}</td>
                      <td className="p-2 text-right">{r.Pu.toFixed(1)}</td>
                      <td className="p-2 text-right">{r.Mu.toFixed(1)}</td>
                      <td className="p-2 text-right">{r.Vu.toFixed(1)}</td>
                      <td className="p-2 text-right">{r.axialCapacity.toFixed(0)}</td>
                      <td className="p-2 text-right">{r.flexuralCapacity.toFixed(1)}</td>
                      <td className="p-2 text-center font-medium">{r.utilizationRatio.toFixed(2)}</td>
                      <td className="p-2 text-center">
                        <Badge variant={r.isSafe ? 'default' : 'destructive'} className="text-[10px]">
                          {r.isSafe ? '✓ PASS' : '✗ FAIL'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* No design results */}
      {(!concreteResults || concreteResults.length === 0) && (!steelResults || steelResults.length === 0) && (
        <div className="text-center text-muted-foreground text-sm py-8 border rounded-lg bg-muted/10">
          Run analysis to see design output
        </div>
      )}
    </div>
  );
}