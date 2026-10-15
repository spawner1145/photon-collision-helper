import argparse
import copy
import gzip
import hashlib
import io
import json
import shutil
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

import nbtlib


WORKSPACE = Path(__file__).resolve().parents[2]
OUTPUT = WORKSPACE / 'output'
GAME = Path('D:/game/.minecraft/versions/1.21.1-NeoForge_21.1.252')
ENTITY_EFFECTS = {'豪火灭却', '豪火球之术', '豪龙火', '须佐能乎_勾玉'}
EXCLUDED_PARTS = {'editor', 'preview', 'backups', 'session-backups', '__pycache__'}


def decode(payload):
    compressed = payload.startswith(b'\x1f\x8b')
    stream = io.BytesIO(gzip.decompress(payload) if compressed else payload)
    return nbtlib.File.parse(stream), compressed


def objects_in(value, path=()):
    if isinstance(value, dict):
        if str(value.get('type', '')) in {'particle_emitter', 'collision_emitter'}:
            yield path, value
        for key, child in value.items():
            yield from objects_in(child, path + (str(key),))
    elif isinstance(value, (list, nbtlib.List)):
        for index, child in enumerate(value):
            yield from objects_in(child, path + (str(index),))


def collision_rows(document):
    rows = []
    for path, item in objects_in(document):
        data = item.get('data', {})
        config = data.get('config', {})
        physics = config.get('physics', {})
        if not (bool(physics.get('_enable', False)) and bool(physics.get('hasCollision', True))):
            continue
        sub = config.get('subEmitters', {})
        events = []
        if sub.get('_enable'):
            events = [dict(event=str(event.get('event')), fx=str(event.get('fxLocation', '')))
                      for event in sub.get('emitters', {}).get('payload', [])]
        rows.append({
            'path': '/'.join(path), 'id': str(data.get('transform', {}).get('id', '')),
            'name': str(data.get('name', '')), 'type': str(item['type']),
            'backend': str(data.get('collision', {}).get('backend', 'NATIVE')),
            'entities': bool(data.get('collision', {}).get('entities', False)),
            'physics': physics.unpack(), 'events': events,
        })
    return rows


def encode(document, compressed):
    stream = io.BytesIO()
    document.write(stream)
    payload = stream.getvalue()
    return gzip.compress(payload, mtime=0) if compressed else payload


def migrate_document(document, effect):
    before = copy.deepcopy(document)
    changes = []
    for path, item in objects_in(document):
        data = item.get('data', {})
        physics = data.get('config', {}).get('physics', {})
        if not (bool(physics.get('_enable', False)) and bool(physics.get('hasCollision', True))):
            continue
        original = copy.deepcopy(item)
        settings = data.setdefault('collision', nbtlib.Compound())
        settings['backend'] = nbtlib.String('CACHED_SWEEP')
        settings['blocks'] = nbtlib.Byte(1)
        settings['entities'] = nbtlib.Byte(effect in ENTITY_EFFECTS)
        settings['fluids'] = nbtlib.Byte(0)
        if effect in ENTITY_EFFECTS:
            settings['entityFilter'] = nbtlib.String('LIVING')
            settings['ignoreOwner'] = nbtlib.Byte(1)
            settings['sweepEntityMotion'] = nbtlib.Byte(1)
        if 'response' not in settings:
            settings['response'] = nbtlib.String('INHERIT')
        item['type'] = nbtlib.String('collision_emitter')
        data['version'] = nbtlib.Int(1)
        if item != original:
            changes.append({'path': list(path), 'id': str(data['transform']['id']),
                            'name': str(data['name']), 'entityCollision': bool(settings['entities'])})
    normalized = copy.deepcopy(document)
    originals = dict(objects_in(before))
    for path, item in objects_in(normalized):
        original = originals[path]
        item['type'] = original['type']
        if 'version' in original['data']:
            item['data']['version'] = original['data']['version']
        else:
            item['data'].pop('version', None)
        if 'collision' in original['data']:
            item['data']['collision'] = copy.deepcopy(original['data']['collision'])
        else:
            item['data'].pop('collision', None)
    assert normalized == before, 'Non-collision fields were modified'
    return changes


