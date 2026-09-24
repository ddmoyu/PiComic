"""Import an integrity-pinned official Phosphor subset and generate Android vectors.

Run once with --fetch to update the checked-in SVGs; normal regeneration is offline.
Geometry is copied verbatim, never redrawn. Update VERSION/INTEGRITY together.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import io
import json
import tarfile
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = '2.1.1'
INTEGRITY = 'v4ARvrip4qBCImOE5rmPUylOEK4iiED9ZyKjcvzuezqMaiRASCHKcRIuvvxL/twvLpkfnEODCOJp5dM4eZilxQ=='
SOURCE = ROOT / 'third_party/phosphor-icons'
ICONS = {
    'back': 'arrow-left', 'chevron': 'caret-right', 'search': 'magnifying-glass',
    'settings': 'gear', 'explore': 'compass', 'categories': 'squares-four',
    'book': 'book-open', 'heart': 'heart', 'download': 'download-simple',
    'user': 'user', 'filter': 'funnel-simple', 'moon': 'moon',
    'refresh': 'arrow-clockwise', 'folder': 'folder', 'wifi': 'wifi-high',
    'info': 'info', 'close': 'x', 'check': 'check', 'play': 'play',
    'pause': 'pause', 'menu': 'list', 'trash': 'trash', 'clock': 'clock', 'plus': 'plus',
    'heart_filled': 'heart-fill',
    'eye': 'eye', 'eye_slash': 'eye-slash',
}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--fetch', action='store_true', help='Download the pinned official package')
if parser.parse_args().fetch:
    url = f'https://registry.npmjs.org/@phosphor-icons/core/-/core-{VERSION}.tgz'
    payload = urllib.request.urlopen(url, timeout=60).read()
    assert base64.b64encode(hashlib.sha512(payload).digest()).decode() == INTEGRITY, 'Upstream integrity mismatch'
    SOURCE.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(payload), mode='r:gz') as archive:
        for name in ICONS.values():
            weight = 'fill' if name.endswith('-fill') else 'regular'
            file = SOURCE / weight / f'{name}.svg'
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(archive.extractfile(f'package/assets/{weight}/{name}.svg').read())
        (SOURCE / 'LICENSE').write_bytes(archive.extractfile('package/LICENSE').read())

vectors = ROOT / 'app/src/main/res/drawable'
vectors.mkdir(parents=True, exist_ok=True)
html_icons = {}
for key, name in ICONS.items():
    weight = 'fill' if name.endswith('-fill') else 'regular'
    source = SOURCE / weight / f'{name}.svg'
    svg = ET.fromstring(source.read_text(encoding='utf-8'))
    assert svg.attrib['viewBox'] == '0 0 256 256'
    assert set(svg.attrib) <= {'width', 'height', 'viewBox', 'fill'}
    paths = []
    html_paths = []
    for path in svg:
        assert path.tag.rsplit('}', 1)[-1] == 'path', f'Unsupported SVG element in {name}'
        assert set(path.attrib) <= {'d', 'fill-rule', 'clip-rule'}, f'Unsupported attributes in {name}'
        data = path.attrib['d']
        fill_type = 'evenOdd' if path.attrib.get('fill-rule') == 'evenodd' else 'nonZero'
        paths.append(f'    <path android:fillColor="#FF000000" android:fillType="{fill_type}" android:pathData="{data}" />')
        html_paths.append(f'<path d="{data}" fill-rule="{path.attrib.get("fill-rule", "nonzero")}"/>')
    mirror = ' android:autoMirrored="true"' if key in {'back', 'chevron'} else ''
    vector = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from @phosphor-icons/core {VERSION}, {weight}/{name}.svg. MIT; see assets/licenses/phosphor-icons.txt. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="256" android:viewportHeight="256"{mirror}>
{chr(10).join(paths)}
</vector>
'''
    (vectors / f'ic_phosphor_{key}.xml').write_text(vector, encoding='utf-8')
    html_icons[key] = ''.join(html_paths)

license_file = ROOT / 'app/src/main/assets/licenses/phosphor-icons.txt'
license_file.parent.mkdir(parents=True, exist_ok=True)
license_file.write_bytes((SOURCE / 'LICENSE').read_bytes())
(SOURCE / 'manifest.json').write_text(json.dumps({
    'package': '@phosphor-icons/core', 'version': VERSION,
    'source': 'https://github.com/phosphor-icons/core',
    'integrity': 'sha512-' + INTEGRITY, 'icons': ICONS,
}, indent=2) + '\n', encoding='utf-8')
(ROOT / 'design/current-ui/icons.json').write_text(json.dumps(html_icons) + '\n', encoding='utf-8')
print(f'Generated {len(ICONS)} official Phosphor vectors and HTML icons.')
