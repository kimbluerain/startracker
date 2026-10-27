#!/usr/bin/env python3
"""Materialize offline license evidence from Gradle's actual shipping resolution.

Run :app:exportPrivatePreviewDependencies first. No network is used here. --check
verifies generated outputs without writing; unknown/missing evidence is an error.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'android/app/src/main/assets'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
NS = {'p': 'http://maven.apache.org/POM/4.0.0'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    generated = {}

    def emit(path, data):
        path = ROOT / path
        generated[path] = data
        return sha(data)

    def pom_license(coordinate, seen=()):
        if coordinate in seen:
            raise ValueError(f'POM parent cycle: {coordinate}')
        group, artifact, version = coordinate.split(':')
        poms = sorted((CACHE / group / artifact / version).glob('*/*.pom'))
        if not poms or len({sha(p.read_bytes()) for p in poms}) != 1:
            raise ValueError(f'Missing or ambiguous POM: {coordinate}')
        data = poms[0].read_bytes()
        relative = f'docs/dependencies/poms/{group}.{artifact}-{version}.pom'
        emit(relative, data)
        repository = 'https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
        evidence = {'coordinate': coordinate, 'path': relative, 'sha256': sha(data),
                    'source': repository + f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.pom'}
        tree = ET.fromstring(data)
        licenses = [{'name': x.findtext('p:name', '', NS), 'url': x.findtext('p:url', '', NS)}
                    for x in tree.findall('p:licenses/p:license', NS)]
        if licenses:
            return licenses, [evidence]
        parent = tree.find('p:parent', NS)
        if parent is None:
            raise ValueError(f'No license or parent: {coordinate}')
        parent_id = ':'.join(parent.findtext('p:' + name, '', NS) for name in ('groupId', 'artifactId', 'version'))
        licenses, ancestors = pom_license(parent_id, (*seen, coordinate))
        return licenses, [evidence, *ancestors]

    documents = []

    def document(id_, title, filename, data):
        path = 'licenses/' + filename
        digest = emit('android/app/src/main/assets/' + path, data)
        documents.append({'id': id_, 'title': title, 'path': path, 'sha256': digest})
        return path

    emit('LICENSE', (ROOT / 'android/LICENSE').read_bytes())
    document('notice', '来源、修改与署名', 'NOTICE.txt', (ROOT / 'NOTICE').read_bytes())
    document('gpl', 'GPL v3 · startracker 与 Heliodos', 'GPL-3.0.txt', (ROOT / 'android/LICENSE').read_bytes())
    document('mit', 'MIT · Astronomy Engine', 'Astronomy-MIT.txt', (ROOT / 'android/astronomy/LICENSE').read_bytes())
    apache = document('apache', 'Apache 2.0 · AndroidX 等运行库', 'Apache-2.0.txt', (ROOT / 'android/colorpicker/LICENSE').read_bytes())
    document('geonames', 'GeoNames 数据来源与修改', 'GeoNames.txt', (ASSETS / 'GEONAMES-LICENSE.txt').read_bytes())
    cc = (ASSETS / 'licenses/CC-BY-4.0.txt').read_bytes()
    cc_source = json.loads((ASSETS / 'licenses/cc-by-source.json').read_text())
    if sha(cc) != cc_source['sha256']:
        raise ValueError('CC BY legalcode provenance checksum mismatch')
    document('ccby', 'CC BY 4.0 · 完整条款', 'CC-BY-4.0.txt', cc)
    document('erfa', 'ERFA · 条款与 SOFA 来源说明', 'ERFA.txt', (ASSETS / 'ERFA-LICENSE.txt').read_bytes())

    resolved = json.loads((ROOT / 'android/app/build/reports/dependencies/private-preview-resolved.json').read_text())
    assert resolved['configuration'] == 'privatePreviewRuntimeClasspath'
    artifacts = []
    extras = {}
    known_texts = {entry['sha256']: entry['path'] for entry in documents}
    for item in resolved['artifacts']:
        file = Path(item['file'])
        data = file.read_bytes()
        if sha(data) != item['sha256']:
            raise ValueError(f'Artifact changed after Gradle export: {item["coordinate"]}')
        licenses, poms = pom_license(item['coordinate'])
        if not all('apache' in license_['name'].lower() and '2.0' in license_['name'] for license_ in licenses):
            raise ValueError(f'Review new runtime license before exporting: {item["coordinate"]}: {licenses}')
        entry = {k: item[k] for k in ('coordinate', 'filename', 'sha256')}
        entry.update(licenses=licenses, licenseText=apache, pomEvidence=poms, embeddedTexts=[], nativeLibraries=[])

        def inspect_zip(payload, prefix=''):
            with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                for name in sorted(archive.namelist()):
                    if name.endswith('/'):
                        continue
                    if name.endswith('.jar'):
                        inspect_zip(archive.read(name), prefix + name + '!/')
                    elif any(token in Path(name).name.lower() for token in ('license', 'notice', 'copyright')):
                        text = archive.read(name)
                        text.decode('utf-8')  # Never silently discard an unreadable notice.
                        digest = sha(text)
                        path = known_texts.get(digest)
                        if path is None:
                            path = f'licenses/embedded/{digest}.txt'
                            known_texts[digest] = path
                            extras[path] = text
                        entry['embeddedTexts'].append({'archivePath': prefix + name, 'path': path, 'sha256': digest})
                    elif name.endswith('.so'):
                        entry['nativeLibraries'].append({'archivePath': prefix + name, 'sha256': sha(archive.read(name))})
        inspect_zip(data)
        artifacts.append(entry)
    for number, (path, data) in enumerate(sorted(extras.items()), 1):
        document(f'embedded_{number}', f'随运行库附带的许可原文 {number}', path.removeprefix('licenses/'), data)

    inventory = {'configuration': resolved['configuration'], 'resolvedComponents': resolved['components'],
                 'artifacts': artifacts, 'localSource': {'module': ':astronomy-jvm', 'upstreamCommit': 'ac3c84ca34696bcc299704d2fe2d6084ec2728cc', 'license': 'MIT'},
                 'scope': 'External shipping-runtime artifacts, not build/test dependencies. Platform constraints are listed as resolved components; they have no binary payload.'}
    emit('android/app/src/main/assets/licenses/runtime-dependencies.json', json_bytes(inventory))
    lines = ['私用 APK 实际运行依赖', '配置：privatePreviewRuntimeClasspath',
             '原始 AAR/JAR SHA256；完整 POM 证据在本地源码 docs/dependencies/poms。',
             '所有以下外部制品的 POM/父 POM 声明 Apache 2.0；随包原文与完整主条款均可离线阅读。', '']
    for entry in artifacts:
        lines += [entry['coordinate'], entry['filename'], 'SHA256 ' + entry['sha256'], '']
    document('dependencies', f'运行依赖与校验和（{len(artifacts)} 个制品）', 'DEPENDENCIES.txt', '\n'.join(lines).encode())
    document('nasa_star_maps', 'NASA / ESA · 离线星图与背景来源', 'NASA-Star-Maps.txt', (ASSETS / 'sky/ATTRIBUTION.txt').read_bytes())
    document('hyg', 'HYG · 亮星数据来源与修改', 'HYG-Star-Catalog.txt', (ASSETS / 'HYG-ATTRIBUTION.txt').read_bytes())
    document('ccbysa', 'CC BY-SA 4.0 · 星表完整条款', 'CC-BY-SA-4.0.txt', (ASSETS / 'licenses/CC-BY-SA-4.0.txt').read_bytes())
    emit('android/app/src/main/assets/licenses/index.json', json_bytes({'documents': documents}))
    for path, data in generated.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise ValueError(f'Generated evidence differs: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print(f'{"Checked" if args.check else "Wrote"} {len(artifacts)} runtime artifacts, {len(documents)} offline documents, {len(generated)} evidence files')


if __name__ == '__main__':
    main()