def migrate_payload(payload, effect):
    document, compressed = decode(payload)
    changes = migrate_document(document, effect)
    if not changes:
        return payload, changes
    result = encode(document, compressed)
    restored, unused = decode(result)
    assert restored == document
    return result, changes


def sha256(payload):
    return hashlib.sha256(payload).hexdigest()


def apply_migration(report):
    stamp = datetime.now(timezone(timedelta(hours=8))).strftime('%Y%m%d_%H%M%S')
    backup = OUTPUT / 'backups' / ('全部碰撞_缓存算法与四术实体前_' + stamp)
    planned = {}
    changes_by_effect = {}
    unchanged_non_fx_entries = 0
    for root in effect_roots():
        for path in effect_files(root):
            original = path.read_bytes()
            changes = []
            if path.suffix == '.fxpack':
                stream = io.BytesIO()
                with zipfile.ZipFile(io.BytesIO(original)) as archive:
                    replacements = {}
                    for info in archive.infolist():
                        if not info.filename.endswith('.fx'):
                            unchanged_non_fx_entries += 1
                            continue
                        payload, modified = migrate_payload(archive.read(info.filename), root.name)
                        if modified:
                            replacements[info.filename] = payload
                            changes.extend(dict(change, entry=info.filename) for change in modified)
                    if changes:
                        with zipfile.ZipFile(stream, 'w') as destination:
                            destination.comment = archive.comment
                            for info in archive.infolist():
                                destination.writestr(info, replacements.get(info.filename, archive.read(info.filename)))
                updated = stream.getvalue() if changes else original
                with zipfile.ZipFile(io.BytesIO(updated)) as archive:
                    assert archive.testzip() is None
            else:
                updated, changes = migrate_payload(original, root.name)
            if updated != original:
                planned[path] = updated
                changes_by_effect.setdefault(root.name, []).extend(changes)
    if not planned:
        print(json.dumps({'completed': True, 'changedFiles': 0, 'alreadyMigrated': True}))
        return
    backup.mkdir(parents=True, exist_ok=False)
    formal_paths = list(planned)
    for path in formal_paths:
        relative = path.relative_to(OUTPUT)
        if 'assets' in relative.parts:
            asset_relative = relative.parts[relative.parts.index('assets') + 1:]
            installed = GAME / 'ldlib2/assets' / Path(*asset_relative)
        elif path.suffix == '.fxpack':
            installed = GAME / 'photon/fxpacks' / path.name
        else:
            continue
        planned[installed] = planned[path]
    metadata = {}
    for effect in changes_by_effect:
        root = OUTPUT / effect
        manifest_path = root / 'manifest.json'
        if manifest_path.exists():
            manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
            if isinstance(manifest, list):
                for entry in manifest:
                    target = root / entry['path']
                    if target in planned:
                        entry['sha256'] = sha256(planned[target])
                        entry['bytes'] = len(planned[target])
                planned[manifest_path] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
                manifest = None
        else:
            manifest = None
        if manifest is not None:
            manifest.setdefault('requires', {})['photon_collision_helper'] = '0.1.4'
            manifest['collision_optimization'] = {
                'backend': 'CACHED_SWEEP', 'response': 'INHERIT (existing explicit responses preserved)',
                'entity_collision': effect in ENTITY_EFFECTS,
                'entity_filter': 'LIVING (players included)' if effect in ENTITY_EFFECTS else 'disabled',
                'ignore_owner': True, 'fluids': False,
                'visuals_timeline_gravity_and_bounce_preserved': True,
                'report': 'editor/collision-optimization.json',
            }
            if effect == '须佐能乎_勾玉':
                manifest['collision'] = 'Cached swept blocks and living entities (players included) / FirstCollision; automatic executor owner exclusion'
            if effect == '天碍震星':
                manifest['collision_mode'] = 'Timeline meteor landing/contact unchanged; cached swept block physics for fragments; no terrain damage'
            for filename in manifest.get('sha256', {}):
                target = root / filename
                if target in planned:
                    manifest['sha256'][filename] = sha256(planned[target])
            planned[manifest_path] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        unique = {change['id']: change for change in changes_by_effect[effect]}
        metadata[effect] = {'backend': 'CACHED_SWEEP', 'entityCollision': effect in ENTITY_EFFECTS,
                            'convertedEmitterCount': len(unique), 'emitters': list(unique.values()),
                            'preservedVisualsAndNativePhysics': True, 'backup': str(backup),
                            'requires': {'photon_collision_helper': '0.1.4'}}
        planned[root / 'editor/collision-optimization.json'] = (
            json.dumps(metadata[effect], ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    records = []
    for path, payload in planned.items():
        base = WORKSPACE if path.is_relative_to(WORKSPACE) else GAME
        category = 'workspace' if base == WORKSPACE else 'game'
        saved = backup / category / path.relative_to(base)
        existed = path.exists()
        if existed:
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, saved)
        records.append({'path': str(path), 'backup': str(saved) if existed else None,
                        'beforeSha256': sha256(path.read_bytes()) if existed else None,
                        'afterSha256': sha256(payload)})
    result = {'completed': False, 'backup': str(backup), 'files': records, 'effects': metadata,
              'nonEffectPackEntriesPreserved': unchanged_non_fx_entries,
              'beforeInventory': report}
    report_path = backup / 'migration.json'
    report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    written = []
    try:
        for record, (path, payload) in zip(records, planned.items()):
            path.parent.mkdir(parents=True, exist_ok=True)
            written.append((path, record))
            path.write_bytes(payload)
            assert sha256(path.read_bytes()) == record['afterSha256']
        for entry in inventory():
            assert all(row['type'] == 'collision_emitter' and row['backend'] == 'CACHED_SWEEP'
                       and row['entities'] == (entry['effect'] in ENTITY_EFFECTS) for row in entry['rows'])
        result['completed'] = True
        report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    except Exception:
        for path, record in reversed(written):
            if record['backup']:
                shutil.copy2(record['backup'], path)
            else:
                path.unlink(missing_ok=True)
        raise
    (Path(__file__).parent / 'output-collision-migration.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'completed': True, 'backup': str(backup), 'files': len(records),
                      'effects': {effect: {'emitters': value['convertedEmitterCount'],
                                           'entities': value['entityCollision']} for effect, value in metadata.items()}},
                     ensure_ascii=False, indent=2))


