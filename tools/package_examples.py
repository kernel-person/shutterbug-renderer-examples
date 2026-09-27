"""Package ONLY the three public examples; no provider, native library, test probe or credentials."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile

MODULES=(('postcards','RendererPostcards','postcards'),
         ('redstone-camera','RendererRedstoneCamera','camera'),
         ('painters-easel','RendererPaintersEasel','easel'))

def verify_jar(data,package):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names=jar.namelist()
        if len(names)!=len(set(names)) or 'plugin.yml' not in names:
            raise ValueError('Missing plugin metadata or duplicate member')
        if b'depend: [ShutterBugRenderer]' not in jar.read('plugin.yml'):
            raise ValueError('Provider dependency must be explicit')
        for name in names:
            if name.endswith('/'): continue
            allowed=(name=='plugin.yml' or name.startswith('META-INF/') or
                     (name.startswith('io/github/kernelperson/'+package+'/') and name.endswith('.class')))
            if not allowed or name.lower().endswith(('.dll','.so','.dylib','.key','.pem')):
                raise ValueError('Unexpected JAR member: '+name)
            if any(secret in jar.read(name) for secret in (b'-----BEGIN PRIVATE KEY-----',b'-----BEGIN OPENSSH PRIVATE KEY-----',b'ric-license-platform-production.')):
                raise ValueError('Sensitive build input in JAR')

def make_zip(files):
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,9,27,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,data)
    return output.getvalue()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    def git(*command): return subprocess.check_output(['git',*command],cwd=root,text=True).strip()
    if git('status','--porcelain'): raise SystemExit('Commit reviewed source before packaging')
    files={name:(root/name).read_bytes() for name in ('README.md','LICENSE','docs/acceptance.md')}
    for module,name,package in MODULES:
        data=(root/module/'target'/(name+'.jar')).read_bytes()
        verify_jar(data,package);files[name+'.jar']=data
    manifest={'version':'1.0.0','sourceCommit':git('rev-parse','HEAD'),
              'source':'https://github.com/kernel-person/shutterbug-renderer-examples',
              'providerIncluded':False,'testProbeIncluded':False,
              'sha256':{name:hashlib.sha256(data).hexdigest() for name,data in sorted(files.items())}}
    files['manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    packed=make_zip(files)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('xb') as output: output.write(packed)
    checksum=hashlib.sha256(packed).hexdigest()
    args.output.with_suffix('.zip.sha256').write_text(checksum+'  '+args.output.name+'\n')
    print(checksum+'  '+str(args.output))

if __name__=='__main__': main()
