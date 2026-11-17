"""Offline glyph recognition for intact Alien Input message images.
Uses the production four-slot layout, per-row centering, 1344 static glyphs and
108 Android-rendered dynamic numerals. Word/number group boundaries stay unknown.
"""
import argparse
import json
import time
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter
from numeral_codec import DYNAMIC_IDS, numeric_candidates

SIZE=(480,208)
FEATURE=48
MIN_SIMILARITY=0.91
MIN_MARGIN=0.025
MIN_LAYOUT=0.90


def feature(image):
    a=np.asarray(image.convert('L').filter(ImageFilter.GaussianBlur(.6)).resize((FEATURE,FEATURE),Image.Resampling.LANCZOS),dtype=np.float32).reshape(-1)
    return a/max(float(np.linalg.norm(a)),1e-8)


def infer_layout(image):
    if image.width<240 or image.width>4096 or image.width*image.height>16000000:
        raise ValueError('Unsupported size: width must be 240..4096 and pixels <=16 million')
    height=image.height*480/image.width
    tolerance=max(2.1,528/image.width)
    if abs(height-208)<=tolerance:return 1,208
    rows=round((height-32)/112)
    if not 2<=rows<=48 or abs(height-(rows*112+32))>tolerance:
        raise ValueError('Canvas does not match the production multiline layout')
    return rows,rows*112+32


def normalize(image):
    rows,height=infer_layout(image)
    a=np.asarray(image.convert('RGB').resize((480,height),Image.Resampling.LANCZOS),dtype=np.float32)
    corners=np.concatenate([a[:12,:12].reshape(-1,3),a[:12,-12:].reshape(-1,3),a[-12:,:12].reshape(-1,3),a[-12:,-12:].reshape(-1,3)])
    bg=np.median(corners,axis=0)
    contrast=np.linalg.norm(a-bg,axis=2)
    # The brightest pixels establish contrast even in very sparse numeral messages.
    scale=float(np.percentile(contrast[16:height-16],99.8))
    if scale<12:
        raise ValueError('No sufficiently contrasting glyphs')
    mask=np.clip(contrast/scale,0,1)
    mask[mask<.035]=0
    return Image.fromarray(np.round(mask*255).astype(np.uint8))


