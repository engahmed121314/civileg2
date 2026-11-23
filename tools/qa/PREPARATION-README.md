# Disposable minified upgrade QA preparation

Status: LOCAL PREPARATION ONLY. No configured release build, emulator, upgrade, Frame entry or minified instrumentation run has executed. The init script and Android test are not compiled. Do not report them as proof.

## Prepared

- Explicit opt-in Gradle init for AGP9.5.2. It preserves release minify/shrink=true and debuggable=false, selects release instrumentation, and uses only the disposable default debug signer. Version code9 and synthetic suffix are test-only, not a production version decision.
- Four source-produced pre-shrink JSON fixtures, with actual typed snapshot `record` wrappers. The long-term fixture covers all nine kept nested input model/enum classes, 24 calculation stages, real adopted bars and explicit thermal state. It was generated with the existing standalone source compiler, not a configured Android release build.
- Android instrumentation assertions for reading those fixtures in the actual minified target, preserving the stored strings/fingerprint, decoding/re-encoding all fields without loss, and retaining exact source values/holds. No new keep rules are proposed.

Long-term fixture revision: 009b62ba5bd5c2ab20fcb57443564c7ff8e7d57c901c6b8646af2937209f465e. Stage21 is deltaAfter=4.39733mm; the source text has display spacing. Input end60months, installation6months, thermalTotal1.5mm/after0.5mm.

## Build route still to execute

1. Finish exact104 native proof. Create a fresh local UI child on current104 and cherry-pick the unpublished UI change, without rewriting its old local history. Run that child's full CI and inspect actual Save dialog pixels before claiming UI completion.
2. Add these QA-only files under tools/qa on a later isolated branch. Put frozen fixtures under tools/qa/androidTest/assets/hordi-source. They are test-APK assets, not app assets. The instrumentation reads its own context's assets.
3. Apply init explicitly with -I and -PcivilegSyntheticQa=true plus -Dcivileg.syntheticQa.root pointing at this checkout. Build assembleFreeRelease and assembleFreeReleaseAndroidTest; verifySyntheticQaContract checks the real release flags.
4. Inspect the actual built manifests/certificates, mapping and APK contents. Both candidate and actual published old APK must use the same disposable signer for this experiment. Preserve original old APK hash/certificate separately. Never call the existing release workflow, which publishes.
5. The release remains non-debuggable. Do not try run-as and do not make it debuggable just to reach its database. Use only a newly created named AOSP/google_apis disposable emulator that supports adb root, with a strict emulator serial and ro.kernel.qemu=1 check. Disable network. Never use a real phone or an existing personal emulator.

## Actual old-to-candidate preservation route still to implement

- Verify old published APK SHA256 8af5940b490e3db76b12c1a42c00defe93ac4b39246adef398387f56b6236729, packagecom.civileg.app/code8/name1.8.0. Re-sign a copy with the candidate's disposable debug signer, not the original file or any production key.
- Install and launch the actual re-signed old app. Its real Room creation must occur before fixture injection. Read the actual SQLite user_version and table/identity metadata; do not assume14. If it is not14, stop and plan the actual migration chain rather than fabricating a source database.
- Force-stop it, then pull the database AND any -wal/-shm sidecars while no process is writing. A lone main file may miss the actual schema/rows. Open/backup the coherent set with host SQLite before modifying a copied fixture.
- Actual production DB name is civil_eg_database. The production Hilt AppModule registers14to15. The unused AppDatabase.getDatabase convenience builder currently does not; no production callers were found. QA must exercise actual MainActivity/Hilt, not silently substitute that convenience builder.
- Preserve a fresh-old14 case exactly, adding only explicit synthetic sentinel project/design/Frame rows, dated/unknown-date values, child inspection links, an orphan-null link and a deliberately advanced/deleted design sequence ID. Keep original tables, Room identity, schema and other contents.
- A separate historical8to9-derived nullable-createdAt case must be explicitly labeled as a modified historical-shape fixture, not a valid fresh14 schema or a real executed8to9 upgrade. Preserve links/indexes/sequence when rebuilding that test table.
- Push only the seeded main DB after removing stale sidecars in this disposable app directory. Restore the original app UID/GID, permissions and SELinux labels. Do not change global security settings.
- Install candidate with adb install-r, same package and disposable signer. Do NOT use connectedAndroidTest as the upgrade executor; it can uninstall/reset the target. No uninstall or pm clear between old and candidate within a case. Independent cases may start fresh disposable state.
- Launch the actual minified MainActivity so production Hilt/Room migrations execute. Verify user_version15, every sentinel value/ID, old quantities taggedLEGACY_UNVERIFIED, null historical date normalized0/unknown, child links, nullable links, index and next sequence. Verify no destructive fallback/data loss.
- Install the instrumentation APK manually and call am instrument for the chosen QA classes. Retain actual results, logcat, manifest/certificate/config reports, screenshots and pulled migrated DB.
- Drive actual Hordi form calculation and Save dialog through the real app UI. Explicit synthetic project/name, no project1. Verify stored input/result strings, fingerprint and quantityBasisUNKNOWN match the actual completed calculation, not a reconstructed DTO. Then open actual Frame Analysis and retain crash-free entry pixels/logs. These UI/upgrade assertions are not yet written in this preparation.

## Boundaries

No production signer, user device, real data, release publishing, integrity bypass, security flag change, new reflection keep rules or overwrite of unrelated branches. No current-head native proof is inherited from parent branches or standalone/JVM experiments.
