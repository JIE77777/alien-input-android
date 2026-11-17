import argparse,json,random,io
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from recognize import Recognizer,positions
from numeral_codec import numeric_candidates
from evaluate_multiline import jpeg
p=argparse.ArgumentParser(description='Held-out palette, layout and ambiguity checks')
p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--uniform',type=Path,required=True);p.add_argument('--original',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();fixtures=a.fixtures;out=a.output;out.mkdir(exist_ok=True,parents=True)
e=Recognizer([a.uniform,a.original],generated_dir=fixtures/'glyphs')
manifest=json.loads((fixtures/'manifest.json').read_text());byname={r['file']:r for r in manifest['samples']}
fgs=[0xffe6b3,0,0xd90000,0,0xffb366,0xffd900,0x66cc00,0x33b333,0x4d4d99]
bgs=[0,0xffe6b3,0xffe6b3,0xffe6b3,0x4d3300,0x262600,0x1a3300,0x0d260d,0x1a1a33]
def color(n):return np.array([(n>>16)&255,(n>>8)&255,n&255],dtype=float)
rows=[]
for name in ['number-123456.png','number-999999999999999999.png','layout-0.png']:
 im=Image.open(fixtures/name).convert('RGB');alpha=np.asarray(im,dtype=float)[:,:,0:1]/255
 for palette,(fg,bg) in enumerate(zip(fgs,bgs)):
  received=Image.fromarray(np.round(color(bg)+(color(fg)-color(bg))*alpha).astype(np.uint8));received=jpeg(received.resize((240,round(im.height/2)),Image.Resampling.LANCZOS),50)
  r=e.recognize(received);rows.append(dict(case=name,palette=palette,status=r['status'],exact=r.get('glyphs')==byname[name]['glyphs'],wrong=r['status']=='recognized' and r.get('glyphs')!=byname[name]['glyphs']))
controls=[('black',Image.new('RGB',(480,256))),('white',Image.new('RGB',(480,256),'white'))]
rng=np.random.default_rng(20261119)
for i in range(10):controls.append((f'noise-{i}',Image.fromarray(rng.integers(0,256,(256,480,3),dtype=np.uint8))))
# Two sparse rows are visually plausible but impossible for the minimum-row production layout.
im=Image.new('RGB',(480,256));mask=e.masks[e.ids.index('F0835')]
for y in [22,134]:im.paste((255,230,179),(190,y),mask)
controls.append(('impossible-two-singletons',im))
neg=[]
for name,im in controls:
 r=e.recognize(im);neg.append(dict(case=name,status=r['status']));assert r['status']!='recognized',(name,r)
# Forty-eight rows alternate 1/4 glyphs: a valid 120-glyph arrangement at the upper row bound.
counts=[1,4]*24;im=Image.new('RGB',(480,5408));expected=[]
for row,count in enumerate(counts):
 for x in positions(count):im.paste((255,230,179),(x,22+112*row),mask);expected.append('F0835')
r=e.recognize(jpeg(im.resize((240,2704),Image.Resampling.LANCZOS),50));assert r['glyphs']==expected
assert numeric_candidates(['F1305','F1302'])[0]['requires_group_confirmation']
assert numeric_candidates(['F1302','F1302'])==[]
result=dict(palette_rows=rows,palette_exact=sum(r['exact'] for r in rows),palette_total=len(rows),palette_wrong=sum(r['wrong'] for r in rows),negative_controls=neg,max_rows_48_glyphs_120=True,number_grouping_not_asserted=True)
(out/'holdout-results.json').write_text(json.dumps(result,indent=2));print(json.dumps({k:v for k,v in result.items() if k not in ['palette_rows','negative_controls']},indent=2));print('negative controls',len(neg),'all rejected')
