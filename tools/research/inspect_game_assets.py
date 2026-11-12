from pathlib import Path
import json, collections, sys, argparse
sys.modules['UnityPy.UnityPyBoost']=None
import UnityPy

parser=argparse.ArgumentParser(description='Read-only Unity resource inventory; never modifies the game.')
parser.add_argument('--game-data',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args();root=args.game_data;out=args.output;out.mkdir(parents=True,exist_ok=True)
rows=[]
for name in ['resources.assets','sharedassets0.assets']:
    env=UnityPy.load(str(root/name))
    counts=collections.Counter(obj.type.name for obj in env.objects)
    print(name,dict(counts))
    for obj in env.objects:
        kind=obj.type.name
        if kind not in ['Texture2D','Sprite','TextAsset','MonoScript']:continue
        try:
            if kind=='Sprite':
                rows.append({'file':name,'id':obj.path_id,'type':kind,'name':obj.peek_name(),'note':'Name only; Unity 6 Sprite body not decoded by pinned parser'})
                continue
            data=obj.read(); entry={'file':name,'id':obj.path_id,'type':kind,'name':data.m_Name}
            if kind=='Texture2D':entry.update(width=data.m_Width,height=data.m_Height)
            if kind=='MonoScript':entry.update(className=data.m_ClassName,namespace=data.m_Namespace)
            if kind=='TextAsset':entry.update(length=len(data.m_Script))
            rows.append(entry)
        except Exception as e:print('ERROR',kind,obj.path_id,str(e)[:160])
(out/'inventory.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
for row in rows:
    if row['type']!='MonoScript' or any(x in row.get('className','').lower() for x in ['alien','character','draw','shape']):print(json.dumps(row,ensure_ascii=False))
