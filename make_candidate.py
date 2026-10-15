import copy
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT.parent / 'output/须佐能乎_环斩冲击/susanoo_ring.fxproj'
DESTINATION = ROOT / 'integration/susanoo-ring-helper.fxproj'

project = nbtlib.load(SOURCE)
for item in project['data']['fx']['fxData']['fxObjects']:
    if not str(item['data']['name']).startswith('11 '):
        continue
    item['type'] = nbtlib.String('collision_emitter')
    item['data']['version'] = nbtlib.Int(1)
    item['data']['collision'] = nbtlib.Compound({
        'blocks': nbtlib.Byte(1),
        'backend': nbtlib.String('CACHED_SWEEP'),
        'entities': nbtlib.Byte(0),
        'fluids': nbtlib.Byte(0),
        'response': nbtlib.String('INHERIT'),
        'entityFilter': nbtlib.String('LIVING'),
        'deduplicateEmitterHits': nbtlib.Byte(1),
        'deduplicationGroup': nbtlib.String('susanoo_ring_blade'),
        'sourceEntityExclusionRadius': nbtlib.Double(0),
    })
DESTINATION.parent.mkdir(parents=True, exist_ok=True)
project.save(DESTINATION)
print(DESTINATION)
