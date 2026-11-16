#!/usr/bin/env python3
import sys
import pathlib

def validate_dxf(file_path):
    p = pathlib.Path(file_path)
    if not p.exists():
        print(f"ERROR: DXF file not found: {file_path}")
        return False

    try:
        import ezdxf
    except ImportError:
        print("WARNING: ezdxf Python package not installed. Skipping deep DXF audit (pip install ezdxf).")
        return True

    try:
        doc = ezdxf.readfile(str(p))
    except Exception as e:
        print(f"FAIL: {p.name} failed to load: {e}")
        return False

    # Run ezdxf audit
    auditor = doc.audit()
    if len(auditor.errors) > 0:
        print(f"FAIL: {p.name} audit errors: {auditor.errors}")
        return False

    # Check layers
    layers = [layer.dxf.name for layer in doc.layers]
    print(f"OK: {p.name} loaded successfully. Layers found: {len(layers)}")

    # Check entities
    msp = doc.modelspace()
    entity_count = len(msp)
    print(f"OK: Modelspace entities: {entity_count}")

    return True

def main():
    if len(sys.argv) < 2:
        print("Usage: python dxf_validate.py <dxf_file_or_directory>")
        sys.exit(1)

    target = pathlib.Path(sys.argv[1])
    success = True

    if target.is_dir():
        for dxf in target.rglob("*.dxf"):
            if not validate_dxf(dxf):
                success = False
    else:
        success = validate_dxf(target)

    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
