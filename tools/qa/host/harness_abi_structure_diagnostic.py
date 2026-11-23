#!/usr/bin/env python3
"""Conservative structural DIAGNOSTIC for the synthetic-upgrade QA harness ABI.

This is NOT a linkage gate, NOT a legal resolver and NOT a boundary proof.
Its pass never certifies linkage, never substitutes for the native tests, and
must never block or suppress native evidence. Acceptance for the synthetic QA
target is the fail-closed Lazy facade gate (verify_lazy_facade_linkage.py)
plus the unchanged native tests under pipefail; this script only records
structural observations about the rebuilt APKs against the finite keep list
in tools/qa/qa-harness-abi-keep.pro, for review alongside native results.

Known limits (read before trusting any line of output):
  * member flags/opcodes are discarded: visibility, static and abstract are
    not verified; a private or instance member wrongly satisfies a match;
  * supertype traversal is a generic DFS over class headers, not JVM/ART
    resolution: no interface-default selection, no package-private rules, no
    overload resolution;
  * keep-line parsing is whitespace and nested-name fragile ('.' vs '$');
  * the platform allowlist (java/, javax/, android/, dalvik/) is a blanket
    prefix assumption, not a verified bootclasspath;
  * instrumentation references whose owners are OUTSIDE the keep list are
    not examined (no test-union or new-boundary check), and nothing here
    proves override completeness;
  * no focused positive fixtures exercise this script, so a silently broken
    check would report PASS.

Always exits 0. Observations are printed with the DIAGNOSTIC prefix; an
anomaly is evidence for review, never a build verdict. Parser/dependency
failure prints a DIAGNOSTIC REFUSAL line and still exits 0.
"""
import re
import sys
import zipfile

try:
    from loguru import logger as _loguru_logger
    _loguru_logger.remove()
    from androguard.core.dex import DEX
    PARSER_OK = True
except Exception as e:
    print('DIAGNOSTIC REFUSAL: parser dependency unavailable: %s: %s' % (type(e).__name__, e))
    print('DIAGNOSTIC: no observations produced; native tests remain the acceptance evidence')
    sys.exit(0)

PLATFORM_PREFIXES = ('java/', 'javax/', 'android/', 'dalvik/')
CONSTRUCTOR_NAMES = ('<init>', '<clinit>')
KEEP_RE = re.compile(r'^-keep class ([\w.$]+)(?:\s*\{\s*(.+?)\s*;\s*\})?\s*(?:#.*)?$')

def java_to_desc(t, known_classes):
    t = t.strip()
    arr = ''
    while t.endswith('[]'):
        arr += '['
        t = t[:-2].strip()
    prim = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S',
            'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}
    if t in prim:
        return arr + prim[t]
    cands = [t.replace('.', '/')]
    parts = t.split('.')
    for i in range(len(parts) - 1, 0, -1):
        cands.append('/'.join(parts[:i]) + '/' + '$'.join(parts[i:]))
    for c in cands:
        if 'L' + c + ';' in known_classes:
            return arr + 'L' + c + ';'
    return arr + 'L' + t.replace('.', '/') + ';'

def parse_sig(owner, sig, known_classes):
    sig = sig.strip()
    if '(' in sig:
        ret_name, params = sig.split('(', 1)
        params = params.rsplit(')', 1)[0]
        pdesc = ''.join(java_to_desc(p, known_classes) for p in params.split(',') if p.strip())
        ret_name = ret_name.strip()
        simple_owner = owner.split('.')[-1].replace('$', '.')
        if ret_name == simple_owner or ret_name == owner.replace('$', '.'):
            return ('m', '<init>', '(' + pdesc + ')V')
        ret, name = ret_name.rsplit(' ', 1)
        return ('m', name, '(' + pdesc + ')' + java_to_desc(ret, known_classes))
    ftype, fname = sig.rsplit(' ', 1)
    return ('f', fname, java_to_desc(ftype, known_classes))

def desc_types(desc):
    return re.findall(r'L[^;]*;', desc)

def load_dexes(path):
    dexes = []
    try:
        with zipfile.ZipFile(path) as zf:
            for n in sorted(zf.namelist()):
                if n.startswith('classes') and n.endswith('.dex'):
                    dexes.append((n, DEX(zf.read(n))))
    except Exception as e:
        print('DIAGNOSTIC REFUSAL: cannot parse %s: %s: %s' % (path, type(e).__name__, e))
        return None
    if not dexes:
        print('DIAGNOSTIC REFUSAL: no dex files in ' + path)
        return None
    return dexes

