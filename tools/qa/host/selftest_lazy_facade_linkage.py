#!/usr/bin/env python3
"""Focused positive/negative fixtures for verify_lazy_facade_linkage.py.

Builds minimal dex fixture APKs (make_fixture_dex.py) covering: inherited
static resolution through the exact facade chain, access-flag rejection,
unknown descriptor type rejection, truncated and cyclic chains, duplicate
class definitions, absent references, and parser refusal on a corrupt dex.
Broken-artifact refusal alone is not sufficient evidence; the positive
fixture must PASS. Exits 1 if any expectation is unmet.
"""
import os
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'fixtures'))
from make_fixture_dex import DexBuilder, ACC_PUBLIC, ACC_STATIC, ACC_INTERFACE, ACC_ABSTRACT

GATE = os.path.join(HERE, 'verify_lazy_facade_linkage.py')
FACADE = 'Lkotlin/LazyKt;'
P1 = 'Lkotlin/LazyKt__LazyJVMKt;'
P2 = 'Lkotlin/LazyKt__LazyKt;'
LAZY = 'Lkotlin/Lazy;'
# Real mapped path: in the rebuilt QA universe Function0 stays renamed by R8
# (the broken instrumentation references it as Llf0;), while the kept facade
# hierarchy and Lazy interface retain their real names.
F0 = 'Llf0;'
OBJ = 'Ljava/lang/Object;'

def base_target(b, lazy_access=ACC_PUBLIC | ACC_STATIC, super_of_p1=P2, super_of_p2=OBJ,
                super_of_facade=P1, lazy_members=None, extra=None):
    if lazy_members is None:
        lazy_members = [('getValue', OBJ, [], ACC_PUBLIC | ACC_ABSTRACT, False),
                        ('isInitialized', 'Z', [], ACC_PUBLIC | ACC_ABSTRACT, False)]
    b.add_class(LAZY, OBJ, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, lazy_members)
    b.add_class(F0, OBJ, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
                [('invoke', OBJ, [], ACC_PUBLIC | ACC_ABSTRACT, False)])
    b.add_class(P2, super_of_p2, ACC_PUBLIC,
                [('lazyOf', LAZY, [OBJ], ACC_PUBLIC | ACC_STATIC, True)])
    b.add_class(P1, super_of_p1, ACC_PUBLIC,
                [('lazy', LAZY, [F0], lazy_access, True)])
    b.add_class(FACADE, super_of_facade, ACC_PUBLIC, [])
    if extra:
        extra(b)

def instrumentation(b, refs):
    b.add_class('Lqa/Runner;', OBJ, ACC_PUBLIC, [])
    for (name, ret, params) in refs:
        b.add_ref(FACADE, name, ret, params)

def write_apk(path, dexes):
    with zipfile.ZipFile(path, 'w') as z:
        for i, raw in enumerate(dexes):
            z.writestr('classes%s.dex' % ('' if i == 0 else str(i + 1)), raw)

def run_gate(target, test):
    return subprocess.run([sys.executable, GATE, target, test],
                          capture_output=True, text=True)

