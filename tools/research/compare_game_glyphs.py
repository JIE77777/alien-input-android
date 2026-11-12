from pathlib import Path
import sys,json,hashlib,collections,argparse,re
sys.modules['UnityPy.UnityPyBoost']=None
import UnityPy
from PIL import Image
parser=argparse.ArgumentParser(description='Compare original texture alpha masks with application glyph assets.')
parser.add_argument('--game-data',required=True,type=Path)
parser.add_argument('--glyph-dir',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args();root=args.game_data;current=args.glyph_dir;out=args.output
raw=out/'raw-reference';raw.mkdir(parents=True,exist_ok=True)
def sha(b):return hashlib.sha256(b).hexdigest()
index=collections.defaultdict(list);existing={}
for file in current.glob('*.png'):
    if not re.fullmatch(r'F\d{4}',file.stem):continue
    img=Image.open(file).convert('RGBA');a=img.getchannel('A');h=sha(a.tobytes());index[h].append(file.stem)
    existing[file.stem]={'alpha_sha256':h,'size':list(img.size),'bbox':a.getbbox()}
env=UnityPy.load(str(root/'resources.assets'));mapping=[];examples=[]
selected={'you','food','eat','hello','me','big','small','talk','word','notaword'}
for obj in env.objects:
    if obj.type.name=='TextAsset' and obj.peek_name() in ['newalien','alieninfo','geometry']:
        data=obj.read();s=data.m_Script
        if isinstance(s,bytes):(raw/(data.m_Name+'.txt')).write_bytes(s)
        else:(raw/(data.m_Name+'.txt')).write_text(s,errors='surrogateescape')
    if obj.type.name!='Texture2D':continue
    data=obj.read()
    if (data.m_Width,data.m_Height)!=(100,100):continue
    img=data.image.convert('RGBA');a=img.getchannel('A');h=sha(a.tobytes())
    row={'game_name':data.m_Name,'path_id':obj.path_id,'alpha_sha256':h,'matching_ids':index.get(h,[]),'bbox':a.getbbox()}
    mapping.append(row)
    if data.m_Name in selected:
        img.save(raw/(data.m_Name+'.png'));examples.append(row)
(out/'alpha-mapping.json').write_text(json.dumps(mapping,ensure_ascii=False,indent=2))
(out/'current-glyph-metrics.json').write_text(json.dumps(existing,indent=2))
matched={id for r in mapping for id in r['matching_ids']}
print('Game 100x100 textures:',len(mapping),'Current glyphs:',len(existing),'Exact alpha-matched IDs:',len(matched))
print(json.dumps(examples,ensure_ascii=False,indent=2))
for name in ['newalien','alieninfo','geometry']:
    p=raw/(name+'.txt')
    print('TEXT SAMPLE',name,p.read_text(errors='replace')[:600])
