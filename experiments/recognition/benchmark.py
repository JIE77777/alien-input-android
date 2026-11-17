"""Deterministic full-pipeline evaluation; thresholds fixed before the run."""
import argparse,io,json,random,time,hashlib
from pathlib import Path
from collections import Counter
import numpy as np
from PIL import Image,ImageDraw
from recognize import Recognizer,positions


def jpeg(im,q):
    b=io.BytesIO();im.save(b,format='JPEG',quality=q,subsampling=2);b.seek(0)
    return Image.open(b).convert('RGB')

def render(masks,fg=(255,230,179),bg=(0,0,0)):
    im=Image.new('RGB',(480,208),bg)
    for x,mask in zip(positions(len(masks)),masks):im.paste(fg,(x,54),mask)
    return im

def transform(im,name):
    if name=='png':return im.copy()
    if name=='jpeg75':return jpeg(im,75)
    if name=='half_jpeg50':return jpeg(im.resize((240,104),Image.Resampling.LANCZOS),50)
    if name=='half_jpeg30':return jpeg(im.resize((240,104),Image.Resampling.LANCZOS),30)
    raise ValueError(name)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--uniform',type=Path,required=True);p.add_argument('--original',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--field-dir',type=Path)
    p.add_argument('--no-alignment',action='store_true')
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    started=time.perf_counter();engine=Recognizer([a.uniform,a.original],alignment=not a.no_alignment);rows=[];summaries=[]
    def check(im,expected,suite,name):
        result=engine.recognize(im)
        top=[s['id'] for s in result.get('best',{}).get('symbols',[])]
        accepted=result['status']=='recognized'
        row=dict(suite=suite,name=name,expected=expected,status=result['status'],top=top,
            exact=accepted and result['glyphs']==expected,wrong=accepted and result['glyphs']!=expected,
            top_exact=top==expected,seconds=result.get('seconds',0),
            min_symbol_margin=min((s['margin'] for s in result.get('best',{}).get('symbols',[])),default=None),
            layout_similarity=result.get('best',{}).get('layout_similarity'))
        rows.append(row)
        return result
    def summarize(suite):
        rr=[r for r in rows if r['suite']==suite]
        result=dict(suite=suite,total=len(rr),correct=sum(r['exact'] for r in rr),wrong=sum(r['wrong'] for r in rr),
                    rejected=sum(r['status']!='recognized' for r in rr),top_exact=sum(r['top_exact'] for r in rr),
                    median_seconds=round(float(np.median([r['seconds'] for r in rr])),4))
        summaries.append(result);print(json.dumps(result),flush=True)
        (a.output/'results.partial.json').write_text(json.dumps(dict(summary=summaries,rows=rows),indent=2))
    # Exact template duplicates are tested separately from near-duplicate ambiguities.
    hashes={}
    for glyph,mask in zip(engine.ids,engine.masks[:1344]):
        hashes.setdefault(hashlib.sha256(mask.tobytes()).hexdigest(),[]).append(glyph)
    duplicates=[ids for ids in hashes.values() if len(ids)>1]
    for style,offset,variant in [('uniform',0,'png'),('uniform',0,'jpeg75'),('uniform',0,'half_jpeg50'),('original',1344,'half_jpeg50')]:
        suite=style+'_'+variant
        for i,glyph in enumerate(engine.ids):
            check(transform(render([engine.masks[offset+i]]),variant),[glyph],suite,glyph)
        summarize(suite)
    rng=random.Random(20261117)
    for variant in ['png','jpeg75','half_jpeg50','half_jpeg30']:
        suite='mixed_'+variant
        # Resetting the seed keeps the same 128 message identities across conditions.
        rng.seed(20261117)
        for i in range(128):
            ids=rng.sample(range(1344),1+i%4)
            fg,bg=((255,230,179),(0,0,0)) if i%2 else ((0,0,0),(255,230,179))
            im=render([engine.masks[j] for j in ids],fg,bg)
            check(transform(im,variant),[engine.ids[j] for j in ids],suite,str(i))
        summarize(suite)
    suite='negative_controls'
    controls=[('black',Image.new('RGB',(480,208),'black')),('white',Image.new('RGB',(480,208),'white'))]
    nrng=np.random.default_rng(20261117)
    for i in range(20):
        controls.append((f'noise-{i}',Image.fromarray(nrng.integers(0,256,(208,480,3),dtype=np.uint8))))
    for i in range(20):
        im=Image.new('RGB',(480,208),'black');draw=ImageDraw.Draw(im)
        draw.text((195,80),f'hello {i}',fill='white');controls.append((f'latin-{i}',im))
    # Out-of-scope procedural numerals: same rendering formula, not in static F templates.
    coeff=['F1302','F1310','F1317','F1324','F1331','F1338']
    for power in [4,8,14,21]:
        for c,glyph in enumerate(coeff,1):
            extra=(power-1)*10;height=100+extra
            mask=Image.new('L',(100,height));mask.paste(engine.masks[engine.ids.index(glyph)],(0,extra));draw=ImageDraw.Draw(mask)
            for k in range(power):draw.line((6,6+k*10,94,6+k*10),fill=255,width=3)
            small=mask.resize((round(100*100/height),100),Image.Resampling.LANCZOS)
            tile=Image.new('L',(100,100));tile.paste(small,((100-small.width)//2,0))
            controls.append((f'procedural-N{c}P{power}',render([tile])))
    for name,im in controls:check(im,None,suite,name)
    summarize(suite)
    field=[]
    if a.field_dir:
        for name in ['wechat-P2.jpg','wechat-footer-32.jpg','wechat-footer-64.jpg']:
            path=a.field_dir/name
            if path.exists():
                expected=['F0902','F0834','F1305','F1302']
                result=check(Image.open(path),expected,'wechat_saved',name)
                field.append(dict(file=name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),**result))
        summarize('wechat_saved')
    result=dict(alignment=not a.no_alignment,scope='Intact single-row canvas only, static 1344 F glyphs, pixel-only, no phrase boundaries',
        fixed_thresholds=dict(similarity=.91,margin=.025,layout=.90,layout_margin=.025),
        identical_masks=duplicates,summary=summaries,rows=rows,field=field,total_seconds=round(time.perf_counter()-started,1))
    (a.output/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
    print('DONE '+str(result['total_seconds'])+' seconds',flush=True)
if __name__=='__main__':main()
