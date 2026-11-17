"""Replay a supplied JPEG's quantization/subsampling, without uploading that image."""
import argparse
import io
import json
from pathlib import Path
from PIL import Image
from benchmark import metrics, sample_groups
from watermark import decode, encode, load_rgb, model


def recompress(image, profile):
    buffer=io.BytesIO()
    tables=[profile['quantization'][key] for key in sorted(profile['quantization'],key=int)]
    image.save(buffer,format='JPEG',qtables=tables,subsampling=profile['subsampling'])
    buffer.seek(0)
    with Image.open(buffer) as received:
        result=received.convert('RGB');result.info.clear()
        return result.copy(),buffer.getvalue()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',type=Path,required=True)
    parser.add_argument('--benchmark-dir',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    profile=json.loads(args.profile.read_text())
    previous=json.loads((args.benchmark_dir/'results.json').read_text())
    groups_by_sample=sample_groups()
    # Keep even samples that failed every baseline decode.
    names={row['sample'] for row in previous['rows']}
    assert names==set(groups_by_sample)
    args.output.mkdir(parents=True,exist_ok=True)
    rows=[]
    for variant in ['Q','P']:
        tm=model(variant)
        for strength in [1.0,1.5,2.0,3.0]:
            for name in sorted(names):
                groups=groups_by_sample[name]
                original=load_rgb(args.benchmark_dir/(name+'-original.png'))
                marked=encode(tm,original,groups,strength)
                for width in [480,240]:
                    image=marked if width==480 else marked.resize((width,104),Image.Resampling.LANCZOS)
                    received,data=recompress(image,profile)
                    result=decode(tm,received)
                    row={'variant':variant,'strength':strength,'sample':name,'width':width,
                         'exact':result.get('groups')==groups,
                         'wrong_message':result['status']=='decoded' and result.get('groups')!=groups,
                         'bytes':len(data),**result,**metrics(original,marked)}
                    rows.append(row)
                    if name=='v-me-50' and width==480:
                        (args.output/f'v-me-50-{variant}-{strength:g}-profile83.jpg').write_bytes(data)
                        marked.save(args.output/f'v-me-50-{variant}-{strength:g}-before.png')
            selected=[r for r in rows if r['variant']==variant and r['strength']==strength and r['width']==480]
            print(variant,strength,sum(r['exact'] for r in selected),'/',len(selected),'at sample JPEG settings',flush=True)
    result={'scope':'Local JPEG encoder replay of sample tables/subsampling; not a full WeChat transmission',
            'profile':profile,'rows':rows}
    (args.output/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')


if __name__=='__main__':main()
