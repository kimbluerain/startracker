#!/usr/bin/env python3
"""Build minimal offline search rows from the unmodified official GeoNames download."""
import gzip,hashlib,json,sys,zipfile
from pathlib import Path
source=Path(sys.argv[1])
assert hashlib.sha256(source.read_bytes()).hexdigest()=='6d9ec0783783fd382b632dc120a9e1d401020ac98552fc74d05f40e56ea719b2'
assets=Path(__file__).resolve().parents[3]/'main/assets'
rows=[];aliases={}
with zipfile.ZipFile(source) as z:
 for line in z.read('cities15000.txt').decode().splitlines():
  c=line.split('\t');names=list(dict.fromkeys([c[1],c[2]]+c[3].split(',')))
  rows.append('\t'.join([c[0],c[1],','.join(names),c[8],c[4],c[5],c[17],c[14]]))
  if c[0] in ['1808926','1814870','1796236','1816670']:
   aliases[c[0]]=dict(name=c[1],verifiedChineseNames=[n for n in names if n in ['杭州','杭州市','义乌','義烏縣','上海','上海市','北京','北京市']],latitude=c[4],longitude=c[5],zone=c[17])
assert len(rows)==34179
payload=('\n'.join(rows)+'\n').encode()
(assets/'cities15000.data').write_bytes(gzip.compress(payload,mtime=0))
(assets/'geonames-provenance.json').write_text(json.dumps(dict(source='https://download.geonames.org/export/dump/cities15000.zip',retrievedDate='2026-10-23',license='CC BY 4.0',licenseUrl='https://creativecommons.org/licenses/by/4.0/',copyright='GeoNames contributors',sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),rows=len(rows),fields=['geonameid','name','namesAndAliases','country','latitude','longitude','ianaTimezone','population'],changes='Selected fields only; gzip packaging. No coordinates or aliases invented. Elevation omitted: selected city assumes zero until user supplies elevation.',verifiedChineseAliases=aliases),ensure_ascii=False,indent=2))
print(len(rows),len(payload),(assets/'cities15000.data').stat().st_size)
