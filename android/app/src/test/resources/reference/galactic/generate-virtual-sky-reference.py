"""Independent dense visual reference; same pinned environment/EOP as 45-point fixtures.
No Android/Astronomy Engine code is loaded. Projection uses explicit true ENU dot products.
"""
from pathlib import Path
import json, math, importlib.metadata
import astropy.units as u
from astropy.coordinates import SkyCoord, AltAz, EarthLocation
from astropy.time import Time
from astropy.utils import iers
root=Path(__file__).resolve().parent
versions={p:importlib.metadata.version(p) for p in ('astropy','pyerfa','astropy-iers-data','numpy','packaging','PyYAML')}
assert versions==json.loads((root/'galactic-altaz-fixtures.json').read_text())['metadata']['versions']
iers.conf.auto_download=False
iers.conf.auto_max_age=None
iers.conf.iers_degraded_accuracy='warn'
view=dict(width=800,height=500,horizontalFovDeg=100.0,azimuthDeg=235.0,altitudeDeg=0.0)
at='2026-10-23T12:00:00Z'
site=dict(latitude=30.2741,longitude=120.1551,zone='Asia/Shanghai',elevationMeters=0)
f=view['width']/2/math.tan(math.radians(view['horizontalFovDeg']/2))
a=math.radians(view['azimuthDeg'])
forward=(math.sin(a),math.cos(a),0); right=(math.cos(a),-math.sin(a),0)
rows=[]
with iers.earth_orientation_table.set(iers.IERS_A.open(str(root/'iers-finals2000A-2026-10-19.all'))):
 for name,latitude in [('plane',0),('south',-5),('north',5)]:
  coords=SkyCoord(l=list(range(360))*u.deg,b=[latitude]*360*u.deg,frame='galactic')
  aa=coords.transform_to(AltAz(obstime=Time(at),location=EarthLocation.from_geodetic(site['longitude']*u.deg,site['latitude']*u.deg,0*u.m),pressure=0*u.hPa))
  for i,(az,alt) in enumerate(zip(aa.az.degree,aa.alt.degree)):
   az,alt=map(math.radians,(az,alt)); enu=(math.sin(az)*math.cos(alt),math.cos(az)*math.cos(alt),math.sin(alt))
   z=sum(v*w for v,w in zip(enu,forward)); x=sum(v*w for v,w in zip(enu,right))
   if z<=0: continue
   px=view['width']/2+f*x/z; py=view['height']/2-f*enu[2]/z
   # Avoid ambiguous edge/horizon classification. These are visible interior anchors.
   if 2<px<view['width']-2 and 2<py<view['height']-2 and abs(enu[2])>0.001:
    rows.append(dict(line=name,index=i,x=px,y=py,belowHorizon=enu[2]<0))
out=dict(metadata=dict(source='Independent Astropy 7.2.0 AIRLESS Galactic sky + explicit ENU projection',versions=versions,iers='iers-finals2000A-2026-10-19.all',instantUtc=at,site=site,view=view,tolerancePixels=0.2),anchors=rows)
(root/'virtual-sky-reference.json').write_text(json.dumps(out,indent=2)+'\n')
print('Saved independent visible anchors:',len(rows))