def index_defs(dexes):
    classes = {}
    for (_n, d) in dexes:
        for c in d.get_classes():
            name = c.get_name()
            if name in classes:
                print('DIAGNOSTIC OBSERVATION: duplicate class definition ' + name)
                continue
            classes[name] = {
                'super': c.get_superclassname(),
                'interfaces': list(c.get_interfaces()),
                'methods': {(m.get_name(), m.get_descriptor()) for m in c.get_methods()},
                'fields': {(f.get_name(), f.get_descriptor()) for f in c.get_fields()},
            }
    return classes

def resolve_member(classes, owner_internal, kind, name, desc):
    seen, stack = set(), [owner_internal]
    ctor = name in CONSTRUCTOR_NAMES
    while stack:
        c = stack.pop()
        if not c or c in seen or c not in classes:
            continue
        seen.add(c)
        if (name, desc) in classes[c]['methods' if kind == 'm' else 'fields']:
            return c
        if ctor:
            return None
        stack.append(classes[c]['super'])
        stack.extend(classes[c]['interfaces'])
    return None

def main():
    if len(sys.argv) != 4:
        print('usage: harness_abi_structure_diagnostic.py TARGET_APK INSTRUMENTATION_APK KEEP_FILE')
        sys.exit(0)
    target_path, inst_path, keep_path = sys.argv[1:4]
    print('DIAGNOSTIC: structural observations only - not linkage certification;')
    print('DIAGNOSTIC: acceptance is the Lazy fail-closed gate plus unchanged native tests')
    target_dexes = load_dexes(target_path)
    inst_dexes = load_dexes(inst_path)
    if target_dexes is None or inst_dexes is None:
        sys.exit(0)
    target_defs = index_defs(target_dexes)

    keeps = []
    try:
        raw_lines = open(keep_path).read().splitlines()
    except Exception as e:
        print('DIAGNOSTIC REFUSAL: cannot read keep file %s: %s' % (keep_path, e))
        sys.exit(0)
    known = set(target_defs)
    for raw in raw_lines:
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        m = KEEP_RE.match(line)
        if not m:
            print('DIAGNOSTIC OBSERVATION: unparsable keep line: ' + line)
            continue
        owner = m.group(1).replace('.', '/')
        owner_internal = 'L' + owner + ';'
        if m.group(2):
            kind, name, desc = parse_sig(m.group(1), m.group(2), known)
            keeps.append((owner_internal, kind, name, desc))
        else:
            keeps.append((owner_internal, None, None, None))

    anomalies = 0
    kept_owners = {k[0] for k in keeps}
    for (owner, kind, name, desc) in keeps:
        if owner not in target_defs:
            print('DIAGNOSTIC OBSERVATION: kept class absent from target: ' + owner)
            anomalies += 1
            continue
        if kind is None:
            continue
        where = resolve_member(target_defs, owner, kind, name, desc)
        if where is None:
            print('DIAGNOSTIC OBSERVATION: kept member not structurally resolved: %s %s%s' % (owner, name, desc))
            anomalies += 1
            continue
        for t in desc_types(desc):
            if t[1:-1].startswith(PLATFORM_PREFIXES):
                continue
            if t not in target_defs:
                print('DIAGNOSTIC OBSERVATION: descriptor type not target-defined: %s used by %s %s%s' % (t, owner, name, desc))
                anomalies += 1
    checked_refs = 0
    for (_n, d) in inst_dexes:
        for pool, kind in ((d.get_methods(), 'm'), (d.get_fields(), 'f')):
            for ref in pool:
                owner = ref.get_class_name()
                if owner not in kept_owners:
                    continue
                if resolve_member(target_defs, owner, kind, ref.get_name(), ref.get_descriptor()) is None:
                    print('DIAGNOSTIC OBSERVATION: instrumentation reference unresolved: %s %s%s (%s)' % (
                        owner, ref.get_name(), ref.get_descriptor(), kind))
                    anomalies += 1
                checked_refs += 1
    print('DIAGNOSTIC SUMMARY: %d kept classes, %d member keeps, %d instrumentation references examined, %d structural anomalies'
          % (len(kept_owners), sum(1 for k in keeps if k[1] is not None), checked_refs, anomalies))
    print('DIAGNOSTIC: structural pass/fail is informational only and never gates the build')
    sys.exit(0)

if __name__ == '__main__':
    main()
