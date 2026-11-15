#!/usr/bin/env python3
import sys
import re
import pathlib

ARABIC_RE = re.compile(r'[\u0600-\u06FF]')

TARGET_FILES = [
    "app/src/main/java/com/civileg/app/utils/PdfGenerator.kt",
    "app/src/main/java/com/civileg/app/utils/PdfDrawingGenerator.kt",
    "app/src/main/java/com/civileg/app/utils/FrameAnalysisPdfExporter.kt",
    "app/src/main/java/com/civileg/app/utils/PdfExportHelper.kt",
    "app/src/main/java/com/civileg/app/utils/exporters/ProfessionalEnglishPdfReporter.kt",
    "app/src/main/java/com/civileg/app/utils/exporters/SteelWarehouseProPdfExporter.kt",
    "app/src/main/java/com/civileg/app/utils/ElementDxfDrawings.kt",
    "app/src/main/java/com/civileg/app/utils/DxfExporter.kt",
    "app/src/main/java/com/civileg/app/domain/modeler/FrameDxfBuilder.kt",
]

def check_file(path):
    p = pathlib.Path(path)
    if not p.exists():
        return []

    violations = []
    lines = p.read_text(encoding='utf-8').splitlines()
    for idx, line in enumerate(lines, 1):
        stripped = line.strip()
        # Skip comment lines
        if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
            continue

        # In FrameDxfBuilder.kt, allow character mapping table ('أ' to "A")
        if "FrameDxfBuilder.kt" in str(path) and ("to" in line and "'" in line):
            continue

        # Check if line contains Arabic
        if ARABIC_RE.search(line):
            violations.append((idx, line.strip()))

    return violations

def main():
    repo_root = pathlib.Path(__file__).resolve().parent.parent.parent
    total_violations = 0

    for rel_path in TARGET_FILES:
        full_path = repo_root / rel_path
        violations = check_file(full_path)
        if violations:
            print(f"FAIL: {rel_path} contains {len(violations)} Arabic string literal line(s):")
            for line_no, content in violations:
                print(f"  Line {line_no}: {content}")
            total_violations += len(violations)

    if total_violations == 0:
        print("SUCCESS: All export code paths are English-only. Zero Arabic string literals found.")
        sys.exit(0)
    else:
        print(f"\nTOTAL VIOLATIONS: {total_violations}")
        sys.exit(1)

if __name__ == "__main__":
    main()
