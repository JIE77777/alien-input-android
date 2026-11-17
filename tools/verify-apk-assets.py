#!/usr/bin/env python3
"""Verify the installed artifact, including names used by Trime's asset synchronizer."""
import hashlib,json,sys,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
apks=list((root/'trime/app/build/outputs/apk/debug').glob('*.apk'))
assert apks,'No APK found to verify'
for apk in apks:
 with zipfile.ZipFile(apk) as archive:
  names=set(archive.namelist())
  expected=(root/'trime/app/src/main/assets/alien/reader-model.agr').read_bytes()
  assert archive.read('assets/alien/reader-model.agr')==expected,'Reader model changed during APK packaging'
  manifests=[n for n in names if n.startswith('assets/') and 'checksum' in n.lower() and n.endswith('.json')]
  assert manifests,'Trime asset checksum manifest missing'
  checked=0
  for manifest in manifests:
   data=json.loads(archive.read(manifest))
   for relative,digest in data['files'].items():
    if not digest:continue
    path='assets/'+relative
    assert path in names,'Runtime asset missing: '+path
    assert hashlib.sha256(archive.read(path)).hexdigest()==digest,'Runtime asset differs: '+path
    checked+=1
  print('PASS APK runtime assets:',checked,'files; reader model intact')
