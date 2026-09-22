"""Bundle the current capture comparison as one local HTML; never reuse an old prototype."""
from pathlib import Path
import argparse
import base64
import hashlib
import json

here = Path(__file__).resolve().parent
root = here.parents[1]
baseline = root / 'artifacts/ui-current/baseline'
shots = sorted(baseline.glob('*.png'))
assert len(shots) == 18, 'Capture all current Compose screens before generating the review'

def png(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode('ascii')

images = {path.stem: png(path) for path in shots}
pages = [png(root / f'app/src/main/res/drawable-nodpi/page_{i}.png') for i in range(1, 7)]
data = ('const BASELINES=' + json.dumps(images) + ';\nconst READER_PAGES=' + json.dumps(pages)
        + ';\nconst ICON_LIBRARY=' + (here / 'icons.json').read_text(encoding='utf-8') + ';')
html = (here / 'index.html').read_text(encoding='utf-8')
html = html.replace('/* CSS */', (here / 'styles.css').read_text(encoding='utf-8'))
html = html.replace('/* DATA */', data).replace('/* APP */', (here / 'app.js').read_text(encoding='utf-8'))
html = html.replace('</head>', '<!-- Phosphor Icons 2.1.1\n' + (root / 'third_party/phosphor-icons/LICENSE').read_text(encoding='utf-8') + '\n-->\n</head>')
out = root / 'artifacts/picomic-ui-current.html'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--capture-apk', type=Path, help='Only when recording a fresh baseline: APK used to capture it')
args = parser.parse_args()
manifest_path = root / 'artifacts/ui-current/capture-manifest.json'
hashes = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in shots}
if args.capture_apk:
    from datetime import datetime
    manifest = {'capture_date': datetime.now().isoformat(timespec='seconds'),
                'source': 'Production PiComicApp Compose tree; disposable headless Android emulator; synthetic safe metadata',
                'apk_sha256': hashlib.sha256(args.capture_apk.read_bytes()).hexdigest(), 'screenshots': hashes}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
else:
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    assert manifest['screenshots'] == hashes, 'Baseline changed: capture and explicitly record the matching APK first'
# Rebuilding the HTML must never relabel old captures with a subsequently built APK hash.
out.write_text(html, encoding='utf-8')
print(f'Built {out} ({out.stat().st_size:,} bytes), {len(images)} current native screenshots')
