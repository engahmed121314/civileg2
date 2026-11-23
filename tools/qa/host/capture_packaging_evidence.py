#!/usr/bin/env python3
"""Retain disposable APK/shrinker evidence without copying signing material."""
import hashlib
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'qa-packaging'
OUT.mkdir(exist_ok=True)
records = []
failures = []

def retain(source, relative):
    source = source.resolve()
    source.relative_to(ROOT)
    if not source.is_file():
        records.append({'source': str(source.relative_to(ROOT)), 'status': 'missing'})
        return False
    if source.suffix.lower() in {'.keystore', '.jks', '.p12', '.pfx', '.pem', '.key'}:
        raise RuntimeError('Signing material must not enter packaging evidence')
    target = OUT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    records.append({'source': str(source.relative_to(ROOT)), 'retained': str(target.relative_to(OUT)),
                    'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    return True

# Only these two APKs, never the keystore or a whole build directory.
for label, variable in [('candidate', 'CANDIDATE_APK'), ('instrumentation', 'TEST_APK')]:
    value = os.environ.get(variable)
    if value:
        path = ROOT / value
        if path.suffix != '.apk':
            raise RuntimeError('Unexpected packaging evidence type')
        if not retain(path, Path('apks') / (label + '.apk')):
            failures.append(label + ' APK missing')
    else:
        records.append({'apk': label, 'status': 'environment path unavailable'})
        failures.append(label + ' APK environment path unavailable')

mapping_root = ROOT / 'app/build/outputs/mapping'
allowed = {'mapping.txt', 'configuration.txt', 'usage.txt', 'seeds.txt', 'resources.txt', 'missing_rules.txt'}
for variant in ['freeRelease', 'freeReleaseAndroidTest']:
    for name in sorted(allowed):
        path = mapping_root / variant / name
        required = name in {'mapping.txt', 'configuration.txt'}
        if path.is_file():
            retain(path, Path('mapping') / variant / name)
        else:
            records.append({'mapping': variant + '/' + name, 'status': 'missing required' if required else 'optional, not emitted'})
            if required:
                failures.append(variant + '/' + name + ' missing')
(OUT / 'artifact-manifest.json').write_text(json.dumps({'complete': not failures, 'failures': failures, 'records': records}, indent=2) + '\n')
print('Packaging evidence:', len(records), 'manifest entries; signing material excluded')
if failures:
    raise SystemExit('Packaging capture incomplete: ' + '; '.join(failures))
