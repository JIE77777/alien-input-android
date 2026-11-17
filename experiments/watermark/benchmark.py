"""Reproducible synthetic compression benchmark, NOT a WeChat field test."""
import argparse
import io
import json
import random
import statistics
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from watermark import decode, encode, load_rgb, model


def sample_groups():
    result = {'v-me-50': [['F0902'],['F0834'],['F1305','F1302']],
              'one': [['F0834']], 'two': [['F0835'],['F1047']],
              'four-grouped': [['F0001','F0902'],['F0834','F1047']]}
    randomizer = random.Random(20261117)
    for i in range(3):
        ids = [f'F{n:04d}' for n in randomizer.sample(range(1,1345),4)]
        result[f'random-{i+1}'] = [[ids[0]],ids[1:]]
    result['light-background'] = result['v-me-50']
    return result


def render(glyph_dir, groups, foreground=(255, 230, 179), background=(0, 0, 0)):
    ids = [glyph for group in groups for glyph in group]
    image = Image.new('RGB', (480, 208), background)
    left = (480 - 112 * len(ids)) // 2 + 6
    for index, glyph in enumerate(ids):
        with Image.open(glyph_dir / (glyph + '.png')) as source:
            mask = source.convert('RGBA').getchannel('A')
        image.paste(foreground, (left + index * 112, 54), mask)
    return image


def jpeg(image, quality):
    buffer = io.BytesIO()
    image.save(buffer, format='JPEG', quality=quality, subsampling=2)
    buffer.seek(0)
    with Image.open(buffer) as decoded:
        result = decoded.convert('RGB')
        result.info.clear()
        return result.copy()


def transformed(image):
    yield 'png_pixels_only', image.copy()
    for quality in [90, 75, 50, 30]:
        yield f'jpeg_q{quality}', jpeg(image, quality)
    for width, quality in [(360, 75), (240, 75), (240, 50)]:
        resized = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
        yield f'width{width}_q{quality}', jpeg(resized, quality)
    repeated = image
    for quality in [85, 65, 75]:
        repeated = jpeg(repeated, quality)
    yield 'jpeg_3rounds', repeated
    dx, dy = round(image.width * .05), round(image.height * .05)
    yield 'crop5pct_each_edge_q75', jpeg(image.crop((dx, dy, image.width-dx, image.height-dy)), 75)
    padded = Image.new('RGB', (image.width+48, image.height+48), (30,30,30))
    padded.paste(image, (24,24))
    yield 'padding24_q75', jpeg(padded, 75)


