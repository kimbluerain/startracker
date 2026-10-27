# Representative star catalog

The offline preview includes 25 real stars and four deliberately limited patterns: Big Dipper (an asterism in Ursa Major), Orion, Cassiopeia and Southern Cross. The names and simplified line connections are authored for this app; they are not borrowed constellation artwork or official constellation boundaries.

## Source and attribution

HYG v4.1 by David Nash / Astronexus, pinned Git commit `c7f7f883fe678cc7680169a50ccd7dcc49b060ce`:
https://github.com/astronexus/HYG-Database/tree/c7f7f883fe678cc7680169a50ccd7dcc49b060ce

Raw upstream table:
https://raw.githubusercontent.com/astronexus/HYG-Database/c7f7f883fe678cc7680169a50ccd7dcc49b060ce/hyg/CURRENT/hygdata_v41.csv

The derived numerical subset and generated `StarCatalogData.kt` retain **CC BY-SA 4.0**. Changes: select 25 HIP identifiers, discard unrelated columns from runtime data, convert RA hours to degrees. All original columns for selected rows are retained in the test reference CSV. This data license is distinct from application source licensing. Credit and license are available offline in About.

`python3 scripts/generate-star-catalog.py` reproduces runtime numbers from the pinned subset without downloading data. The CSV extraction has been reserialized and is not claimed to be byte-identical upstream lines or a complete upstream download.

## Coordinate contract

ICRS/J2000 positions; `pmra` is tangent-plane eastward μRA*cos(dec), `pmdec` northward, both mas/year. Their convention was independently checked against the same catalog Cartesian velocity fields. Propagate tangent vector from J2000 TT, normalize, then use pinned Astronomy Engine `rotationEqjHor` for precession, nutation and Earth rotation. No finite-distance parallax, radial velocity, annual aberration, deflection or atmospheric refraction is applied. The intended angular tolerance is 0.05°, not astrometric precision. Independent Astropy apparent-airless fixtures include 2000, 2026, 2050 and both hemispheres; future Earth orientation/leap seconds remain frozen estimates.

Computed data is cached in a sky calculation at its explicit time/site; phone motion only projects it with the existing true-north pose. Canvas AR and GL virtual sky use the same pure projected geometry. Only visible object labels are emitted and short cached label textures are bounded; no full-screen CPU raster is generated for each pose.

## Verified numerical results for this preview

- 150 independent Astropy airless horizontal directions (25 stars, 2 hemispheres, 3 years): maximum angular error **0.005945859°**, below the 0.05° model bound.
- The same 150 independently propagated ICRS directions are checked within **0.000001°**, so omitting proper motion or its cos(dec) convention is detectable separately from the larger horizon-model allowance.
- These are calculation checks, not a claim about phone compass or camera field accuracy.
- Runtime numeric Kotlin subset is 1,784 source bytes; the final APK delta is measured separately. Reference fixtures and generation environment are test/source material, not installed assets.


## v11 screen-resolution background field (2026-10-26)

The same pinned HYG v4.1 source additionally provides 99,049 stars at magnitude <=9.5. The Sun is excluded. STF1 contains a little-endian count and 28-byte records: HIP int32, RA degrees/Dec degrees/pmRA*cosDec/pmDec/magnitude/B-V float32. Missing HIP is zero, not a shared identity; 259 missing B-V values use neutral0.65. This is a rendering subset, not additional named constellation figures or a replacement for the precise25-star calculations. The data remains CC BY-SA4.0; attribution and full legalcode are offline.

`python3 scripts/generate-sky-detail.py catalog` reproduces2,773,380 bytes from the SHA-pinned original CSV. See `sky-detail/sources.json` and `catalog-report.json`. CSV/EXR originals live only under local artifacts. Proper motion convention remains J2000 tangent east/north. Maximum decoded float32 RA/Dec rounding against the source is <0.000016degrees; this is storage precision, not field AR accuracy.
