#!/usr/bin/env python3
"""Reproduce the offline sky field and tiled SVS background from pinned originals.

Technical data conversion only: preserves astronomical coordinates and image pixels.
Original CSV/EXR and intermediate RGB cache stay in artifacts, outside the APK/Git.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'artifacts/v11-deep-sky/sources'
ASSETS = ROOT / 'android/app/src/main/assets/sky'
PINNED = {
    'hygdata_v41.csv': 'd9f69fd86bbf90a4e4d52b4c5c53eacfa6dfc0bfdef85bfd94f095e0bebe4ebd',
    'milkyway_2020_16k_gal.exr': 'ee023f03e99466b3829117c15a001269ba32a3fecb6012d0b2d41f201c955351',
}

def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def checked(name):
    path = SOURCES / name
    assert digest(path) == PINNED[name], f'Unverified original: {name}'
    return path

def catalog():
    stars = []
    seen_hip = set()
    missing_color = 0
    for row in csv.DictReader(checked('hygdata_v41.csv').open()):
        if int(row['id']) == 0 or float(row['mag']) > 9.5:
            continue
        hip = int(row['hip']) if row['hip'] else 0
        assert not hip or hip not in seen_hip, f'Duplicate HIP {hip}'
        if hip:
            seen_hip.add(hip)
        values = [float(row['ra']) * 15, float(row['dec']), float(row['pmra']),
                  float(row['pmdec']), float(row['mag']), float(row['ci'] or '.65')]
        assert all(math.isfinite(x) for x in values)
        assert 0 <= values[0] < 360 and -90 <= values[1] <= 90
        missing_color += not bool(row['ci'])
        stars.append((int(row['id']), hip, values))
    stars.sort(key=lambda row: row[0])
    ASSETS.mkdir(parents=True, exist_ok=True)
    target = ASSETS / 'field-stars.bin'
    with target.open('wb') as f:
        f.write(b'STF1' + struct.pack('<I', len(stars)))
        for _, hip, values in stars:
            f.write(struct.pack('<i6f', hip, *values))
    # Preserve all existing precise constellation members in the background data.
    ref = ROOT / 'android/app/src/test/resources/reference/stars/hyg-v41-25stars-raw.csv'
    assert {int(r['hip']) for r in csv.DictReader(ref.open())} <= seen_hip
    assert target.stat().st_size == 8 + 28 * len(stars)
    report = {'format': 'STF1, LE count, LE (int32 HIP, 6 float32 RAdeg/Decdeg/pmRAcosDec/pmDec/mag/B-V)',
              'epoch': 'ICRS J2000; proper motion mas/year', 'count': len(stars),
              'magnitudeLimit': 9.5, 'missingColorNeutralBV065': missing_color,
              'sourceSha256': PINNED['hygdata_v41.csv'],
              'bytes': target.stat().st_size, 'sha256': digest(target)}
    (SOURCES.parent / 'catalog-report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report), flush=True)

def tiles():
    os.environ['OPENCV_IO_ENABLE_OPENEXR'] = '1'
    import cv2
    import numpy as np
    from PIL import Image
    cv2.setNumThreads(2)
    source = checked('milkyway_2020_16k_gal.exr')
    cache = SOURCES / 'milkyway-srgb-16384.rgb'
    shape = (8192, 16384, 3)
    # A disk-backed working image keeps conversion buffers bounded on the build Mac.
    if not cache.exists():
        linear = cv2.imread(str(source), cv2.IMREAD_UNCHANGED)
        assert linear is not None and linear.shape == shape and linear.dtype == np.float32
        rgb = np.memmap(cache.with_suffix('.part'), mode='w+', shape=shape, dtype=np.uint8)
        for y in range(0, shape[0], 128):
            v = np.clip(np.nan_to_num(linear[y:y+128], nan=0, posinf=1, neginf=0), 0, 1)
            srgb = np.where(v <= .0031308, v * 12.92, 1.055 * np.power(v, 1/2.4) - .055)
            rgb[y:y+128] = np.rint(np.clip(srgb, 0, 1) * 255).astype(np.uint8)[:, :, ::-1]
        rgb.flush()
        del rgb, linear
        cache.with_suffix('.part').rename(cache)
    assert cache.stat().st_size == math.prod(shape)
    full = np.memmap(cache, mode='r', shape=shape, dtype=np.uint8)
    records = []
    ASSETS.mkdir(parents=True, exist_ok=True)
    fallback = ASSETS / 'galaxy-fallback.jpg'
    Image.fromarray(cv2.resize(full, (2048, 1024), interpolation=cv2.INTER_AREA)).save(fallback, quality=92, subsampling=0, optimize=True)
    for width in (4096, 8192, 16384):
        level = full if width == 16384 else cv2.resize(full, (width, width//2), interpolation=cv2.INTER_AREA)
        target = ASSETS / 'galaxy-tiles' / str(width)
        target.mkdir(parents=True, exist_ok=True)
        for ty in range(width//1024):
            yy = np.clip(np.arange(ty*512-1, ty*512+513), 0, width//2-1)
            for tx in range(width//512):
                xx = np.arange(tx*512-1, tx*512+513) % width
                tile = np.ascontiguousarray(level[yy[:,None], xx[None,:]])
                file = target / f'{tx}_{ty}.jpg'
                Image.fromarray(tile).save(file, quality=90, subsampling=0, optimize=True)
                records.append({'path': str(file.relative_to(ASSETS)), 'bytes': file.stat().st_size, 'sha256': digest(file)})
        print(f'level {width}: {sum(r["bytes"] for r in records if r["path"].startswith("galaxy-tiles/"+str(width)+"/"))} bytes', flush=True)
    report = {'sourceSha256': PINNED[source.name], 'tileSize': 512, 'gutter': 1, 'jpegSize': 514,
              'levels': [4096,8192,16384], 'fallbackSize': [2048,1024], 'fallbackBytes': fallback.stat().st_size,
              'fallbackSha256': digest(fallback), 'tileCount':len(records),
              'totalBytes': sum(r['bytes'] for r in records)+fallback.stat().st_size, 'tiles': records}
    (SOURCES.parent / 'tiles-report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='tiles'}), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('part', choices=['catalog','tiles','all'])
    mode = parser.parse_args().part
    if mode in ('catalog','all'):
        catalog()
    if mode in ('tiles','all'):
        tiles()
