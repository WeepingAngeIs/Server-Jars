"""Add pinned actual integration mods/test fixtures without rebuilding the tested release JAR."""
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Accessory game checks run only on GitHub-hosted runners')
harness = ROOT / 'dist/packaged-harness'
release = ROOT / 'dist/120-release/Unyielding-1.2.0.jar'
runtime_jar = harness / 'libs/Unyielding-1.2.0.jar'
release_hash = hashlib.sha256(release.read_bytes()).hexdigest()
assert hashlib.sha256(runtime_jar.read_bytes()).hexdigest() == release_hash, 'Runtime release differs before accessory installation'
inputs = [
    ('accessories-neoforge-1.1.0-beta.53+1.21.1.jar', 'jtmvUHXj/Fb55Fgjz',
     'baafa9a5e48c17c243d45b6260095ffef2ad00e4e970aafc5b1ca9ab5f4a542b18b0fb35d4584318791edbaf00a3c44806f62062513d991383192aca4df27a07'),
    ('accessories_compat_layer-neoforge-0.1.12+1.21.1.jar', 'ySnjMzLg/JMgVNypu',
     'f729c4e1c069182aac89805551a056b937e17fd6d9c031b14a6003b5c4fac609563f2c3d6786fe56ecfb62a1094124e68b2e4fb6e2612818d899d3bac181c215'),
    ('curios-neoforge-9.5.1+1.21.1.jar', 'vvuO3ImH/yohfFbgD',
     '5981a267686b744e7e3c227f78cbcd5267c14ac6979a28e814695f4589273998563147207fef4a5cdb7cdbdc39797cd95d9e4abadb55869f18e02a38d0654ae5'),
]
manifest = {'release_sha256': release_hash, 'integration_mods': []}
for filename, ids, sha512 in inputs:
    target = harness / 'libs' / filename
    project, version = ids.split('/')
    url = f'https://cdn.modrinth.com/data/{project}/versions/{version}/{filename.replace("+", "%2B")}'
    if not target.exists():
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'Unyielding-Accessory-CI/1.0'})
                with urllib.request.urlopen(request, timeout=120) as response:
                    target.write_bytes(response.read())
                break
            except (OSError, TimeoutError):
                if attempt == 2:
                    raise
                time.sleep(3 * (attempt + 1))
    assert hashlib.sha512(target.read_bytes()).hexdigest() == sha512, f'Wrong pinned integration JAR: {filename}'
    manifest['integration_mods'].append({'file': filename, 'url': url, 'sha512': sha512})
for name in ('AccessoryCustodyPort', 'AccessoryCustodySmoke'):
    source = (ROOT / f'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/{name}.java').read_text(encoding='utf-8')
    source = source.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;', 1)
    source = source.replace('@EventBusSubscriber(modid=UnyieldingQuests.MODID)', '@EventBusSubscriber(modid="uq_bundle_smoke")', 1)
    (harness / f'src/main/java/dev/unyielding/bundlesmoke/{name}.java').write_text(source, encoding='utf-8')
tags = harness / 'src/main/resources/data/accessories/tags/item'
tags.mkdir(parents=True, exist_ok=True)
(tags / 'ring.json').write_text('{"replace":false,"values":["minecraft:gold_ingot","minecraft:amethyst_shard","minecraft:diamond"]}', encoding='utf-8')
assert hashlib.sha256(runtime_jar.read_bytes()).hexdigest() == release_hash, 'Release JAR changed during accessory installation'
(ROOT / 'dist/packaged-smoke/accessory-runtime-inputs.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print('Accessory integration installed around unchanged assembled JAR:', release_hash)
