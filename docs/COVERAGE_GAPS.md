# Coverage gaps (JaCoCo, `freeDebug` unit tests)

Baseline: commit `7c8f042`, generated 2026-11-19. Scope: JVM unit tests of the
`free` flavor only. Instrumented tests and the separate `:core:calculations`
module are **not** included in these numbers.

## Reproduce

```powershell
.\gradlew.bat :app:testFreeDebugUnitTest :app:jacocoTestReport --no-daemon
```

```bash
./gradlew :app:testFreeDebugUnitTest :app:jacocoTestReport --no-daemon
```

XML: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`
HTML: `app/build/reports/jacoco/jacocoTestReport/html/index.html`

The CI `test` job runs the same report task and uploads the directory as the
`coverage-report` artifact (14-day retention). There is **no threshold gate yet**;
this batch establishes the report and the gap list only.

## Snapshot

| Scope | Instructions | Branches | Lines |
|---|---:|---:|---:|
| Whole `:app` | 19.4% | 11.4% | 27.3% |
| `domain/**` (26 packages) | 63.2% | 46.2% | — |
| `domain/calculations/**` (17 packages) | **66.0%** | **48.4%** | — |

T1 acceptance is **≥ 70% instructions on `domain/calculations`**: the current
66.0% (126,168 / 191,079) is 4.0 points short, and branch coverage (48.4%) is
the larger gap. `domain/repository` has no counters (interface-only package);
`domain/calculations/slab` has no branch counter.

**Update 2026-11-19 (Phase 1.3, batch 1):** after golden vectors for
`ModelCheckEngine`, that class is 685/702 instructions (97.6%); `domain/**` is
63.6% instructions / 46.7% branches; the app suite is 143 XML / 1,935 tests.
The package table below remains the `7c8f042` baseline; `domain/modelcheck` is no
longer a zero-coverage package.

## Domain packages, worst first

| Package | Instructions | Branches | Missed instructions |
|---|---:|---:|---:|
| `domain/repository` | n/a | n/a | 0 |
| `domain/modelcheck` | 0% | 0% | 984 |
| `domain/calculations/slab` | 0% | n/a | 222 |
| `domain/editor` | 0% | 0% | 987 |
| `domain/usecases` | 40.4% | 39% | 3276 |
| `domain/entities` | 44.1% | 27.3% | 14106 |
| `domain/calculations/utils` | 47% | 25% | 294 |
| `domain/drawing` | 57.1% | 36.6% | 1566 |
| `domain/calculations/aci` | 61.5% | 39.1% | 16761 |
| `domain/calculations/sbc` | 61.8% | 40.5% | 17816 |
| `domain/calculations/ecp` | 64.1% | 44.4% | 18357 |
| `domain/calculations` | 67.2% | 64.6% | 8573 |
| `domain/calculations/base` | 79.1% | 37.5% | 753 |
| `domain/calculations/settlement` | 81.1% | 69.1% | 260 |
| `domain/modeler` | 83% | 67.6% | 1238 |
| `domain/calculations/base/posttensioned` | 83.5% | 67.2% | 1095 |
| `domain/calculations/compositebeam` | 84.3% | 67.1% | 249 |
| `domain/base` | 90.4% | n/a | 7 |
| `domain/calculations/deepbeam` | 91.6% | 73.5% | 135 |
| `domain` | 92.6% | 50% | 176 |
| `domain/calculations/pilegroup` | 93.3% | 75% | 110 |
| `domain/calculations/fireresistance` | 93.4% | 77.2% | 126 |
| `domain/calculations/column` | 93.7% | 52.6% | 57 |
| `domain/calculations/wallopenings` | 96.6% | 50% | 46 |
| `domain/calculations/soilstructure` | 96.8% | 62% | 46 |
| `domain/calculations/beam` | 98.8% | 83.3% | 11 |

## Largest uncovered classes (instructions < 50%)

| Class | Instructions | Missed instructions |
|---|---:|---:|
| `domain/calculations/BeamDesignEngine` | 15.9% | 4871 |
| `domain/calculations/aci/AISCSteelDesignEngine` | 32.9% | 4860 |
| `domain/calculations/ecp/ECPAdvancedBeam` | 14.3% | 4860 |
| `domain/calculations/sbc/SBCSteelDesignEngine` | 25.7% | 4658 |
| `domain/calculations/aci/ACIAdvancedSlab` | 32% | 4473 |
| `domain/calculations/sbc/SBCAdvancedBeam` | 27.3% | 4340 |
| `domain/calculations/sbc/SBCAdvancedSlab` | 38.5% | 3329 |
| `domain/calculations/ecp/ECPAdvancedColumn` | 31.6% | 2700 |
| `domain/calculations/aci/ACIPileFoundation` | 2.5% | 2336 |
| `domain/calculations/ecp/SteelBasePlateDesign` | 48% | 2299 |
| `domain/usecases/CalculateElementBoq` | 25% | 1986 |
| `domain/entities/SteelEntitiesKt` | 13.7% | 1971 |
| `domain/calculations/ecp/ECPAdvancedSlab` | 34.9% | 1668 |
| `domain/calculations/ecp/ECPBeam` | 48.1% | 1021 |
| `domain/calculations/sbc/SBCJoistSlab` | 0% | 979 |

## Priority for Phase 1.3 (golden vectors)

Pick **one engine per batch** (§9.1). **Done:** `ModelCheckEngine` (golden
vector + boundaries, 10 tests; see `ModelCheckEngineTest.kt`). Remaining
confirmed zero-coverage candidates: `SBCJoistSlab` (0/979 instructions),
`SlabDrawingPackageBuilder` (0/222). The name-based audit in
`AGENT_MASTER_PLAN.md` §2.4 is stale for `ACIJoistSlab` (187/192 covered via
`HordiSlabViewModelTest`) and `BeamDesignEnginePart2` (1583/2568 covered);
`PostTensionedDesign` is an interface (0/0) exercised through its implementation
and `PostTensionedTest`. Always use the report, not the name heuristic.

R4 applies: before any new/changed formula, record clause + source + date in
`docs/CLAUSE_LOG.md` (currently missing) and cite it in code. Do not treat this
gap list as a verification of any engineering formula.

## Follow-ups (not in this batch)

- Convert the report into a ratcheting CI gate (Q2) so coverage cannot drop.
- Add `:core:calculations` coverage to the same report once its module has a
  JaCoCo task; today its tests write to `core/calculations/build/test-results/testDebugUnitTest`.
