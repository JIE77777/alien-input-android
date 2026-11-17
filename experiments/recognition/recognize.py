"""Offline static-glyph prototype for intact, single-row Alien Input images.

Reads pixels only. Assumes the renderer's 480x208 canvas (or uniform resize),
1..4 centered cells, 100px glyph masks, 112px pitch. No phrase-boundary inference.
"""
import argparse
import json
import time
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

SIZE=(480,208)
FEATURE=48
MIN_SIMILARITY=0.91
MIN_MARGIN=0.025
MIN_LAYOUT=0.90


def feature(image):
    a=np.asarray(image.convert('L').filter(ImageFilter.GaussianBlur(.6)).resize((FEATURE,FEATURE),Image.Resampling.LANCZOS),dtype=np.float32).reshape(-1)
    return a/max(float(np.linalg.norm(a)),1e-8)


def normalize(image):
    if image.width<240 or image.width>4096 or abs(image.width/image.height-480/208)>.025:
        raise ValueError('Only intact 480:208 single-row canvases, width 240..4096, are supported')
    a=np.asarray(image.convert('RGB').resize(SIZE,Image.Resampling.LANCZOS),dtype=np.float32)
    corners=np.concatenate([a[:12,:12].reshape(-1,3),a[:12,-12:].reshape(-1,3),a[-12:,:12].reshape(-1,3),a[-12:,-12:].reshape(-1,3)])
    bg=np.median(corners,axis=0)
    contrast=np.linalg.norm(a-bg,axis=2)
    scale=float(np.percentile(contrast[48:160],99.8))
    if scale<12:
        raise ValueError('No sufficiently contrasting glyphs')
    mask=np.clip(contrast/scale,0,1)
    mask[mask<.035]=0
    return Image.fromarray(np.round(mask*255).astype(np.uint8))


def positions(count):
    return [(480-count*112)//2+6+i*112 for i in range(count)]


class Recognizer:
    def __init__(self, glyph_dirs, alignment=True):
        self.alignment=alignment
        self.ids=[f'F{i:04d}' for i in range(1,1345)]
        self.masks=[]
        self.styles=[]
        vectors=[]
        for root in glyph_dirs:
            for glyph in self.ids:
                with Image.open(Path(root)/(glyph+'.png')) as im:
                    if im.size!=(100,100):
                        raise ValueError('Unexpected glyph size')
                    mask=im.convert('RGBA').getchannel('A').copy()
                self.masks.append(mask)
                self.styles.append(str(root))
                vectors.append(feature(mask))
        self.vectors=np.stack(vectors)

    def match_features(self, vectors):
        # Collapse alternate rendering styles BEFORE computing the runner-up gap.
        scores=np.asarray(vectors,dtype=np.float32)@self.vectors.T
        scores=scores.reshape(len(vectors),-1,len(self.ids))
        maxima=scores.max(axis=1)
        styles=scores.argmax(axis=1)
        order=np.argsort(-maxima,axis=1)[:,:3]
        return maxima,styles,order

    def classify(self, crops):
        maxima,styles,order=self.match_features(np.stack([feature(c) for c in crops]))
        results=[]
        for r,indices in enumerate(order):
            top=int(indices[0]);gap=float(maxima[r,top]-maxima[r,indices[1]])
            confidence=bool(maxima[r,top]>=MIN_SIMILARITY and gap>=MIN_MARGIN)
            results.append(dict(id=self.ids[top],similarity=round(float(maxima[r,top]),5),margin=round(gap,5),accepted=confidence,
                candidates=[dict(id=self.ids[int(i)],similarity=round(float(maxima[r,i]),5)) for i in indices],
                template_index=int(styles[r,top])*len(self.ids)+top))
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

    def recognize(self, image):
        started=time.perf_counter()
        try: mask=normalize(image)
        except ValueError as error:
            return dict(status='unreadable',reason=str(error))
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


def feature_canvas(image):
    # Restrict to the actual glyph band: experimental footers carry no recognition signal.
    a=np.asarray(image.crop((0,48,480,160)).filter(ImageFilter.GaussianBlur(.6)).resize((240,56),Image.Resampling.LANCZOS),dtype=np.float32).reshape(-1)
    return a/max(float(np.linalg.norm(a)),1e-8)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--glyph-dir',type=Path,action='append',required=True)
    p.add_argument('--input',type=Path,required=True)
    p.add_argument('--no-alignment',action='store_true',help='Reproduce the original fixed-position baseline')
    a=p.parse_args()
    engine=Recognizer(a.glyph_dir,alignment=not a.no_alignment)
    with Image.open(a.input) as image:
        result=engine.recognize(image)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    raise SystemExit(0 if result['status']=='recognized' else 2)

if __name__=='__main__': main()
