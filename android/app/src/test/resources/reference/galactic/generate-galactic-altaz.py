"""Independent offline oracle; run with the pinned requirements in this directory."""
from pathlib import Path
import datetime, hashlib, importlib.metadata, json, math, shutil, warnings
import astropy.units as u
import astropy, erfa, numpy as np
from astropy.coordinates import SkyCoord, AltAz, EarthLocation
from astropy.coordinates.builtin_frames.utils import get_polar_motion
from astropy.time import Time
from astropy.utils import iers

ROOT = Path(__file__).resolve().parent
VERSIONS = {name: importlib.metadata.version(name) for name in ['astropy','pyerfa','astropy-iers-data','numpy','packaging','PyYAML']}
EXPECTED = {'astropy':'7.2.0','pyerfa':'2.0.1.5','astropy-iers-data':'0.2026.10.19.0.52.1','numpy':'2.5.4','packaging':'26.3','PyYAML':'6.0.3'}
assert VERSIONS == EXPECTED, VERSIONS
# No IERS or leap-second network refresh is permitted during generation.
iers.conf.auto_download = False
iers.conf.auto_max_age = None
iers.conf.iers_degraded_accuracy = 'warn'
TABLE = ROOT / 'iers-finals2000A-2026-10-19.all'
if not TABLE.exists(): shutil.copyfile(iers.IERS_A_FILE, TABLE)
assert hashlib.sha256(TABLE.read_bytes()).hexdigest() == hashlib.sha256(Path(iers.IERS_A_FILE).read_bytes()).hexdigest()
table = iers.IERS_A.open(str(TABLE))
sites = [dict(id='hangzhou',latitude=30.2741,longitude=120.1551,elevationMeters=0,zone='Asia/Shanghai'),dict(id='sydney',latitude=-33.8688,longitude=151.2093,elevationMeters=0,zone='Australia/Sydney'),dict(id='london',latitude=51.5074,longitude=-0.1278,elevationMeters=0,zone='Europe/London')]
inputs = [(f'galactic_l{l}_b0',SkyCoord(l=l*u.deg,b=0*u.deg,frame='galactic'),dict(galacticLongitudeDeg=l,galacticLatitudeDeg=0)) for l in [0,90,180,270]]
inputs.append(('sgr_a_star_radio_icrs_j2000',SkyCoord(ra=266.41681662499997*u.deg,dec=-29.00782497222222*u.deg,frame='icrs'),dict(icrsRaDeg=266.41681662499997,icrsDecDeg=-29.00782497222222)))
fixtures = []
with iers.earth_orientation_table.set(table):
 for site in sites:
  location = EarthLocation.from_geodetic(lon=site['longitude']*u.deg,lat=site['latitude']*u.deg,height=site['elevationMeters']*u.m,ellipsoid='WGS84')
  for instant in ['2000-02-29T12:00:00','2026-10-23T12:00:00','2050-06-21T12:00:00']:
   for name,coordinate,source in inputs:
    with warnings.catch_warnings(record=True) as caught:
     warnings.simplefilter('always')
     t=Time(instant,scale='utc')
     dut1,status=table.ut1_utc(t,return_status=True)
     xp,yp=get_polar_motion(t)
     aa=coordinate.transform_to(AltAz(obstime=t,location=location,pressure=0*u.hPa,temperature=0*u.deg_C,relative_humidity=0,obswl=0.55*u.micron))
     icrs=coordinate.icrs
     taiutc=(t.tai.jd1-t.jd1+t.tai.jd2-t.jd2)*86400
    az,alt=aa.az.degree,aa.alt.degree
    azr,altr=map(math.radians,(az,alt))
    fixtures.append(dict(id=name,sourceCoordinate=source,site=site,instantUtc=instant+'Z',icrsRaDeg=icrs.ra.degree,icrsDecDeg=icrs.dec.degree,azimuthDeg=az,altitudeDeg=alt,enu=[math.cos(altr)*math.sin(azr),math.cos(altr)*math.cos(azr),math.sin(altr)],dut1Seconds=float(dut1.to_value(u.s)),iersStatus=int(status),polarMotionUsedArcsec=[float(xp*u.rad.to(u.arcsec)),float(yp*u.rad.to(u.arcsec))],taiMinusUtcSeconds=taiutc,warnings=sorted(set(str(w.message) for w in caught))))
assert len(fixtures)==45
assert all(0<=x['azimuthDeg']<360 and -90<=x['altitudeDeg']<=90 for x in fixtures)
assert all(abs(sum(v*v for v in x['enu'])-1)<1e-14 for x in fixtures)
meta=dict(generatedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),versions=VERSIONS,erfaVersion=erfa.version.erfa_version,sofaVersion=erfa.version.sofa_version,source='Astropy coordinates Galactic/ICRS -> AltAz, ERFA apparent-place transforms; no Astronomy Engine used',pressureHpa=0,refraction='disabled',siteHeight='WGS84 ellipsoid height 0 m, not a claim of sea-level orthometric height at these cities',distance='Unit-spherical fixed directions at infinity; no proper motion, radial velocity or finite-distance parallax',iers=dict(tableFile=TABLE.name,sha256=hashlib.sha256(TABLE.read_bytes()).hexdigest(),package='astropy-iers-data=='+VERSIONS['astropy-iers-data'],sourceUrl='https://datacenter.iers.org/data/9/finals2000A.all',bundledSource='https://github.com/astropy/astropy-iers-data',mjdRange=[float(table['MJD'][0].value),float(table['MJD'][-1].value)],auto_download=False,auto_max_age=None,degraded_accuracy='warn',statusMeaning={'0':'IERS B measured','1':'IERS A measured','2':'IERS A predicted','-1':'before available range','-2':'beyond available range'},futurePolicy='Beyond-table UT1-UTC is clipped to boundary; polar motion uses Astropy 50-year mean 0.035,0.29 arcsec. ERFA UTC future conversion uses its latest leap-second table. Rows retain warnings; these are frozen model fixtures, not actual future UTC truth.'),sgrA=dict(adoptedIcrsRaDeg=266.41681662499997,adoptedIcrsDecDeg=-29.00782497222222,source='SIMBAD ICRS epoch J2000 radio coordinate; 2011AJ....142...35P',sourceUrl='https://simbad.cds.unistra.fr/simbad/sim-id?Ident=Sgr+A%2A', rawSourceFile='simbad-sgr-a-radio-raw.json',paper='https://ui.adsabs.harvard.edu/abs/2011AJ....142...35P',caveat='Fixed physical radio reference at J2000; not proper-motion propagated Sgr A*. Different from Galactic l=0,b=0.'))
(ROOT/'galactic-altaz-fixtures.json').write_text(json.dumps(dict(metadata=meta,fixtures=fixtures),indent=2)+'\n')
(ROOT/'galactic-altaz-requirements.txt').write_text('\n'.join(k+'=='+v for k,v in EXPECTED.items())+'\n')
(ROOT/'erfa-leap-seconds.json').write_text(json.dumps(dict(columns=list(erfa.leap_seconds.get().dtype.names),rows=erfa.leap_seconds.get().tolist()),indent=2)+'\n')
print(json.dumps(dict(rows=len(fixtures),metadata=meta,altitudeRange=[min(x['altitudeDeg'] for x in fixtures),max(x['altitudeDeg'] for x in fixtures)],warningMessages=sorted(set(w for x in fixtures for w in x['warnings']))),indent=2))
