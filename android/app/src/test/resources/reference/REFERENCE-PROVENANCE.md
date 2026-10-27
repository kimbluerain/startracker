# Independent Sun/Moon reference data

Retrieved 2026-10-23 from official APIs. Runtime calculations do not use these test files.

- Original 72 direction fixtures and raw responses: JPL Horizons DE441, targets 10/301,
  `CENTER=coord@399`, geodetic east longitude/latitude/km height, UT (post-1962 UTC),
  apparent target center, AIRLESS, north-through-east azimuth. Exact requests are in
  `horizons-requests.json`; definitions: https://ssd.jpl.nasa.gov/horizons/manual.html#observer-table
  API: https://ssd-api.jpl.nasa.gov/doc/horizons.html
- 36 event time fixtures: USNO API, upper limb, sea-level unobstructed horizon,
  standard 34 arcminute refraction. Exact requests and raw responses are preserved.
  Sun center zenith distance 90.8333 degrees; Moon uses parallax and angular radius.
  Times are minute-rounded UT1 plus explicit offset, interpreted as nominal UTC only
  for the 120-second tolerance. Do not infer second-level truth. Definition source
  https://aa.usno.navy.mil/faq/RST_defs is retained as usno-definitions-raw.html.
- Supplement: 48 JPL vectors for equator (near zenith 89.883 degrees), elevated Yiwu,
  date line and Tromso, four seasons plus January lunar dates. Ground elevation 500m
  plus eye height 2m queries JPL observer elevation 502m. Another 36 JPL vectors use
  the exact event instants computed by the adapter. Their *angles* are independently
  queried, never copied from Astronomy Engine; event times are validated separately
  against USNO. These 36 vectors certify the 0.1-degree same-time azimuth criterion,
  not event-time accuracy. Reproduce via `fetch-supplement.py`; original computed
  instants are retained in `computed-event-query-times.json`. Request dates/times,
  parameters and raw responses are committed. Do not silently refresh expected values.
- 2050 samples are frozen-model regressions. Horizons freezes the last known leap
  second and final predicted EOP (`eop.261022.p270118`); these are not measurements or
  guaranteed future civil-time truth. Astronomy Engine uses its pinned Delta-T model.
  Future timezone law, Earth rotation, actual atmosphere and terrain can differ.

Directions use an independent ENU vector/cross-product angle, so azimuth wrap and
near-zenith singularity cannot inflate the comparison. Topocentric Moon samples
near the horizon detect omission of lunar parallax. Thresholds remain <=0.05 degrees
for direction, <=120 seconds for event time, and <=0.1 degrees for event azimuth.

City data is independent of these astronomy fixtures. `build-city-index.py` selects
only needed fields from the official cached zip, verifies its SHA256, and produces
runtime gzip plus provenance. Runtime attribution is assets/GEONAMES-LICENSE.txt.
