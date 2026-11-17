"""Held-out layout/blur stresses; do not retune thresholds on these results."""
import argparse,json,random,io
from pathlib import Path
from PIL import Image,ImageFilter,PngImagePlugin
from recognize import Recognizer
from benchmark import render,jpeg
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--uniform',type=Path,required=True);p.add_argument('--original',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--no-alignment',action='store_true')
a=p.parse_args();engine=Recognizer([a.uniform,a.original],alignment=not a.no_alignment);rng=random.Random(20261118);rows=[]
for i in range(128):
 ids=rng.sample(range(1344),1+i%4);expected=[engine.ids[j] for j in ids];im=render([engine.masks[j] for j in ids])
 moved=Image.new('RGB',im.size);moved.paste(im,(2,-2))
 padded=Image.new('RGB',(528,256),(30,30,30));padded.paste(im,(24,24))
 for name,source in [('translate_2px',moved),('blur_1px',im.filter(ImageFilter.GaussianBlur(1))),('rotate_1deg',im.rotate(1,resample=Image.Resampling.BICUBIC)),('crop5pct',im.crop((24,10,456,198))),('padding24',padded)]:
  received=jpeg(source,65);res=engine.recognize(received);got=res.get('glyphs')
  rows.append(dict(name=name,sample=i,expected=expected,status=res['status'],recognized=got,exact=got==expected,wrong=res['status']=='recognized' and got!=expected))
# Deliberately false metadata and a different file name must not alter recognized pixels.
im=render([engine.masks[901],engine.masks[833],engine.masks[1304],engine.masks[1301]])
meta=PngImagePlugin.PngInfo();meta.add_text('AlienInput','ALIEN1|v6.12.4|F0001')
b=io.BytesIO();im.save(b,format='PNG',pnginfo=meta);b.seek(0)
res=engine.recognize(Image.open(b));assert res['glyphs']==['F0902','F0834','F1305','F1302']
summary=[]
for name in dict.fromkeys(r['name'] for r in rows):
 rr=[r for r in rows if r['name']==name]
 summary.append(dict(name=name,total=len(rr),correct=sum(r['exact'] for r in rr),wrong=sum(r['wrong'] for r in rr),rejected=sum(r['status']!='recognized' for r in rr)))
a.output.mkdir(exist_ok=True,parents=True)
(a.output/'stress-results.json').write_text(json.dumps(dict(alignment=not a.no_alignment,summary=summary,rows=rows,false_metadata_ignored=True),ensure_ascii=False,indent=2))
print(json.dumps(summary,indent=2))
