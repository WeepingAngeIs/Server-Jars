"""Overlay only the requested follow-up classes onto the verified current JAR."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

baseline = Path('ci-inputs/Unyielding-1.1.8-baseline.jar')
expected = '638ded0f08d806939ed3ce8efafae841386907ec4a33687568a1b00565acc518'
assert hashlib.sha256(baseline.read_bytes()).hexdigest() == expected, 'Wrong baseline JAR'
jars = list(Path('unyielding-quests/build/libs').glob('*.jar'))
jars = [p for p in jars if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
assert len(jars) == 1, f'Expected one quest JAR, got {jars}'

prefix = 'dev/unyielding/quests/'
roots = [
    'Registration', 'UnyieldingQuests',
    'client/AdminInventoryButtons', 'client/InvButtons',
    'commands/ModCommands', 'commands/ForceReviveCommand',
    'settings/CorvisquireStars', 'mixin/PokemonProgressRequirementMixin',
    'mixin/CorvisquireEvolutionGuardMixin',
]
frames = {f'assets/unyieldingquests/textures/gui/sprites/icon_spin_frame_{i}.png' for i in range(4)}
out = Path('dist/118-followup/Unyielding-1.1.8.jar')
out.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(baseline) as base, ZipFile(jars[0]) as component:
    assert len(base.namelist()) == len(set(base.namelist())), 'Duplicate baseline entries'
    names = set(component.namelist())
    updates = {}
    for root in roots:
        class_root = prefix + root
        found = [n for n in names if n == class_root + '.class' or n.startswith(class_root + '$') and n.endswith('.class')]
        assert class_root + '.class' in found, f'Missing compiled {class_root}'
        updates.update((n, component.read(n)) for n in found)
    for frame in frames:
        assert frame in names, f'Missing {frame}'
        updates[frame] = component.read(frame)
    mixin_path = 'assets/unyieldingquests/pokemon.mixins.json'
    old_mixins = json.loads(base.read(mixin_path))
    new_mixins = json.loads(component.read(mixin_path))
    for category in ('mixins', 'client', 'server'):
        if category in new_mixins:
            old_mixins[category] = list(dict.fromkeys(old_mixins.get(category, []) + new_mixins[category]))
    updates[mixin_path] = json.dumps(old_mixins, indent=2).encode()
    cleanup_files = [n for n in names if n.startswith('data/cui_cleanup/') and not n.endswith('/')]
    assert cleanup_files, 'Missing ground cleanup functions'
    updates.update((n, component.read(n)) for n in cleanup_files)
    for tag in ('load', 'tick'):
        path = f'data/minecraft/tags/function/{tag}.json'
        assert path in names, f'Missing cleanup {tag} tag'
        old_tag = json.loads(base.read(path)) if path in base.namelist() else {'values': []}
        new_tag = json.loads(component.read(path))
        for value in new_tag.get('values', []):
            if value not in old_tag.setdefault('values', []):
                old_tag['values'].append(value)
        updates[path] = json.dumps(old_tag, indent=2).encode()
    replaced = set(updates)
    with ZipFile(out, 'w', ZIP_DEFLATED) as result:
        for entry in base.infolist():
            result.writestr(entry, updates.pop(entry.filename, base.read(entry)))
        for name, data in updates.items():
            result.writestr(name, data)
with ZipFile(baseline) as base, ZipFile(out) as result:
    assert len(result.namelist()) == len(set(result.namelist())), 'Duplicate output entries'
    for name in base.namelist():
        if name not in replaced:
            assert base.read(name) == result.read(name), f'Unexpected change: {name}'
sha = hashlib.sha256(out.read_bytes()).hexdigest()
(out.parent / 'SHA256SUMS').write_text(f'{sha}  {out.name}\n')
print(f'Assembled {out}: {sha}; patched {len(replaced)} class/resource entries')
