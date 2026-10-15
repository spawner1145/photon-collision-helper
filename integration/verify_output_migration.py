import argparse
import hashlib
import io
import json
import zipfile
from pathlib import Path

from migrate_output import ENTITY_EFFECTS, GAME, OUTPUT, WORKSPACE, decode, inventory, migrate_document, objects_in


ROOT = Path(__file__).resolve().parent


def normalized_matches(before, after, effect):
    expected, unused = decode(before)
    actual, unused = decode(after)
    migrate_document(expected, effect)
    assert expected == actual, effect


def verify(repair_manifest=False, refresh_manifests=False):
    initial = json.loads((ROOT / 'output-collision-migration-initial.json').read_text(encoding='utf-8'))
    latest = json.loads((ROOT / 'output-collision-migration.json').read_text(encoding='utf-8'))
    assert initial['completed'] and latest['completed']
    if repair_manifest:
        for record in initial['files']:
            path = Path(record['path'])
            if path.name != 'manifest.json' or not record['backup']:
                continue
            before = json.loads(Path(record['backup']).read_text(encoding='utf-8'))
            if not isinstance(before, list):
                continue
            for entry in before:
                target = path.parent / entry['path']
                if target.exists():
                    entry['sha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
                    entry['bytes'] = target.stat().st_size
            path.write_text(json.dumps(before, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if refresh_manifests:
        for effect in initial['effects']:
            path = OUTPUT / effect / 'manifest.json'
            manifest = json.loads(path.read_text(encoding='utf-8'))
            if isinstance(manifest, list):
                for entry in manifest:
                    target = path.parent / entry['path']
                    if target.exists():
                        entry['sha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
                        entry['bytes'] = target.stat().st_size
            else:
                for filename in manifest.get('sha256', {}):
                    target = path.parent / filename
                    if target.exists():
                        manifest['sha256'][filename] = hashlib.sha256(target.read_bytes()).hexdigest()
            path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    reports = inventory()
    effects = {}
    for report in reports:
        rows = effects.setdefault(report['effect'], {})
        for row in report['rows']:
            assert row['type'] == 'collision_emitter' and row['backend'] == 'CACHED_SWEEP'
            assert row['entities'] == (report['effect'] in ENTITY_EFFECTS)
            rows[row['id']] = row
    compared_files = []
    archive_visuals = 0
    installed_matches = 0
    for record in initial['files']:
        path = Path(record['path'])
        if path.is_relative_to(GAME):
            if path.suffix == '.fxpack':
                sources = list(OUTPUT.glob('*/' + path.name))
            else:
                relative = path.relative_to(GAME / 'ldlib2')
                sources = list(OUTPUT.glob('*/' + relative.as_posix()))
            assert any(source.read_bytes() == path.read_bytes() for source in sources), path
            installed_matches += 1
            continue
        if path.suffix not in {'.fx', '.fxproj', '.fxpack'}:
            continue
        effect = path.relative_to(OUTPUT).parts[0]
        before = Path(record['backup']).read_bytes()
        after = path.read_bytes()
        if path.suffix == '.fxpack':
            with zipfile.ZipFile(io.BytesIO(before)) as original, zipfile.ZipFile(io.BytesIO(after)) as archive:
                assert original.namelist() == archive.namelist()
                assert archive.testzip() is None
                for name in original.namelist():
                    if name.endswith('.fx'):
                        normalized_matches(original.read(name), archive.read(name), effect)
                    else:
                        assert original.read(name) == archive.read(name), name
                        archive_visuals += 1
                    loose = path.parent / name
                    if name.endswith('.fx') and loose.exists():
                        left, unused = decode(archive.read(name))
                        right, unused = decode(loose.read_bytes())
                        assert left == right, str(loose)
        else:
            normalized_matches(before, after, effect)
        compared_files.append(str(path.relative_to(WORKSPACE)))
    for effect in ENTITY_EFFECTS:
        projects = []
        for project in (OUTPUT / effect).glob('*.fxproj'):
            document, unused = decode(project.read_bytes())
            for path, emitter in objects_in(document):
                settings = emitter['data'].get('collision', {})
                if settings.get('entities'):
                    assert str(settings['entityFilter']) == 'LIVING'
                    assert not settings['fluids'] and settings['ignoreOwner']
                    assert settings['sweepEntityMotion']
                    projects.append(str(emitter['data']['name']))
        assert projects, effect
    total = sum(len(rows) for rows in effects.values())
    assert total == 41 and len(effects) == 8
    summary = {
        'passed': True, 'effects': {name: {'emitters': len(rows), 'entities': name in ENTITY_EFFECTS}
                                  for name, rows in effects.items()},
        'totalCollisionEmitters': total, 'livingEntityEffects': sorted(ENTITY_EFFECTS),
        'entityFilter': 'LIVING (includes players, excludes items and vehicles)',
        'nonCollisionConfigTimelineAndVisualsUnchanged': True,
        'verifiedEffectFiles': compared_files, 'unchangedPackAssetEntries': archive_visuals,
        'installedCopiesMatch': installed_matches, 'backups': [initial['backup'], latest['backup']],
    }
    (ROOT / 'output-collision-static-verification.json').write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repair-manifest', action='store_true')
    parser.add_argument('--refresh-manifests', action='store_true')
    arguments = parser.parse_args()
    verify(arguments.repair_manifest, arguments.refresh_manifests)
