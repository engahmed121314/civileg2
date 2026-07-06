---
Task ID: 1
Agent: Main Agent
Task: Implement Frame Analysis & Design feature for civileg2

Work Log:
- Explored project structure, understood MVVM + Compose + Room architecture
- Created domain/entities/FrameEntities.kt (FrameNode, FrameMember, SupportType, loads, results, diagrams)
- Created domain/calculations/FrameAnalysisEngine.kt (stiffness matrix method solver with Gauss elimination)
- Created domain/calculations/ConcreteFrameDesign.kt (ECP 203/ACI 318 flexure + shear design)
- Created domain/calculations/SteelFrameDesign.kt (optimal section selection from SteelTables library)
- Created viewmodel/FrameAnalysisViewModel.kt (Hilt ViewModel with LiveData, templates, PDF inputs)
- Created ui/compose/screens/FrameDrawingCanvas.kt (interactive canvas with frame, supports, loads, BMD/SFD/AFD)
- Created ui/compose/screens/FrameAnalysisScreen.kt (5-tab screen: Drawing, Nodes, Members, Loads, Results)
- Created utils/FrameAnalysisPdfExporter.kt (Arabic PDF with iText, diagrams as bitmap)
- Added ic_frame.xml drawable vector
- Added FrameAnalysis route to Screen.kt
- Added FrameAnalysis to HomeScreen module list
- Added FrameAnalysis composable route in MainActivity.kt
- Added PDF export button to FrameAnalysisScreen top bar

Stage Summary:
- Complete frame analysis module implemented with 8 new files
- Stiffness matrix method supports 2D frames with fixed/pin/roller supports
- UDL, point loads, moments on members; nodal loads (Fx, Fy, Mz)
- Interactive BMD/SFD/AFD diagrams with member tap selection
- Conditional concrete reinforcement design (ECP 203/ACI 318) or steel section optimization (AISC/SBC)
- PDF export with Arabic support via NotoNaskhArabic fonts
- Fully integrated into app navigation from HomeScreen