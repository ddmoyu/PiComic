"""Create a runtime inventory and collect notices from already resolved Gradle artifacts.
Run writeRuntimeInventory first. Missing POMs may be fetched from official Maven repositories.
"""
import csv
import hashlib
import io
import pathlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / '.gradle/caches/modules-2/files-2.1'
OUT = ROOT / 'app/src/main/assets/licenses'
OUT.mkdir(parents=True, exist_ok=True)
POMS = ROOT / 'artifacts/license-poms'
POMS.mkdir(parents=True, exist_ok=True)
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
rows = list(csv.DictReader((ROOT / 'app/build/reports/runtime-dependencies.tsv').open(encoding='utf-8'), delimiter='\t'))
rows = [r for r in rows if r['group'] != 'PiComic']
rows.append(dict(group='com.android.tools', module='desugar_jdk_libs_nio', version='2.1.5'))
notice_map = {}
lines = ['# Resolved runtime dependency notices', '', 'Generated from the releaseRuntimeClasspath and core library desugaring configuration. Includes platform/BOM metadata components.', '', '| Component | Declared license | Embedded notices |', '|---|---|---|']

def collect(archive, coordinate, nested=False):
    found = []
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            name = pathlib.PurePosixPath(item.filename).name.lower()
            if item.is_dir() or item.file_size > 1024 * 1024:
                continue
            if name.startswith(('license', 'notice', 'copying')):
                content = z.read(item)
                content.decode('utf-8')
                digest = hashlib.sha256(content).hexdigest()[:20]
                filename = f'embedded-{digest}.txt'
                (OUT / filename).write_bytes(content)
                notice_map.setdefault(filename, set()).add(coordinate + ' / ' + item.filename)
                found.append(filename)
            elif not nested and item.filename == 'classes.jar':
                found += collect(io.BytesIO(z.read(item)), coordinate, True)
    return sorted(set(found))

def declared(root, depth=0):
    if depth > 8:
        raise ValueError('POM parent chain too deep')
    values = [(e.findtext('m:name', namespaces=NS), e.findtext('m:url', namespaces=NS)) for e in root.findall('m:licenses/m:license', NS)]
    if values:
        return values
    parent = root.find('m:parent', NS)
    if parent is None:
        return []
    group = parent.findtext('m:groupId', namespaces=NS)
    module = parent.findtext('m:artifactId', namespaces=NS)
    version = parent.findtext('m:version', namespaces=NS)
    path = POMS / f'{group}-{module}-{version}.pom'
    if not path.exists():
        url = 'https://repo.maven.apache.org/maven2/' + group.replace('.', '/') + f'/{module}/{version}/{module}-{version}.pom'
        with urllib.request.urlopen(url, timeout=30) as response:
            value = response.read(1024 * 1024 + 1)
            if len(value) > 1024 * 1024:
                raise ValueError('POM too large')
            path.write_bytes(value)
    return declared(ET.fromstring(path.read_bytes()), depth + 1)

for row in rows:
    group, module, version = row['group'], row['module'], row['version']
    coordinate = f'{group}:{module}:{version}'
    directory = CACHE / group / module / version
    poms = list(directory.glob('*/*.pom'))
    if poms:
        pom = poms[0].read_bytes()
    else:
        destination = POMS / f'{group}-{module}-{version}.pom'
        if not destination.exists():
            base = 'https://dl.google.com/dl/android/maven2/' if group.startswith(('androidx.', 'com.android.')) else 'https://repo.maven.apache.org/maven2/'
            url = base + group.replace('.', '/') + f'/{module}/{version}/{module}-{version}.pom'
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read(1024 * 1024 + 1)
                if len(data) > 1024 * 1024:
                    raise ValueError('POM too large')
                destination.write_bytes(data)
        pom = destination.read_bytes()
    root = ET.fromstring(pom)
    licenses = declared(root)
    if not licenses:
        raise ValueError(f'No declared license: {coordinate}')
    labels = ', '.join(f'[{name}]({url})' for name, url in licenses)
    files = []
    for artifact in directory.glob('*/*'):
        if artifact.suffix in ('.jar', '.aar') and not artifact.name.endswith(('-sources.jar', '-javadoc.jar')):
            files += collect(artifact, coordinate)
    lines.append(f'| {coordinate} | {labels} | ' + ', '.join(f'[{f}]({f})' for f in sorted(set(files))) + ' |')
lines += ['', '## Embedded notice origins', '']
for filename, origins in sorted(notice_map.items()):
    lines += [f'- [{filename}]({filename}): ' + '; '.join(sorted(origins))]
(OUT / 'runtime-dependencies.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'{len(rows)} resolved components; {len(notice_map)} embedded notices; all POM licenses present.')
