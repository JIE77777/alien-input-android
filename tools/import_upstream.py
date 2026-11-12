#!/usr/bin/env python3
"""Import only the public v6.12.4 glyphs, standard codes and phrase data."""
import base64, json, pathlib, sys
source = pathlib.Path(sys.argv[1])
out = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets'
(out / 'glyphs').mkdir(parents=True, exist_ok=True)
s = json.loads((source / 'data/standard.json').read_text())
i = json.loads((source / 'data/ime.json').read_text())
g = json.loads((source / 'data/glyphs.json').read_text())['glyphs']
def safe(t): return str(t).replace('\t', ' ').replace('\n', ' ')
for r in g:
 (out / 'glyphs' / (r['id'] + '.png')).write_bytes(base64.b64decode(r['png'].split(',',1)[1]))
for key, value in i['keyImages'].items():
 if value.startswith('data:image/png;base64,'):
  (out / 'glyphs' / (key + '.png')).write_bytes(base64.b64decode(value.split(',',1)[1]))
with (out/'glyphs.tsv').open('w') as f:
 for r in s['glyphs']:
  f.write('\t'.join([r['id'],safe(r['label']),str(r.get('count',0)), '|'.join(','.join(c) for c in s['codes'][r['id']])])+'\n')
with (out/'phrases.tsv').open('w') as f:
 for r in s['wordLayer']['phrases']:
  if r.get('coding_available'):
   f.write('\t'.join([r['id'],safe(r['label']),str(r.get('occurrences',0)),','.join(r['glyphs'])])+'\n')
with (out/'keys.tsv').open('w') as f:
 for k in s['slots']:
  f.write('\t'.join([k['normal'], k['shift'], k['main'], k['sub']])+'\n')
(out/'LICENSE.txt').write_text((source/'LICENSE').read_text())
(out/'THIRD_PARTY_NOTICES.txt').write_text((source/'THIRD_PARTY_NOTICES.md').read_text())
print(f'Imported {len(g)} glyphs, {sum(map(len,s["codes"].values()))} routes, {len(s["slots"])} key pairs.')
