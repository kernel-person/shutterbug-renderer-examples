"""Reproducibly package the generated pack without touching previous release archives."""
import argparse
import hashlib
from pathlib import Path
import zipfile

def package(source):
    import io
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(source.rglob('*')):
            if path.is_symlink():raise ValueError('Symlink in generated pack')
            if not path.is_file():continue
            relative=path.relative_to(source).as_posix()
            if relative!='pack.mcmeta' and not (relative.startswith('assets/village_trades/') and path.suffix in ('.png','.json')):
                raise ValueError('Unexpected pack member: '+relative)
            info=zipfile.ZipInfo(relative,date_time=(2026,9,29,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,path.read_bytes())
    return output.getvalue()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    source=Path(__file__).resolve().parents[1]/'public/resourcepack';data=package(source)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('xb') as file:file.write(data)
    print(hashlib.sha256(data).hexdigest()+'  '+str(args.output))