def positions(count):
    return [(480-count*112)//2+6+i*112 for i in range(count)]


class Recognizer:
    def __init__(self, glyph_dirs, alignment=True, generated_dir=None, numeric_detail=True):
        self.numeric_detail=numeric_detail
        self.alignment=alignment
        self.ids=[f'F{i:04d}' for i in range(1,1345)]
        if generated_dir is not None:self.ids+=DYNAMIC_IDS
        self.masks=[]
        self.styles=[]
        vectors=[]
        for root in glyph_dirs:
            for glyph in self.ids:
                path=(Path(generated_dir) if glyph.startswith('N') else Path(root))/(glyph+'.png')
                with Image.open(path) as im:
                    if im.size!=(100,100):
                        raise ValueError('Unexpected glyph size')
                    mask=im.convert('RGBA').getchannel('A').copy()
                self.masks.append(mask)
                self.styles.append(str(root))
                vectors.append(feature(mask))
        self.vectors=np.stack(vectors)
        self.number_models={}
        if generated_dir is not None:
            for power in range(4,22):
                split=round(100*((power-1)*10+10)/(100+(power-1)*10))
                bars=[];roots=[];ids=[]
                for c in range(1,7):
                    gid=f'N{c}P{power}';mask=self.masks[self.ids.index(gid)]
                    degraded=mask.resize((50,50),Image.Resampling.LANCZOS).resize((100,100),Image.Resampling.LANCZOS)
                    for variant in [mask,degraded]:
                        bar,root=number_features(variant,split)
                        bars.append(bar);roots.append(root);ids.append(gid)
                self.number_models[power]=(split,np.stack(bars),np.stack(roots),ids)


    def match_features(self, vectors):
        # Collapse alternate rendering styles BEFORE computing the runner-up gap.
        scores=np.asarray(vectors,dtype=np.float32)@self.vectors.T
        scores=scores.reshape(len(vectors),-1,len(self.ids))
        maxima=scores.max(axis=1)
        styles=scores.argmax(axis=1)
        order=np.argsort(-maxima,axis=1)[:,:3]
        return maxima,styles,order

    def number_evidence(self,crop):
        best={}
        for power,(split,bars,roots,ids) in self.number_models.items():
            bar,root=number_features(crop,split)
            bs=bars@bar;rs=roots@root;scores=(bs+rs)*.5
            for i,gid in enumerate(ids):
                if gid not in best or scores[i]>best[gid]['similarity']:
                    best[gid]=dict(id=gid,similarity=float(scores[i]),bars_similarity=float(bs[i]),coefficient_similarity=float(rs[i]))
        ranked=sorted(best.values(),key=lambda row:row['similarity'],reverse=True)
        top=ranked[0];top['margin']=top['similarity']-ranked[1]['similarity']
        return top

    def classify(self, crops):
        maxima,styles,order=self.match_features(np.stack([feature(c) for c in crops]))
        results=[]
        for r,indices in enumerate(order):
            top=int(indices[0]);gap=float(maxima[r,top]-maxima[r,indices[1]])
            confidence=bool(maxima[r,top]>=MIN_SIMILARITY and gap>=MIN_MARGIN)
            detail=None
            if self.ids[top].startswith('N') and self.numeric_detail:
                evidence=self.number_evidence(crops[r])
                detailed_index=self.ids.index(evidence['id'])
                in_shortlist=detailed_index in indices
                confidence=bool(in_shortlist and maxima[r,detailed_index]>=MIN_SIMILARITY
                    and maxima[r,detailed_index]-maxima[r,:1344].max()>=MIN_MARGIN
                    and evidence['similarity']>=.94 and evidence['margin']>=MIN_MARGIN
                    and min(evidence['bars_similarity'],evidence['coefficient_similarity'])>=.90)
                if in_shortlist:
                    top=detailed_index
                gap=float(maxima[r,top]-np.max(np.delete(maxima[r],top)))
                detail={k:round(v,5) if isinstance(v,float) else v for k,v in evidence.items()}
            result=dict(id=self.ids[top],similarity=round(float(maxima[r,top]),5),margin=round(gap,5),accepted=confidence,
                candidates=[dict(id=self.ids[int(i)],similarity=round(float(maxima[r,i]),5)) for i in indices],
                template_index=int(styles[r,top])*len(self.ids)+top)
            if detail is not None:result['number_structure']=detail
            results.append(result)
        return results

    def hypotheses(self, mask, offset=(0,0)):
        observed=feature_canvas(mask)
        hypotheses=[]
        for count in range(1,5):
            xs=positions(count)
            crops=[mask.crop((x,54,x+100,154)) for x in xs]
            if any(float(np.asarray(c,dtype=np.float32).sum())<255*12 for c in crops):
                continue
            symbols=self.classify(crops)
            rendered=Image.new('L',SIZE)
            for x,symbol in zip(xs,symbols):
                rendered.paste(self.masks[symbol['template_index']],(x,54))
            reconstruction=float(np.dot(observed,feature_canvas(rendered)))
            hypotheses.append(dict(count=count,layout_similarity=round(reconstruction,5),symbols=symbols,alignment_offset=list(offset)))
        return hypotheses

    def choose(self, hypotheses):
        if not hypotheses:
            return dict(status='unreadable',reason='No plausible occupied layout')
        # Compare different glyph sequences, not two alignments of the same answer.
        unique={}
        for h in hypotheses:
            key=tuple(s['id'] for s in h['symbols'])
            if key not in unique or h['layout_similarity']>unique[key]['layout_similarity']:
                unique[key]=h
        ranked=sorted(unique.values(),key=lambda h:h['layout_similarity'],reverse=True)
        best=ranked[0]
        gap=best['layout_similarity']-ranked[1]['layout_similarity'] if len(ranked)>1 else 1.
        accepted=best['layout_similarity']>=MIN_LAYOUT and gap>=.025 and all(s['accepted'] for s in best['symbols'])
        return dict(status='recognized' if accepted else 'uncertain',glyphs=[s['id'] for s in best['symbols']] if accepted else None,
                    phrase_boundaries='unknown',best=best,layout_margin=round(gap,5),alternatives=ranked[1:4])

    def recognize_row(self, mask):
        started=time.perf_counter()
        hypotheses=self.hypotheses(mask)
        result=self.choose(hypotheses)
        aligned=False
        if self.alignment and result['status']!='recognized':
            aligned=True
            for dx in range(-2,3):
                for dy in range(-2,3):
                    if dx==0 and dy==0:continue
                    shifted=Image.new('L',SIZE)
                    shifted.paste(mask,(dx,dy))
                    hypotheses.extend(self.hypotheses(shifted,(dx,dy)))
            result=self.choose(hypotheses)
        for h in [result.get('best',{})]+result.get('alternatives',[]):
            for symbol in h.get('symbols',[]):symbol.pop('template_index',None)
        result['alignment_search']=aligned
        result['seconds']=round(time.perf_counter()-started,4)
        return result

    def recognize(self, image):
        started=time.perf_counter()
        try:
            rows,height=infer_layout(image)
            mask=normalize(image)
        except ValueError as error:
            return dict(status='unreadable',reason=str(error))
        results=[]
        for row in range(rows):
            top=(height-rows*112)//2+6+row*112
            # Map each row to the canonical single-row coordinates. The comparison
            # band excludes neighbouring rows and any optional footer.
            band=mask.crop((0,top-54,480,top+154))
            result=self.recognize_row(band)
            result['row']=row;result['canonical_y']=top
            results.append(result)
        accepted=all(r['status']=='recognized' for r in results)
        glyphs=[g for r in results for g in (r.get('glyphs') or [])] if accepted else None
        if glyphs is not None and len(glyphs)>120:
            return dict(status='unreadable',reason='More than 120 glyphs exceeds the production draft limit')
        row_counts=[len(r.get('best',{}).get('symbols',[])) for r in results]
        layout_reason=None
        if accepted and any(a+b<=4 for a,b in zip(row_counts,row_counts[1:])):
            # These adjacent rows could always be merged under the production layout.
            accepted=False;glyphs=None
            layout_reason='Adjacent rows cannot come from the production minimum-row layout'
        # Keep the single-row fields for compatibility with the first prototype.
        output=dict(results[0]) if rows==1 else {}
        output.update(status='recognized' if accepted else 'uncertain',glyphs=glyphs,
            phrase_boundaries='unknown',rows=results,row_count=rows,
            numeric_candidates=numeric_candidates(glyphs) if glyphs else [],
            seconds=round(time.perf_counter()-started,4))
        if layout_reason:output['reason']=layout_reason
        return output


def number_features(image,split):
    # Balance the repetitive upper bars against the small coefficient at the bottom.
    a=np.asarray(image.convert('L').filter(ImageFilter.GaussianBlur(.3)),dtype=np.float32).copy()
    a[split:]=0
    v=a.reshape(-1);v=v/max(float(np.linalg.norm(v)),1e-8)
    root=feature(image.crop((0,split,100,100)))
    return v,root


def feature_canvas(image):
    # Restrict to the actual glyph band: experimental footers carry no recognition signal.
    a=np.asarray(image.crop((0,48,480,160)).filter(ImageFilter.GaussianBlur(.6)).resize((240,56),Image.Resampling.LANCZOS),dtype=np.float32).reshape(-1)
    return a/max(float(np.linalg.norm(a)),1e-8)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--glyph-dir',type=Path,action='append',required=True)
    p.add_argument('--input',type=Path,required=True)
    p.add_argument('--generated-dir',type=Path,default=Path(__file__).parent/'fixtures/android/glyphs')
    p.add_argument('--no-alignment',action='store_true',help='Reproduce the original fixed-position baseline')
    a=p.parse_args()
    engine=Recognizer(a.glyph_dir,alignment=not a.no_alignment,generated_dir=a.generated_dir)
    with Image.open(a.input) as image:
        result=engine.recognize(image)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    raise SystemExit(0 if result['status']=='recognized' else 2)

if __name__=='__main__': main()
