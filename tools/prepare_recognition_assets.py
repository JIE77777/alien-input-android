#!/usr/bin/env python3
"""Build local pixel-recognition assets from the pinned source masks and Android fixtures."""
from pathlib import Path
import argparse,shutil
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
old=a.output/'recognition-originals';old.mkdir(parents=True,exist_ok=True)
for source in (ROOT/'app/src/main/assets/glyphs').glob('F*.png'):shutil.copyfile(source,old/source.name)
variants=a.output/'recognition-numerals';variants.mkdir(exist_ok=True)
for source in (ROOT/'experiments/recognition/fixtures/android/glyphs').glob('N*.png'):
 mask=Image.open(source).getchannel('A')
 for size in [50,75]:
  resized=mask.resize((size,size),Image.Resampling.LANCZOS).resize((100,100),Image.Resampling.BILINEAR)
  rgba=Image.new('RGBA',(100,100),(255,255,255,0));rgba.putalpha(resized);rgba.save(variants/(source.stem+'-'+str(size)+'.png'))
print('Prepared original masks and scale variants for pixel recognition')
