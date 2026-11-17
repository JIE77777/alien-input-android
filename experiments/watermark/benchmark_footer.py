import argparse,json,random
from collections import defaultdict
from pathlib import Path
from PIL import Image,ImageDraw
from benchmark import sample_groups,transformed,render
from footer import encode,decode,packet
from trustmark.bchecc import BCH
from payload import pack
p=argparse.ArgumentParser(description='Synthetic footer compression checks; not a WeChat field test')
p.add_argument('--glyph-dir',type=Path,required=True)
p.add_argument('--meme',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
out=a.output
out.mkdir(parents=True,exist_ok=True)
source=Image.open(a.meme).convert('RGB')
glyph=a.glyph_dir
rows=[]; controls=[]
for name,groups in sample_groups().items():
 if name=='light-background': continue
 im=render(glyph,groups)
 if name=='v-me-50': im=source
 for level in [32,48,64,96]:
  marked=encode(im,groups,level)
  if name=='v-me-50': marked.save(out/f'footer-{level}.png')
  for trans,received in transformed(marked):
   result=decode(received)
   rows.append(dict(sample=name,level=level,transform=trans,exact=result.get('groups')==groups,wrong=result['status']=='decoded' and result.get('groups')!=groups,**result))
 for trans,received in transformed(im):
  controls.append(dict(sample=name,transform=trans,**decode(received)))
# Raw BCH checks demonstrate this specific library configuration corrects injected errors.
rng=random.Random(20261117); tests=0
for errors in range(9):
 for _ in range(20):
  groups=[[f'F{rng.randint(1,1344):04d}' for _ in range(rng.randint(1,4))]]
  bits=list(packet(groups))
  for pos in rng.sample(range(144),errors):bits[pos]='1' if bits[pos]=='0' else '0'
  data=bytearray(int(''.join(bits[i:i+8]),2) for i in range(0,80,8))
  ecc=bytearray(int(''.join(bits[i:i+8]),2) for i in range(80,144,8))
  n=BCH(8,285).decode(data,ecc)
  assert n==errors and ''.join(format(b,'08b') for b in data)==pack(groups)+'00000'
  tests+=1
summary=[]
for level in [32,48,64,96]:
 by={}
 for trans in dict.fromkeys(r['transform'] for r in rows):
  rr=[r for r in rows if r['level']==level and r['transform']==trans]
  by[trans]=f"{sum(r['exact'] for r in rr)}/{len(rr)}"
 summary.append(dict(level=level,transforms=by))
result=dict(summary=summary,rows=rows,controls=controls,bch_corruption_tests=tests,wrong_messages=sum(r['wrong'] for r in rows),negative_accepts=sum(r['status']=='decoded' for r in controls))
(out/'footer-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in result.items() if k not in ['rows','controls']},ensure_ascii=False,indent=2))
