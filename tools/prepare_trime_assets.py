#!/usr/bin/env python3
"""Use official v3.3.13 native/data assets, plus the locally compiled semantic dictionary."""
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1])
expected = 'e7535ea421a25962eb2ea37c46ff867838e69d33ba6d935876ab84d108de5020'
if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
    raise SystemExit('Official Trime APK SHA256 does not match release metadata')
trime = root / 'trime'
assets = trime / 'app/src/main/assets'
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.endswith('/'):
            continue
        if name.startswith('lib/arm64-v8a/'):
            target = trime / 'app/prebuilt' / name.removeprefix('lib/')
        elif name.startswith('assets/shared/'):
            target = assets / name.removeprefix('assets/')
        else:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(name))
(trime / 'app/prebuilt/official-assets.json').write_text(json.dumps({
    'tag': 'v3.3.13', 'commit': 'b5cad1a49095a3652e9188982de400ca3a653f37',
    'sha256': expected, 'abi': 'arm64-v8a',
}, indent=2) + '\n')
alien = assets / 'alien'
alien.mkdir(exist_ok=True)
for name in ('lexicon.tsv', 'manifest.json'):
    shutil.copyfile(root / 'lexicon/generated' / name, alien / name)
shutil.copytree(root / 'app/src/main/assets/glyphs', alien / 'glyphs', dirs_exist_ok=True)
for name in ('LICENSE.txt', 'THIRD_PARTY_NOTICES.txt'):
    shutil.copyfile(root / 'app/src/main/assets' / name, alien / name)
for path in (root / 'rime').glob('*.yaml'):
    shutil.copyfile(path, assets / 'shared' / path.name)
import subprocess
subprocess.run([sys.executable, str(root / 'tools/prepare_rime_pure.py')], check=True)
glyph_python = Path(os.environ['DEV_ENV_ROOT']) / 'venvs/game-assets/bin/python'
subprocess.run([str(glyph_python), str(root / 'tools/prepare_uniform_glyphs.py'), '--output', str(alien / 'glyphs')], check=True)
double = root / 'vendor/rime-double-pinyin/double_pinyin_flypy.schema.yaml'
if not double.is_file():
    raise SystemExit('Missing pinned vendor/rime-double-pinyin checkout')
shutil.copyfile(double, assets / 'shared' / double.name)
print('Prepared pinned native library, Rime data, alien dictionary and glyphs')
