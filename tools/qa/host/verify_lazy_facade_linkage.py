#!/usr/bin/env python3
"""Fail-closed linkage gate for the synthetic-upgrade QA target.

Runs AFTER both APKs are assembled and BEFORE any emulator launch. The
androidx.test runner (androidx.test.platform.io.TestDirCalculator) reaches
kotlin.LazyKt.lazy through a DIRECT STATIC INVOKE compiled into its bytecode;
this gate inspects the actual rebuilt artifacts (never source assumptions):

  1. the target APK defines kotlin.LazyKt, its multifile part hierarchy
     (exact directed superclass chain, cycle/truncation/duplicate-class
     checked), and the kotlin.Lazy contract interface (interface flag plus
     members);
  2. every facade-hierarchy member's full signature (name + descriptor) has
     parameter/return types that resolve to classes DEFINED in the target
     dexes or to an exact platform allowlist; unknown types FAIL with the
     offending descriptor;
  3. every kotlin.LazyKt method REFERENCE in the instrumentation APK's dexes
     resolves, through legal superclass traversal (constructors are not
     inherited), to a defined PUBLIC STATIC method with matching full
     signature in the target facade hierarchy.

This gate inspects method references and class/method definitions only. It
does not disassemble instructions and claims nothing about invoke sites
beyond the method-reference table. Its companion fixtures are parser-level
structural fixtures; they are not validated to load or run on ART.

Dependency or parser failure refuses (exit 1) with evidence. Exit 0 only when
all checks pass. Requires: pip install androguard==4.1.4 (workflow installs
it; an install failure fails the step before this script runs).
"""
import re
import sys
import zipfile

try:
    from loguru import logger as _loguru_logger
    _loguru_logger.remove()
    from androguard.core.dex import DEX
except Exception as e:
    print('GATE FAIL: parser dependency unavailable: %s: %s' % (type(e).__name__, e))
    sys.exit(1)

FACADE = 'Lkotlin/LazyKt;'
PARTS = ['Lkotlin/LazyKt__LazyJVMKt;', 'Lkotlin/LazyKt__LazyKt;']
LAZY_IFACE = 'Lkotlin/Lazy;'
# Exact Lazy contract: member name + full descriptor, each public abstract
# non-static on a public interface.
LAZY_CONTRACT = [('getValue', '()Ljava/lang/Object;'), ('isInitialized', '()Z')]
EXPECTED_CHAIN = PARTS + ['Ljava/lang/Object;']
# Exact grounded platform allowlist: dex primitives/void plus the root
# supertype. Everything else in a kept signature must be defined in the
# target APK's own dexes. No kotlin.* or java.* blanket exemptions.
PLATFORM_TYPES = frozenset(['V', 'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D', 'Ljava/lang/Object;'])
CONSTRUCTOR_NAMES = ('<init>', '<clinit>')
TYPE_RE = re.compile(r'\[*L[^;\[]*;')

def load_apk_dexes(path):
    dexes = []
    try:
        zf = zipfile.ZipFile(path)
    except Exception as e:
        print('GATE FAIL: cannot open APK %s: %s: %s' % (path, type(e).__name__, e))
        sys.exit(1)
    with zf:
        for n in sorted(zf.namelist()):
            if n.startswith('classes') and n.endswith('.dex'):
                try:
                    dexes.append((n, DEX(zf.read(n))))
                except Exception as e:
                    print('GATE FAIL: parser refused %s!%s: %s: %s' % (path, n, type(e).__name__, e))
                    sys.exit(1)
    if not dexes:
        print('GATE FAIL: no dex files in ' + path)
        sys.exit(1)
    return dexes

def defined_methods(cls):
    out = {}
    for m in cls.get_methods():
        flags = ''
        try:
            flags = m.get_access_flags_string()
        except Exception:
            flags = ''
        out[(m.get_name(), m.get_descriptor())] = flags
    return out

