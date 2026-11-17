import argparse,io,json,random,hashlib,time
from pathlib import Path
from PIL import Image
from recognize import Recognizer,positions
from numeral_codec import DYNAMIC_IDS,encode_integer,integer_candidate

def jpeg(im,q):
 b=io.BytesIO();im.convert('RGB').save(b,format='JPEG',quality=q,subsampling=2);b.seek(0);return Image.open(b).convert('RGB')

def variants(im):
 yield 'png',im.convert('RGB')
 yield 'jpeg75',jpeg(im,75)
 yield 'half_jpeg50',jpeg(im.resize((240,round(im.height*240/im.width)),Image.Resampling.LANCZOS),50)

def main():
 p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--uniform',type=Path,required=True);p.add_argument('--original',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--coarse-numbers',action='store_true');p.add_argument('--skip-static',action='store_true');a=p.parse_args()
 a.output.mkdir(parents=True,exist_ok=True);t=time.perf_counter()
 e=Recognizer([a.uniform,a.original],generated_dir=a.fixtures/'glyphs',numeric_detail=not a.coarse_numbers)
 manifest=json.loads((a.fixtures/'manifest.json').read_text());rows=[];summaries=[]
 def check(source,expected,suite,name,variant,expected_rows=None,number=None):
  r=e.recognize(source);accepted=r['status']=='recognized';pred=r.get('glyphs');top=[s['id'] for row in r.get('rows',[]) for s in row.get('best',{}).get('symbols',[])]
  row=dict(suite=suite,name=name,variant=variant,status=r['status'],exact=pred==expected and accepted,wrong=accepted and pred!=expected,expected=expected,predicted=pred,top=top,row_counts=[len(row.get('best',{}).get('symbols',[])) for row in r.get('rows',[])],seconds=r.get('seconds',0))
  if expected_rows is not None:row['row_counts_correct']=row['row_counts']==expected_rows
  if number is not None:row['expected_number']=number;row['numeric_correct']=accepted and any(c['start']==0 and c['end']==len(expected) and c['decimal']==number for c in r['numeric_candidates'])
  rows.append(row)
  if (variant=='png' and suite=='android_messages') or row['wrong']:(a.output/(name+'-'+variant+'-decode.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2))
 def summarize(suite):
  for variant in ['png','jpeg75','half_jpeg50']:
   rr=[r for r in rows if r['suite']==suite and r['variant']==variant]
   if not rr:continue
   summary=dict(suite=suite,variant=variant,total=len(rr),correct=sum(r['exact'] for r in rr),wrong=sum(r['wrong'] for r in rr),rejected=sum(r['status']!='recognized' for r in rr),top_correct=sum(r['top']==r['expected'] for r in rr))
   summaries.append(summary);print(json.dumps(summary),flush=True)
  (a.output/'results.partial.json').write_text(json.dumps(dict(summary=summaries,rows=rows),indent=2))
 for gid in DYNAMIC_IDS:
  mask=Image.open(a.fixtures/'glyphs'/(gid+'.png')).getchannel('A');im=Image.new('RGB',(480,208));im.paste((255,230,179),(190,54),mask)
  for name,received in variants(im):check(received,[gid],'dynamic_glyphs',gid,name,[1])
 summarize('dynamic_glyphs')
 for sample in manifest['samples']:
  im=Image.open(a.fixtures/sample['file'])
  for name,received in variants(im):check(received,sample['glyphs'],'android_messages',Path(sample['file']).stem,name,sample['row_counts'],sample.get('number'))
 summarize('android_messages')
 # Supported-bank regression: all static glyphs at half width + JPEG50.
 for i in range(0 if a.skip_static else 1344):
  gid=e.ids[i];im=Image.new('RGB',(480,208));im.paste((255,230,179),(190,54),e.masks[i]);received=jpeg(im.resize((240,104),Image.Resampling.LANCZOS),50)
  check(received,[gid],'static_regression',gid,'half_jpeg50',[1])
 summarize('static_regression')
 rng=random.Random(20261119)
 for number in [0,1,-1,1234,-1234,123456,10**18-1,-10**18+1]+[rng.randrange(-10**18+1,10**18) for _ in range(1000)]:assert integer_candidate(encode_integer(number))==str(number)
 for ids in [['F1302','F1302'],['F1301','F1302'],['F1302','F1305'],['F0098','F1301'],['N6P21']]:assert integer_candidate(ids) is None
 result=dict(summary=summaries,rows=rows,number_round_trips=1008,noncanonical_rejections=5,numeric_detail=not a.coarse_numbers,total_seconds=round(time.perf_counter()-t,1))
 (a.output/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('DONE '+str(result['total_seconds']),flush=True)
if __name__=='__main__':main()
