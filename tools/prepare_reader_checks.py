from pathlib import Path
import io,json,re
from PIL import Image
root=Path(__file__).resolve().parents[1]
out=root/'build/reader-checks/test-assets/reader-checks';out.mkdir(parents=True,exist_ok=True)
fixtures=root/'experiments/recognition/fixtures/android';records=[]
def save(image,name,ids,mode='half',smoke=False,must=False):
 if mode=='half':image=image.resize((240,round(image.height/2)),Image.Resampling.LANCZOS)
 file=name+('.png' if mode=='png' else '.jpg');image.convert('RGB').save(out/file,format='PNG' if mode=='png' else 'JPEG',**({} if mode=='png' else {'quality':50 if mode=='half' else 75,'subsampling':2}))
 # Dense 18-digit integers at half resolution exercise the confirmation flow.
 interactive = mode == 'half' and re.fullmatch(r'number--?\d{18}-half', name) is not None
 records.append(dict(file=file,glyphs=ids,smoke=smoke,must=must,interactive=interactive))
for sample in json.loads((fixtures/'manifest.json').read_text())['samples']:
 for mode in ['png','jpeg','half']:
  save(Image.open(fixtures/sample['file']),sample['file'][:-4]+'-'+mode,sample['glyphs'],mode,True,True)
for c in range(1,7):
 for p in range(4,22):
  gid=f'N{c}P{p}';im=Image.new('RGB',(480,208));im.paste((255,230,179),(190,54),Image.open(fixtures/'glyphs'/(gid+'.png')).getchannel('A'))
  for mode in ['png','half']:save(im,gid+'-'+mode,[gid],mode,p in [4,12,21],False)
for i in range(1,1345):
 gid=f'F{i:04d}';im=Image.new('RGB',(480,208));im.paste((255,230,179),(190,54),Image.open(root/'build/uniform-glyphs/glyphs'/(gid+'.png')).getchannel('A'));save(im,gid,[gid],smoke=i in [1,33,93,306,834,835,902,1047,1301,1302],must=False)
# Previous real WeChat files are copied byte-for-byte, not recompressed here.
for name in ['wechat-P2.jpg','wechat-footer-32.jpg','wechat-footer-64.jpg']:
 (out/name).write_bytes((root/'experiments/watermark/results'/name).read_bytes());records.append(dict(file=name,glyphs=['F0902','F0834','F1305','F1302'],smoke=True,must=True))
for color in ['black','white']:save(Image.new('RGB',(480,208),color),'negative-'+color,[],mode='png',smoke=True)
(out/'manifest.json').write_text(json.dumps(records,indent=2));print('Prepared',len(records),'reader checks')
