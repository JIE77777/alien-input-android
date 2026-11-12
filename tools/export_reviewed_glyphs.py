#!/usr/bin/env python3
"""Overlay only explicitly reviewed game textures, with hashes checked before copying."""
from pathlib import Path
import argparse,hashlib,json,shutil
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parents[1];manifest=json.loads((root/'research/game/glyph-map.json').read_text());count=0
for row in manifest['glyphs']:
 if row['status']!='reviewed':continue
 source=root/'research/game/raw-reference'/(row['resource_name']+'.png')
 if hashlib.sha256(source.read_bytes()).hexdigest()!=row['source_sha256']:raise SystemExit('Unreviewed texture change: '+row['id'])
 args.output.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,args.output/(row['id']+'.png'));count+=1
print(f'Exported {count} reviewed game textures; pending mappings keep their original assets')