def effect_roots():
    return sorted(directory for directory in OUTPUT.iterdir()
                  if directory.is_dir() and directory.name not in EXCLUDED_PARTS
                  and any(directory.glob('*.fxpack')))


def effect_files(root):
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if set(relative.parts) & EXCLUDED_PARTS:
            continue
        if any(token in path.stem.lower() for token in ['backup', 'before-', 'previous-', '副本', '备份']):
            continue
        if path.suffix in {'.fx', '.fxproj', '.fxpack'}:
            yield path


def inventory():
    report = []
    for root in effect_roots():
        for path in effect_files(root):
            if path.suffix == '.fxpack':
                with zipfile.ZipFile(path) as archive:
                    for name in archive.namelist():
                        if name.endswith('.fx'):
                            document, compressed = decode(archive.read(name))
                            rows = collision_rows(document)
                            if rows:
                                report.append({'effect': root.name, 'file': str(path.relative_to(OUTPUT)),
                                               'entry': name, 'rows': rows})
            else:
                document, compressed = decode(path.read_bytes())
                rows = collision_rows(document)
                if rows:
                    report.append({'effect': root.name, 'file': str(path.relative_to(OUTPUT)), 'rows': rows})
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    arguments = parser.parse_args()
    report = inventory()
    (Path(__file__).parent / 'output-collision-inventory.json').write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    summary = {}
    for entry in report:
        current = summary.setdefault(entry['effect'], {'files': 0, 'objects': {}})
        current['files'] += 1
        for row in entry['rows']:
            current['objects'][row['id']] = {'name': row['name'], 'backend': row['backend'], 'events': row['events']}
    print(json.dumps({name: {'files': value['files'], 'emitters': len(value['objects'])}
                      for name, value in summary.items()}, ensure_ascii=False, indent=2))
    if arguments.apply:
        apply_migration(report)


if __name__ == '__main__':
    main()
