from pathlib import Path
import argparse,concurrent.futures,hashlib,json,urllib.request,time

BASE='https://cai-watermark.adobe.net/watermarking/trustmark-models/'
parser=argparse.ArgumentParser(description='Fetch official Q/P models with upstream digest verification')
parser.add_argument('--output',type=Path,required=True)
OUT=parser.parse_args().output
OUT.mkdir(parents=True,exist_ok=True)
EXPECTED={
 'trustmark_Q.yaml':'fe40df84a7feeebfceb7a7678d7e6ec6',
 'decoder_Q.ckpt':'4ced90e9cfe13e3295ad082887fe9187',
 'encoder_Q.ckpt':'700328b8754db934b2f6cb5e5185d81f',
 'trustmark_P.yaml':'fe40df84a7feeebfceb7a7678d7e6ec6',
 'decoder_P.ckpt':'9450972bc0c3c217cb7b8220dd2f7a3c',
 'encoder_P.ckpt':'0a18f6de6d57c6ef7dda30ce6154a775'}
jobs=[];sizes={}
for name,expected in EXPECTED.items():
 p=OUT/name
 if p.exists() and hashlib.md5(p.read_bytes()).hexdigest()==expected:
  print('Cached',name,flush=True);continue
 with urllib.request.urlopen(urllib.request.Request(BASE+name,method='HEAD'),timeout=30) as r:size=int(r.headers['Content-Length'])
 sizes[name]=size
 for start in range(0,size,512*1024):jobs.append((name,start,min(size-1,start+512*1024-1)))

def part(job):
 name,start,end=job
 p=OUT/(name+'.part'+str(start))
 if p.exists() and p.stat().st_size==end-start+1:return
 for retry in range(4):
  try:
   request=urllib.request.Request(BASE+name,headers={'Range':f'bytes={start}-{end}'})
   with urllib.request.urlopen(request,timeout=45) as r:
    if sizes[name]>512*1024:assert r.status==206 and r.headers.get('Content-Range','').startswith(f'bytes {start}-{end}/')
    data=r.read()
   assert len(data)==end-start+1
   p.write_bytes(data);return
  except Exception:
   if retry==3:raise
   time.sleep(1)

with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
 for i,_ in enumerate(pool.map(part,jobs),1):
  if i%20==0:print('Chunks',i,'/',len(jobs),flush=True)
for name,size in sizes.items():
 data=b''.join((OUT/(name+'.part'+str(start))).read_bytes() for start in range(0,size,512*1024))
 assert hashlib.md5(data).hexdigest()==EXPECTED[name],name
 (OUT/name).write_bytes(data)
 for start in range(0,size,512*1024):(OUT/(name+'.part'+str(start))).unlink()
 print('Verified',name,len(data),flush=True)
record={name:{'url':BASE+name,'md5':expected,'sha256':hashlib.sha256((OUT/name).read_bytes()).hexdigest(),'bytes':(OUT/name).stat().st_size} for name,expected in EXPECTED.items()}
(OUT/'provenance.json').write_text(json.dumps(record,indent=2)+'\n')
