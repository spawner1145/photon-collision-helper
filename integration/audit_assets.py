import hashlib
import json
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parents[1]
FORMAL = ROOT.parent / 'output/须佐能乎_环斩冲击'
BACKUP = ROOT.parent / 'output/backups/须佐环斩_算法性能优化前_20261015_141539/须佐能乎_环斩冲击'

comparisons = []
for source in sorted((BACKUP / 'assets').rglob('*')):
    if not source.is_file() or source.suffix == '.fx':
        continue
    relative = source.relative_to(BACKUP)
    assert source.read_bytes() == (FORMAL / relative).read_bytes(), relative
    comparisons.append(str(relative))

for name in ['cut_impact.fxproj', 'cut_mark.fxproj']:
    assert nbtlib.load(BACKUP / name) == nbtlib.load(FORMAL / name), name

original = nbtlib.load(BACKUP / 'susanoo_ring.fxproj')
current = nbtlib.load(FORMAL / 'susanoo_ring.fxproj')
probes = 0
for before, after in zip(original['data']['fx']['fxData']['fxObjects'], current['data']['fx']['fxData']['fxObjects']):
    if str(before['data']['name']).startswith('11 '):
        assert str(after['type']) == 'collision_emitter'
        assert before['data']['config'] == after['data']['config']
        assert before['data']['transform'] == after['data']['transform']
        after['type'] = before['type']
        after['data']['version'] = before['data']['version']
        del after['data']['collision']
        probes += 1
assert probes == 3
assert original == current, 'Unexpected non-collision project changes'

baseline = json.loads((FORMAL / 'editor/performance-cpu-diagnosis.json').read_text())
optimized = json.loads((FORMAL / 'editor/performance-helper-final.json').read_text())
native = [sample['medianMs'] for sample in baseline['cpu'] if sample['probes'] is True]
helper = [sample['medianMs'] for sample in optimized['cpu'] if sample['probes'] is True]
record = {
    'passed': True,
    'unchangedAssetFiles': comparisons,
    'unchangedProbeConfigAndTransforms': probes,
    'unchangedMainTimelineAndVisuals': True,
    'unchangedContactProjects': True,
    'nativeEmptyEditorMedianMilliseconds': native,
    'helperEmptyEditorMedianMilliseconds': helper,
    'meanMedianSpeedup': (sum(native) / len(native)) / (sum(helper) / len(helper)),
    'measurementScope': 'CPU simulation ticks in empty editor scene, not total FPS or dense-terrain GPU cost',
    'helperJarSha256': hashlib.sha256((ROOT / 'build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar').read_bytes()).hexdigest(),
}
(ROOT / 'integration/asset-performance-verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(record, ensure_ascii=False, indent=2))
