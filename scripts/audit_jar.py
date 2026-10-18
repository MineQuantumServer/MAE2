#!/usr/bin/env python3
"""Check distributable metadata, all source resources, IDs, recipes, mixins and Java level."""
import hashlib
import json
import re
from pathlib import Path
import sys
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
jar = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/libs/mae2-2.1.0+mc1.21.1.jar'
items = ['pattern_p2p_tunnel', 'eu_p2p_tunnel', 'faulty_card', 'cloud_chamber']
items += [f'{t}_multi_p2p_tunnel' for t in ['item', 'fluid', 'fe', 'eu', 'redstone', 'pattern']]
items += [f'{n}x_crafting_accelerator' for n in [4, 16, 64, 256]]
with zipfile.ZipFile(jar) as z:
    names = set(z.namelist())
    assert len(names) == len(z.namelist()), 'Duplicate archive entries'
    meta = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
    assert meta['mods'][0]['modId'] == 'mae2'
    deps = {d['modId']: d['versionRange'] for d in meta['dependencies']['mae2']}
    assert deps['minecraft'] == '[1.21.1]' and deps['ae2'] == '[19.2.18]' and deps['gtceu'] == '[8.0.0]'
    assert json.loads(z.read('META-INF/mae2-dependencies.json')) == json.loads((root / 'dependencies.lock.json').read_text())
    mixins = json.loads(z.read('mixins.mae2.json'))
    assert mixins['required'] and mixins['compatibilityLevel'] == 'JAVA_21'
    assert mixins['injectors']['defaultRequire'] == 1
    for mixin in mixins['mixins'] + mixins['client']:
        assert mixins['package'].replace('.', '/') + '/' + mixin + '.class' in names
    assert 'LICENSE.txt' in names
    for path in names:
        if path.endswith('.json'): json.loads(z.read(path))
        if path.endswith('.class'): assert int.from_bytes(z.read(path)[6:8], 'big') == 65, path
        assert not re.match(r'data/[^/]+/(recipes|loot_tables|structures|advancements)/', path), path
        assert '/.cache/' not in path and not path.startswith('.cache/'), path
    for source in ['src/main/resources', 'src/generated/resources']:
        for path in (root / source).rglob('*'):
            relative = path.relative_to(root / source)
            if path.is_file() and '.cache' not in relative.parts:
                assert relative.as_posix() in names, f'Unpackaged resource: {relative}'
    for item in items:
        assert f'assets/mae2/models/item/{item}.json' in names, item
    recipes = [n for n in names if n.startswith('data/mae2/recipe/') and n.endswith('.json')]
    for recipe in recipes:
        result = json.loads(z.read(recipe)).get('result')
        assert result and 'id' in result and 'item' not in result, recipe
    summary = {'artifact': jar.name, 'bytes': jar.stat().st_size,
        'sha256': hashlib.file_digest(jar.open('rb'), 'sha256').hexdigest(),
        'item_models': len(items), 'recipes': len(recipes),
        'loot_tables': sum(n.startswith('data/mae2/loot_table/') and n.endswith('.json') for n in names),
        'test_structures': sum(n.startswith('data/mae2/structure/') and n.endswith('.nbt') for n in names),
        'guides': sum(n.startswith('assets/mae2/ae2guide/') and n.endswith('.md') for n in names),
        'verified': True}
print(json.dumps(summary, indent=2))
