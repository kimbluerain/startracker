# Galactic numerical and render reference provenance

Collected 2026-10-23. Independent of production Astronomy Engine. All supplied scientific files were checked against the research folder's SHA-256 manifest before copying. `SHA256SUMS.json` records this retained subset plus licenses and the new dense render reference. Scoped `.gitattributes` preserves the raw ERFA whitespace and fixed-width IERS padding; source hashes are not changed by formatting.

## Fixed rotation and physical center

- `galactic-fixtures.json`: **8** canonical Galactic→ICRS directions from ERFA 2.0.1 `eraG2icrs`; source, matrix, vector components and source URL are in JSON. The (l=320°, b=−45°) row reproduces the ERFA published test. Retained source includes the full ERFA license; binary attribution is also in `src/main/assets/ERFA-LICENSE.txt`.
- `erfa-g2icrs-source.c.txt`: https://raw.githubusercontent.com/liberfa/erfa/v2.0.1/src/g2icrs.c . `erfa-g2icrs-published-test.txt` retains the independent published test from the same release's `t_erfa_c.c`.
- `astropy-galactic-source.py.txt`: https://raw.githubusercontent.com/astropy/astropy/v7.2.0/astropy/coordinates/builtin_frames/galactic.py . Its FK4/FK5-linked Galactic transform differs from ERFA's canonical ICRS transform by far less than the 0.05° acceptance limit. Full BSD notice: `ASTROPY-LICENSE.rst`, fetched from the same v7.2.0 tag.
- `simbad-sgr-a-request.json` and `simbad-sgr-a-radio-raw.json`: exact SIMBAD TAP request/response for physical **radio Sgr A***, ICRS epoch J2000 RA **266.416816625°**, Dec **−29.00782497222222°**, coordinate bibliography **2011AJ....142...35P**. Primary record: https://simbad.cds.unistra.fr/simbad/sim-id?Ident=Sgr+A%2A ; paper: https://ui.adsabs.harvard.edu/abs/2011AJ....142...35P . No proper motion is propagated.
- Geometric l=0°, b=0° is RA 266.4049948010461°, Dec −28.936173960138692° under the canonical rotation. It is **0.072393596°** from the adopted physical radio center. Astropy `galcen_coord` (266.4051°, −28.936175°) is not used as the radio source.

## Independent horizon fixtures

`galactic-altaz-fixtures.json` contains **45** Astropy/ERFA apparent AIRLESS AltAz unit vectors: four plane points + Sgr A*, Hangzhou/Sydney/London, 2000-02-29 / 2026-10-23 / 2050-06-21 at 12:00 UTC. Compare **spherical vector separation**, not azimuth alone near zenith. `pressure=0 hPa`, no refraction; directions at infinity, no proper motion, velocity or parallax. WGS84 **ellipsoid height 0 m** is a synthetic site convention, not measured orthometric sea-level height.

Production uses canonical ICRS fixed vectors, approximates them as J2000 mean equatorial at the app's 0.05° tolerance, then uses the pinned Astronomy Engine precession/nutation/Earth rotation. It omits annual aberration, light deflection and polar motion. These geometric directions differ slightly from the apparent-place oracle; tests report the actual maximum, not a claim of exact Astropy equivalence.

Regenerator: `generate-galactic-altaz.py`, Python environment pinned by `galactic-altaz-requirements.txt` (Astropy 7.2.0, pyerfa 2.0.1.5 / ERFA 2.0.1 / SOFA 20231011 and all transitive versions). `galactic-altaz-generation-report.json`, `galactic-altaz-reproduction-baseline.json` preserve the research reproduction evidence. No Python dependency is added to Android.

Frozen IERS table `iers-finals2000A-2026-10-19.all` has SHA-256 `0baf00f67a39ab362a3e4f07d2a1f084842178523f56bcf5a861e7ac084d411a`, source https://datacenter.iers.org/data/9/finals2000A.all as bundled with astropy-iers-data 0.2026.10.19.0.52.1. Coverage MJD 41684–61682. The retained `erfa-leap-seconds.json` records leap seconds. Regeneration forbids auto-download. Year 2000 uses measured IERS B data; 2026 uses predicted IERS A data; **2050 is outside coverage**, clips DUT1 to the boundary, uses Astropy's mean polar motion and latest known leap seconds. Warnings are retained per row. These are frozen model regressions, **not actual future UTC truth**.

## Fixed-date visual regression

`generate-virtual-sky-reference.py` uses the same pinned Astropy environment and frozen IERS table to independently project a dense Galactic plane and ±5° band into a synthetic ENU camera: Hangzhou, 2026-10-23T12:00:00Z, aim az235°/alt0°, horizontal FOV100°, 800×500. It stores **229 visible interior anchors** in `virtual-sky-reference.json`; no Android classes or production formulas are loaded. The view's simple ENU basis is explicit. Tests require rendered segment endpoints to agree within **0.2 px** and preserve below-horizon style. Synthetic band width is a recognition aid, not a measured luminous outline.

`virtual-sky-reviewed.svg` is the production drawing captured and visually reviewed after comparison with those independent anchors. It is a regression baseline, not a scientific oracle. `GalacticSnapshotTest` writes the current production SVG to `app/build/outputs/galactic-snapshot.svg`, then compares it with the retained baseline. To deliberately update after reviewing a change: `SKYTRAILS_RECORD_SNAPSHOT=1 python3 scripts/build-android.py :app:testDebugUnitTest --tests '*GalacticSnapshotTest' --rerun-tasks` from repository root, review the image, then rerun without that variable.

The SVG is an actual offline render of production geometry, not an Android UI/device screenshot. Task5 supplies the interactive host. No licensed or unregistered Galaxy photograph is used.