def main():
    results = []
    tmp = tempfile.mkdtemp(prefix='lazygate-fixtures-')

    def case(label, expect_pass, target_dexes, refs, must_mention=None, target_extra=None,
             lazy_access=ACC_PUBLIC | ACC_STATIC, super_of_p1=P2, super_of_p2=OBJ,
             super_of_facade=P1, lazy_members=None):
        tb = DexBuilder(); base_target(tb, lazy_access, super_of_p1, super_of_p2, super_of_facade, lazy_members, target_extra)
        ib = DexBuilder(); instrumentation(ib, refs)
        tp = os.path.join(tmp, label + '-target.apk'); ip = os.path.join(tmp, label + '-test.apk')
        write_apk(tp, target_dexes(tb))
        write_apk(ip, [ib.build()])
        r = run_gate(tp, ip)
        ok = (r.returncode == 0) if expect_pass else (r.returncode != 0)
        detail = (r.stdout + r.stderr).strip().splitlines()
        mention_ok = True
        if must_mention:
            mention_ok = any(must_mention in line for line in detail)
        results.append((label, expect_pass, ok and mention_ok, detail[-1] if detail else '(no output)'))

    # POSITIVE: inherited static through the exact chain; second ref resolves on part P2.
    case('positive-inherited-static', True,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0]), ('lazyOf', LAZY, [OBJ])])
    # NEGATIVE: defined but not static -> access rejection.
    case('negative-nonstatic', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='not public static',
         lazy_access=ACC_PUBLIC)
    # NEGATIVE: chain truncated above P1.
    case('negative-truncated-chain', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='truncated',
         super_of_p1='Lkotlin/Gone;')
    # NEGATIVE: reference with no matching defined member.
    case('negative-absent-reference', False,
         lambda tb: [tb.build()],
         [('nope', 'V', [])],
         must_mention='no defined method')
    # NEGATIVE: duplicate facade class across two dexes.
    def dup(tb):
        first = tb.build()
        b2 = DexBuilder(); b2.add_class(FACADE, OBJ, ACC_PUBLIC, [])
        return [first, b2.build()]
    case('negative-duplicate-class', False, dup,
         [('lazy', LAZY, [F0])],
         must_mention='duplicate class definition')

    # NEGATIVE: facade member with an undefined descriptor type (hierarchy member).
    def bad_member(tb):
        # attach a static member with an undefined descriptor type directly to
        # the facade (checked hierarchy)
        facade = next(c for c in tb.classes if c['name'] == FACADE)
        facade['methods'].append(('bad', 'V', ['Lx/Missing;'], ACC_PUBLIC | ACC_STATIC, True))
    case('negative-unknown-hierarchy-type', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='Lx/Missing;',
         target_extra=bad_member)

    # NEGATIVE: superclass cycle (P1 <-> P2).
    case('negative-cycle', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='cycle',
         super_of_p2=P1)
    # NEGATIVE: reversed part order (facade -> P2 -> P1 -> Object).
    case('negative-reversed-chain', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='exact directed chain',
         super_of_facade=P2, super_of_p2=P1, super_of_p1=OBJ)
    # NEGATIVE: Lazy member with the wrong descriptor.
    case('negative-lazy-wrong-descriptor', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='exact contract member missing',
         lazy_members=[('getValue', LAZY, [], ACC_PUBLIC | ACC_ABSTRACT, False),
                       ('isInitialized', 'Z', [], ACC_PUBLIC | ACC_ABSTRACT, False)])
    # NEGATIVE: Lazy member present with exact descriptor but not abstract.
    case('negative-lazy-nonabstract', False,
         lambda tb: [tb.build()],
         [('lazy', LAZY, [F0])],
         must_mention='public abstract non-static',
         lazy_members=[('getValue', OBJ, [], ACC_PUBLIC | ACC_ABSTRACT, False),
                       ('isInitialized', 'Z', [], ACC_PUBLIC, False)])
    # NEGATIVE: parser refusal on a corrupt dex.
    corrupt_t = os.path.join(tmp, 'corrupt-target.apk')
    corrupt_i = os.path.join(tmp, 'corrupt-test.apk')
    write_apk(corrupt_t, [b'not a dex at all'])
    ib = DexBuilder(); instrumentation(ib, [('lazy', LAZY, [F0])])
    write_apk(corrupt_i, [ib.build()])
    r = run_gate(corrupt_t, corrupt_i)
    results.append(('negative-parser-refusal', False, r.returncode != 0 and 'parser refused' in (r.stdout + r.stderr),
                    (r.stdout + r.stderr).strip().splitlines()[-1] if (r.stdout + r.stderr).strip() else '(no output)'))

    failures = 0
    for label, expect_pass, ok, last in results:
        status = 'ok' if ok else 'UNMET'
        if not ok:
            failures += 1
        print('%s %-36s expect=%s last=%s' % (status, label, 'pass' if expect_pass else 'fail', last[:120]))
    if failures:
        print('SELFTEST FAIL: %d expectation(s) unmet' % failures)
        sys.exit(1)
    print('SELFTEST PASS: %d focused fixtures behaved as expected' % len(results))
    sys.exit(0)

main()