def main():
    if len(sys.argv) != 3:
        print('usage: verify_lazy_facade_linkage.py TARGET_APK INSTRUMENTATION_APK')
        sys.exit(1)
    target, test = sys.argv[1], sys.argv[2]
    t_dexes = load_apk_dexes(target)
    i_dexes = load_apk_dexes(test)
    failures = []

    defined = {}          # class name -> ClassDef (duplicate-checked)
    access_by_class = {}  # class name -> access flags string
    methods_defined = {}  # class name -> {(name, descriptor): flags}
    for dex_name, d in t_dexes:
        for c in d.get_classes():
            name = c.get_name()
            if name in defined:
                failures.append('duplicate class definition: %s in multiple target dexes' % name)
                continue
            defined[name] = c
            try:
                access_by_class[name] = c.get_access_flags_string()
            except Exception:
                access_by_class[name] = ''
            methods_defined[name] = defined_methods(c)

    # 1. facade, parts, contract interface defined with correct kind flags:
    #    facade public non-interface; parts non-interface classes (no public
    #    requirement); Lazy a public interface.
    for want in [FACADE] + PARTS + [LAZY_IFACE]:
        if want not in defined:
            failures.append('target does not define ' + want)
    if LAZY_IFACE in defined:
        fl = access_by_class.get(LAZY_IFACE, '')
        if 'interface' not in fl:
            failures.append(LAZY_IFACE + ' is defined but its access flags lack the interface bit: ' + fl)
        if 'public' not in fl:
            failures.append(LAZY_IFACE + ' must be public (access: ' + fl + ')')
    if FACADE in defined:
        fl = access_by_class.get(FACADE, '')
        if 'interface' in fl:
            failures.append(FACADE + ' must be a class, not an interface')
        if 'public' not in fl:
            failures.append(FACADE + ' must be public (access: ' + fl + ')')
    for part in PARTS:
        if part in defined and 'interface' in access_by_class.get(part, ''):
            failures.append(part + ' must be a class, not an interface')

    # 2. exact directed superclass chain from the facade: cycle/truncation checked
    chain = []
    if FACADE in defined:
        seen = set()
        cur_name = FACADE
        while True:
            if cur_name in seen:
                failures.append('superclass cycle detected at ' + cur_name + ' (chain so far: ' + ' -> '.join(chain) + ')')
                break
            seen.add(cur_name)
            cls = defined.get(cur_name)
            if cls is None:
                if cur_name != 'Ljava/lang/Object;':
                    failures.append('superclass chain truncated: ' + cur_name + ' is not defined in the target dexes (chain so far: ' + ' -> '.join(chain) + ')')
                break
            sup = cls.get_superclassname()
            if not sup:
                break
            chain.append(sup)
            cur_name = sup
        print('facade superclass chain: ' + ' -> '.join([FACADE] + chain))
        if chain != EXPECTED_CHAIN:
            failures.append('facade chain is not the exact directed chain ' + ' -> '.join([FACADE] + EXPECTED_CHAIN) + ' (actual: ' + ' -> '.join([FACADE] + chain) + ')')

    # 3. full-signature descriptor dependency check over the facade hierarchy
    #    and the Lazy contract interface (mapped types such as a renamed
    #    Function0 are fine only while they stay defined in the target).
    hierarchy = [n for n in [FACADE] + PARTS + [LAZY_IFACE] if n in defined]
    for cls_name in hierarchy:
        for (mname, desc), _flags in sorted(methods_defined.get(cls_name, {}).items()):
            for typ in TYPE_RE.findall(desc):
                bare = typ.lstrip('[')
                if bare not in defined and bare not in PLATFORM_TYPES:
                    failures.append('%s member %s%s has descriptor type %s that is neither defined in the target dexes nor in the exact platform allowlist' % (cls_name, mname, desc, typ))
    if LAZY_IFACE in defined:
        iface_members = methods_defined.get(LAZY_IFACE, {})
        for member, desc in LAZY_CONTRACT:
            flags = iface_members.get((member, desc))
            if flags is None:
                failures.append(LAZY_IFACE + ' exact contract member missing: ' + member + desc + ' (defined: ' + '; '.join(sorted(n + d for (n, d) in iface_members)) + ')')
            else:
                if 'public' not in flags or 'abstract' not in flags or 'static' in flags:
                    failures.append(LAZY_IFACE + ' member ' + member + desc + ' must be public abstract non-static (access: ' + flags + ')')

    # 4. instrumentation method references resolve via legal traversal
    refs = []
    for dex_name, d in i_dexes:
        for m in d.get_methods():
            if m.get_class_name() == FACADE:
                refs.append((dex_name, m.get_name(), m.get_descriptor()))
    if not refs:
        failures.append('instrumentation dex has no method reference to ' + FACADE + ' (gate cannot prove the runner path)')
    for dex_name, mname, desc in refs:
        match = None
        if mname in CONSTRUCTOR_NAMES:
            scope = [FACADE]  # constructors are never inherited
        else:
            scope = [FACADE] + chain
        for cls_name in scope:
            hit = methods_defined.get(cls_name, {}).get((mname, desc))
            if hit is not None:
                match = (cls_name, hit)
                break
        if match is None:
            failures.append('instrumentation %s references LazyKt.%s%s but no defined method with that full signature exists in the facade hierarchy' % (dex_name, mname, desc))
            continue
        owner, flags = match
        if 'public' not in flags or 'static' not in flags:
            failures.append('instrumentation %s references LazyKt.%s%s resolved on %s but the defined method is not public static (access: %s)' % (dex_name, mname, desc, owner, flags or 'unknown'))
        else:
            print('resolved: LazyKt.%s%s -> %s (public static)' % (mname, desc, owner))
    print('instrumentation LazyKt method references checked: %d' % len(refs))

    if failures:
        print('GATE FAIL (%d):' % len(failures))
        for f in failures:
            print('  - ' + f)
        sys.exit(1)
    print('GATE PASS: facade hierarchy defined with exact directed chain, full-signature descriptor dependencies resolve, instrumentation method references resolve to public static definitions')
    sys.exit(0)

main()
