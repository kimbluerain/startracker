#!/usr/bin/env python3
"""Reproduce independent JPL vectors; computed times select queries, never expected angles.
Run from any cwd. Requires network; production app never runs this helper.
"""
from pathlib import Path
import json,urllib.parse,urllib.request,csv,datetime,collections,time
root=Path(__file__).resolve().parent
rows=json.loads((root/'computed-event-query-times.json').read_text())
base=json.loads((root/'horizons-requests.json').read_text())[0]['parameters']
groups=collections.defaultdict(list)
for row in rows: groups['event-'+row['site']['id']+'-'+row['target'].lower()].append(row)
extra_sites=[
 dict(id='equator',latitude=0,longitude=0,elevationMeters=0,zone='UTC'),
 dict(id='yiwu-height',latitude=29.31506,longitude=120.07676,elevationMeters=500,eyeHeightMeters=2,zone='Asia/Shanghai'),
 dict(id='kiritimati',latitude=1.8721,longitude=-157.4278,elevationMeters=0,zone='Pacific/Kiritimati'),
 dict(id='tromso',latitude=69.6492,longitude=18.9553,elevationMeters=0,zone='Europe/Oslo')]
for site in extra_sites:
 for target in ('SUN','MOON'):
  for at in ('2026-03-20T12:07:00Z','2026-06-21T00:00:00Z','2026-09-23T12:00:00Z','2026-12-21T00:00:00Z','2026-01-03T10:00:00Z','2026-01-18T20:00:00Z'):
   groups['extra-'+site['id']+'-'+target.lower()].append(dict(site=site,target=target,instantUtc=at))
fixtures=[];requests=[]
for id,group in groups.items():
 params=dict(base);site=group[0]['site']
 params.update(TLIST="'"+','.join(str(datetime.datetime.fromisoformat(r['instantUtc'].replace('Z','+00:00')).timestamp()/86400+2440587.5) for r in group)+"'",COMMAND="'10'" if group[0]['target']=='SUN' else "'301'",SITE_COORD=f"'{site['longitude']},{site['latitude']},{(site['elevationMeters']+site.get('eyeHeightMeters',0))/1000}'")
 url='https://ssd.jpl.nasa.gov/api/horizons.api?'+urllib.parse.urlencode(params)
 raw_file=root/(id+'-raw.json')
 if raw_file.exists():response=json.loads(raw_file.read_text())
 else:
  for attempt in range(4):
   try:
    with urllib.request.urlopen(url,timeout=45) as stream:response=json.load(stream)
    assert '$$SOE' in response.get('result',''),response
    raw_file.write_text(json.dumps(response,indent=2))
    break
   except Exception:
    if attempt==3:raise
    time.sleep(2)
 raw=response['result'].split('$$SOE')[1].split('$$EOE')[0].strip().splitlines()
 assert len(raw)==len(group)
 # Horizons sorts TLIST. Match that independent ordering explicitly.
 group=sorted(group,key=lambda r:r['instantUtc'])
 for row,line in zip(group,raw):
  col=next(csv.reader([line]));out=dict(row)
  out.update(azimuthDeg=float(col[5]),altitudeDeg=float(col[6]),rawFile=raw_file.name)
  fixtures.append(out)
 requests.append(dict(id=id,method='GET',url=url,parameters=params,retrievedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat()))
 print(id,len(group),flush=True)
(root/'supplement-requests.json').write_text(json.dumps(requests,indent=2))
(root/'supplement-fixtures.json').write_text(json.dumps(dict(metadata=dict(source='JPL Horizons DE441, UTC, AIRLESS, observer center',eventUse='Independent azimuth at computed exact event instant; these angles do not validate event time.',futureCaveat='2050 freezes last leap-second and EOP prediction; model regression only.'),fixtures=fixtures),indent=2))
