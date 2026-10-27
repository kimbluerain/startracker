"""Independent HYG proper-motion -> airless AltAz oracle; no production code imported.
Usage: python generate-stars-altaz.py --reference-dir <repo>/android/app/src/test/resources/reference/galactic
All outputs remain in this script's directory. Existing fixed IERS table is read-only.
"""
from pathlib import Path
import argparse,csv,datetime,hashlib,importlib.metadata,json,math,warnings
import astropy, astropy.units as u, erfa
from astropy.coordinates import SkyCoord,AltAz,EarthLocation
from astropy.coordinates.builtin_frames.utils import get_polar_motion
from astropy.time import Time
from astropy.utils import iers
root=Path(__file__).resolve().parent
ap=argparse.ArgumentParser();ap.add_argument('--reference-dir',required=True);args=ap.parse_args()
reference=Path(args.reference_dir)
names=['astropy','pyerfa','astropy-iers-data','numpy','packaging','PyYAML']
versions={n:importlib.metadata.version(n) for n in names}
expected={'astropy':'7.2.0','pyerfa':'2.0.1.5','astropy-iers-data':'0.2026.10.19.0.52.1','numpy':'2.5.4','packaging':'26.3','PyYAML':'6.0.3'}
assert versions==expected,(versions,expected)
iers.conf.auto_download=False; iers.conf.auto_max_age=None; iers.conf.iers_degraded_accuracy='warn'
table_path=reference/'iers-finals2000A-2026-10-19.all'
sha=hashlib.sha256(table_path.read_bytes()).hexdigest()
assert sha=='0baf00f67a39ab362a3e4f07d2a1f084842178523f56bcf5a861e7ac084d411a'
table=iers.IERS_A.open(str(table_path))
rows=list(csv.DictReader((root/'hyg-v41-25stars-raw.csv').open()))
assert len(rows)==25 and len({r['hip'] for r in rows})==25
sites=[dict(id='hangzhou',latitude=30.2741,longitude=120.1551,elevationMeters=0),dict(id='sydney',latitude=-33.8688,longitude=151.2093,elevationMeters=0)]
dates=['2000-02-29T12:00:00','2026-10-26T12:00:00','2050-06-21T12:00:00']
fixtures=[]
with iers.earth_orientation_table.set(table):
 for site in sites:
  location=EarthLocation.from_geodetic(lon=site['longitude']*u.deg,lat=site['latitude']*u.deg,height=site['elevationMeters']*u.m,ellipsoid='WGS84')
  for stamp in dates:
   for row in rows:
    with warnings.catch_warnings(record=True) as caught:
     warnings.simplefilter('always')
     t=Time(stamp,scale='utc')
     source=SkyCoord(ra=float(row['ra'])*15*u.deg,dec=float(row['dec'])*u.deg,pm_ra_cosdec=float(row['pmra'])*u.mas/u.yr,pm_dec=float(row['pmdec'])*u.mas/u.yr,obstime=Time('J2000',scale='tt'),frame='icrs')
     propagated=source.apply_space_motion(new_obstime=t)
     # Drop velocity differentials only after applying motion; AltAz needs direction, not velocity.
     direction=SkyCoord(ra=propagated.ra,dec=propagated.dec,frame='icrs')
     aa=direction.transform_to(AltAz(obstime=t,location=location,pressure=0*u.hPa,temperature=0*u.deg_C,relative_humidity=0,obswl=0.55*u.micron))
     dut1,status=table.ut1_utc(t,return_status=True);xp,yp=get_polar_motion(t)
     taiutc=(t.tai.jd1-t.jd1+t.tai.jd2-t.jd2)*86400
    az=aa.az.degree; alt=aa.alt.degree; azr,altr=map(math.radians,(az,alt))
    fixtures.append(dict(hip=int(row['hip']),proper=row['proper'],site=site,instantUtc=stamp+'Z',sourceRaDeg=float(row['ra'])*15,sourceDecDeg=float(row['dec']),pmRaCosDecMasYr=float(row['pmra']),pmDecMasYr=float(row['pmdec']),propagatedIcrsRaDeg=propagated.ra.degree,propagatedIcrsDecDeg=propagated.dec.degree,azimuthDeg=az,altitudeDeg=alt,enu=[math.cos(altr)*math.sin(azr),math.cos(altr)*math.cos(azr),math.sin(altr)],dut1Seconds=float(dut1.to_value(u.s)),iersStatus=int(status),polarMotionUsedArcsec=[float(xp*u.rad.to(u.arcsec)),float(yp*u.rad.to(u.arcsec))],taiMinusUtcSeconds=taiutc,warnings=sorted(set(str(w.message) for w in caught))))
assert len(fixtures)==150
assert all(0<=f['azimuthDeg']<360 and -90<=f['altitudeDeg']<=90 for f in fixtures)
assert all(abs(sum(v*v for v in f['enu'])-1)<1e-14 for f in fixtures)
metadata=dict(generatedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),versions=versions,erfaVersion=erfa.version.erfa_version,sofaVersion=erfa.version.sofa_version,source='Astropy SkyCoord.apply_space_motion / ICRS -> AltAz with ERFA; no production code or Astronomy Engine used',inputSha256=hashlib.sha256((root/'hyg-v41-25stars-raw.csv').read_bytes()).hexdigest(),sourceEpoch='J2000.0 TT',properMotion='pm_ra_cosdec and pm_dec, milliarcseconds per Julian year',pressureHpa=0,refraction='disabled',distance='Unit-spherical input; no physical distance, radial velocity, or finite-distance parallax. ERFA pmsafe may override a missing distance internally; those warnings are retained. This is an angular-proper-motion reference, not full physical space motion.',sites='Synthetic WGS84 ellipsoid height 0m',iers=dict(tableFile=table_path.name,sha256=sha,sourceUrl='https://datacenter.iers.org/data/9/finals2000A.all',autoDownload=False,statusMeaning={'0':'measured IERS B','1':'measured IERS A','2':'predicted IERS A','-1':'before table','-2':'beyond table'},futurePolicy='2050 outside table clips UT1-UTC to boundary; polar motion uses Astropy 50-year mean 0.035,0.29 arcsec; latest known leap seconds. Frozen model fixtures, not actual future UTC truth.'),suggestedAcceptance='Spherical angular separation of ENU <=0.05 degrees; do not compare azimuth alone near zenith.')
(root/'stars-altaz-fixtures.json').write_text(json.dumps(dict(metadata=metadata,fixtures=fixtures),indent=2)+'\n')
(root/'generation-report.json').write_text(json.dumps(dict(rows=len(fixtures),metadata=metadata,warningMessages=sorted(set(w for f in fixtures for w in f['warnings']))),indent=2)+'\n')
print(json.dumps(dict(rows=len(fixtures),versions=versions,altitudeRange=[min(f['altitudeDeg'] for f in fixtures),max(f['altitudeDeg'] for f in fixtures)],warningMessages=sorted(set(w for f in fixtures for w in f['warnings']))),indent=2))
