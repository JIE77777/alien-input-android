#!/usr/bin/env python3
"""Restore pinned external sources/data without touching existing user modifications."""
import argparse, hashlib, json, os, shutil, subprocess, urllib.parse, urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TRIME='b5cad1a49095a3652e9188982de400ca3a653f37'
DOUBLE='01a13287cbd27819be1c34fa1ddc1b3643d5001b'

def run(*args,cwd=None):subprocess.run(args,cwd=cwd,check=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def restore(url,target,expected,cache=None,offline=False):
    if target.exists():
        if sha(target)!=expected:raise SystemExit('Existing dependency differs; not overwriting: '+str(target))
        return
    target.parent.mkdir(parents=True,exist_ok=True)
    if cache and cache.exists() and sha(cache)==expected:shutil.copyfile(cache,target);return
    if offline:raise SystemExit('Missing offline dependency: '+str(target))
    temporary=target.with_name(target.name+'.download')
    with urllib.request.urlopen(url,timeout=120) as response,temporary.open('wb') as output:shutil.copyfileobj(response,output)
    if sha(temporary)!=expected:temporary.unlink();raise SystemExit('Dependency hash mismatch: '+target.name)
    temporary.replace(target)

def checkout(relative,url,commit,cache,offline):
    target=ROOT/relative
    if not target.exists():
        local=cache/relative if cache else None
        if local and (local/'.git').exists():run('git','clone','--no-hardlinks','--no-checkout',str(local),str(target))
        else:
            if offline:raise SystemExit('Missing checkout cache: '+relative)
            run('git','init',str(target));run('git','remote','add','origin',url,cwd=target)
            run('git','fetch','--depth','1','origin',commit,cwd=target)
        run('git','checkout','--detach',commit,cwd=target)
    actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=target,text=True).strip()
    if actual!=commit:raise SystemExit('Checkout differs from pinned commit: '+relative)
    return target

def main():
    p=argparse.ArgumentParser();p.add_argument('--source-cache',type=Path);p.add_argument('--offline',action='store_true');a=p.parse_args()
    trime=checkout('trime','https://github.com/osfans/trime.git',TRIME,a.source_cache,a.offline)
    checkout('vendor/rime-double-pinyin','https://github.com/rime/rime-double-pinyin.git',DOUBLE,a.source_cache,a.offline)
    patch=ROOT/'patches/trime-host.patch'
    applied=subprocess.run(['git','apply','--reverse','--check',str(patch)],cwd=trime,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
    if not applied:run('git','apply','--check',str(patch),cwd=trime);run('git','apply',str(patch),cwd=trime)
    relative=Path('app/src/test/java/com/osfans/trime/data/theme/AlienKeyboardIntegrationTest.kt')
    target=trime/relative;target.parent.mkdir(parents=True,exist_ok=True)
    source=ROOT/'tests/trime/AlienKeyboardIntegrationTest.kt'
    if target.exists() and target.read_bytes()!=source.read_bytes():raise SystemExit('Modified theme test; not overwriting')
    shutil.copyfile(source,target)
    vendor=ROOT/'vendor/rime-pure';metadata=json.loads((vendor/'upstream.json').read_text())
    for item in metadata['files']:
        url='https://raw.githubusercontent.com/SivanLaai/rime-pure/'+metadata['commit']+'/'+urllib.parse.quote(item['path'],safe='/')
        cache=a.source_cache/'vendor/rime-pure'/item['file'] if a.source_cache else None
        restore(url,vendor/item['file'],item['sha256'],cache,a.offline)
    env=Path(os.environ.get('DEV_ENV_ROOT',Path.home()/'project/dev-env'))
    restore('https://github.com/osfans/trime/releases/download/v3.3.13/com.osfans.trime-v3.3.13-0-gb5cad1a4-arm64-v8a-release.apk',
            env/'downloads/trime-v3.3.13-arm64.apk','e7535ea421a25962eb2ea37c46ff867838e69d33ba6d935876ab84d108de5020',offline=a.offline)
    print('Pinned sources, host patch, theme tests, dictionaries and official native assets ready')

if __name__=='__main__':main()
