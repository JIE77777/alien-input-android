"""Experimental visible geometric footer, canonical 480x208 images only.

144 bits = 80 payload bits (75 + 5 zero pad) + BCH(8, 285) 64 parity bits.
Bits are short dashes in the upper/lower half of 48x3 cells. No neural model.
Not a frozen interchange format; cropping/rotation/other aspect ratios unsupported.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageStat
from trustmark.bchecc import BCH
from payload import pack, unpack, code

SIZE = (480, 208)

def packet(groups):
    data = bytes(int((pack(groups) + '00000')[i:i+8], 2) for i in range(0, 80, 8))
    ecc = BCH(8, 285).encode(data)
    assert len(ecc) == 8
    return ''.join(format(b, '08b') for b in data + bytes(ecc))

def encode(image, groups, level=64):
    if image.size != SIZE or not 24 <= level <= 128:
        raise ValueError('Requires 480x208 image and level 24..128')
    image = image.convert('RGB').copy()
    # Fail instead of painting over message content. This initial design targets black.
    if image.crop((0,176,480,208)).getbbox() is not None:
        raise ValueError('Footer requires empty black bottom margin')
    draw = ImageDraw.Draw(image)
    ink = (level, round(level*230/255), round(level*160/255))
    for i, bit in enumerate(packet(groups)):
        row, col = divmod(i,48)
        x, y = 25 + col*9, 179 + row*8 + (0 if bit == '1' else 4)
        draw.line((x,y,x+5,y), fill=ink, width=2)
    image.info.clear()
    return image

def decode(image):
    if image.width < 240 or abs(image.width/image.height - 480/208) > 0.025:
        return {'status':'unreadable','reason':'Unsupported size/aspect ratio'}
    image = image.convert('L').resize(SIZE, Image.Resampling.LANCZOS)
    bits=[]
    contrasts=[]
    for i in range(144):
        row,col=divmod(i,48)
        x,y=25+col*9,179+row*8
        upper=ImageStat.Stat(image.crop((x,y,x+6,y+2))).mean[0]
        lower=ImageStat.Stat(image.crop((x,y+4,x+6,y+6))).mean[0]
        contrasts.append(abs(upper-lower))
        bits.append('1' if upper>lower else '0')
    raw=''.join(bits)
    data=bytearray(int(raw[i:i+8],2) for i in range(0,80,8))
    ecc=bytearray(int(raw[i:i+8],2) for i in range(80,144,8))
    corrected=BCH(8,285).decode(data,ecc)
    if corrected < 0:
        return {'status':'unreadable','reason':'BCH failed'}
    body=''.join(format(b,'08b') for b in data)
    try:
        if body[75:] != '00000':
            raise ValueError('Noncanonical padding')
        groups=unpack(body[:75])
    except ValueError as exc:
        return {'status':'unreadable','reason':str(exc)}
    return {'status':'decoded','groups':groups,'code':code(groups),'corrected_bits':corrected,
            'mean_contrast':round(sum(contrasts)/len(contrasts),2)}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['encode','decode'])
    p.add_argument('--input',type=Path,required=True)
    p.add_argument('--output',type=Path)
    p.add_argument('--groups')
    p.add_argument('--level',type=int,default=64)
    a=p.parse_args()
    with Image.open(a.input) as source:
        if source.width*source.height>16000000:
            p.error('Image too large')
        image=source.convert('RGB')
    if a.action=='encode':
        if not a.output or not a.groups:
            p.error('encode requires output and groups')
        if a.output.resolve()==a.input.resolve():
            p.error('Output must differ from input')
        groups=[g.split(',') for g in a.groups.split(';')]
        image=encode(image,groups,a.level)
        a.output.parent.mkdir(parents=True,exist_ok=True)
        image.save(a.output,format='PNG')
    result=decode(image)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    raise SystemExit(0 if result['status']=='decoded' else 2)
if __name__=='__main__':
    main()