def metrics(original, marked):
    a, b = np.asarray(original, dtype=np.float32), np.asarray(marked, dtype=np.float32)
    diff = np.abs(a-b)
    mse = float(np.mean((a-b)**2))
    flat = np.all(a == a[0,0], axis=2)
    return {'psnr_db': round(10*np.log10(255**2/max(mse, 1e-12)),2),
            'mean_absolute_rgb': round(float(diff.mean()),3),
            'max_rgb_change': int(diff.max()),
            'background_mean_change': round(float(diff[flat].mean()),3),
            'background_pixels_change_gt5_percent': round(float((diff.max(axis=2)[flat]>5).mean()*100),2)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--glyph-dir', type=Path, required=True)
    parser.add_argument('--meme', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--device', default='cpu')
    parser.add_argument('--variants', nargs='+', choices=['Q','P'], default=['Q','P'])
    parser.add_argument('--strengths', nargs='+', type=float, default=[1.0,1.5])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    samples = []
    for name, groups in sample_groups().items():
        if name == 'v-me-50':
            image = load_rgb(args.meme)
        elif name == 'light-background':
            image = render(args.glyph_dir,groups,(0,0,0),(255,230,179))
        else:
            image = render(args.glyph_dir,groups)
        samples.append((name,image,groups))
    for name, image, _ in samples:
        image.save(args.output / (name+'-original.png'))
    rows, qualities, controls = [], [], []
    comparisons = []
    for variant in args.variants:
        tm = model(variant, args.device)
        # Unwatermarked source images, compressed originals, and flat canvases.
        negatives = [(name, image) for name,image,_ in samples]
        negatives += [(name+'-q50',jpeg(image,50)) for name,image,_ in samples]
        negatives += [('black',Image.new('RGB',(480,208),'black')),
                      ('white',Image.new('RGB',(480,208),'white'))]
        for name, image in negatives:
            result = decode(tm, image)
            controls.append({'variant':variant,'sample':name,**result})
        for strength in args.strengths:
            setting = f'{variant}-{strength:g}'
            for name, original, groups in samples:
                started = time.perf_counter()
                marked = encode(tm, original, groups, strength)
                encode_ms = (time.perf_counter()-started)*1000
                path = args.output / f'{name}-{setting}.png'
                marked.save(path)
                marked = load_rgb(path)  # Never retain an in-memory payload channel.
                quality = dict(setting=setting,sample=name,encode_ms=round(encode_ms,1),**metrics(original,marked))
                qualities.append(quality)
                if name == 'v-me-50':
                    diff = np.clip(np.abs(np.asarray(original,dtype=float)-np.asarray(marked,dtype=float))*8,0,255).astype('uint8')
                    comparisons.append((setting,original,marked,Image.fromarray(diff)))
                for transform, received in transformed(marked):
                    started = time.perf_counter()
                    result = decode(tm,received)
                    row = {'setting':setting,'sample':name,'transform':transform,
                           'width':received.width,'height':received.height,
                           'decode_ms':round((time.perf_counter()-started)*1000,1),
                           'exact':result.get('groups')==groups,
                           'wrong_message':result['status']=='decoded' and result.get('groups')!=groups,**result}
                    rows.append(row)
                    if name=='v-me-50' and transform=='width240_q75':
                        received.save(args.output / f'{name}-{setting}-{transform}.png')
                sample_rows = [r for r in rows if r['setting']==setting and r['sample']==name]
                print(f'{setting} {name}: {sum(r["exact"] for r in sample_rows)}/{len(sample_rows)} exact, PSNR {quality["psnr_db"]}',flush=True)
                (args.output/'results.partial.json').write_text(json.dumps({'rows':rows,'quality':qualities,'controls':controls},ensure_ascii=False,indent=2))
        del tm
    summary = []
    for setting in sorted({row['setting'] for row in rows}):
        selected = [r for r in rows if r['setting']==setting]
        by_transform = {}
        for transform in dict.fromkeys(r['transform'] for r in selected):
            matches = [r for r in selected if r['transform']==transform]
            by_transform[transform] = {'exact':sum(r['exact'] for r in matches),'total':len(matches)}
        q = [r for r in qualities if r['setting']==setting]
        summary.append({'setting':setting,'exact':sum(r['exact'] for r in selected),'total':len(selected),
                        'wrong_messages':sum(r['wrong_message'] for r in selected),
                        'decode_median_ms':round(statistics.median(r['decode_ms'] for r in selected),1),
                        'encode_median_ms':round(statistics.median(r['encode_ms'] for r in q),1),
                        'mean_psnr_db':round(statistics.mean(r['psnr_db'] for r in q),2),
                        'by_transform':by_transform})
    result = {'scope':'Synthetic transformations of 8 generated/known alien images; not WeChat validation',
              'device':args.device,'seed':20261117,'payload_bits':75,'expected_groups':sample_groups(),'rows':rows,'quality':qualities,
              'controls':controls,'summary':summary,
              'negative_acceptances':sum(r['status']=='decoded' for r in controls)}
    (args.output/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    sheet = Image.new('RGB',(1440,len(comparisons)*240),'#333333')
    draw=ImageDraw.Draw(sheet)
    for i,(setting,original,marked,difference) in enumerate(comparisons):
        for j,(title,img) in enumerate([('Original',original),(f'TrustMark {setting}',marked),('Difference x8',difference)]):
            draw.text((j*480+8,i*240+8),title,fill='white')
            sheet.paste(img,(j*480,i*240+28))
    sheet.save(args.output/'comparison.png')
    print(json.dumps(summary,ensure_ascii=False,indent=2),flush=True)


if __name__=='__main__':
    main()
