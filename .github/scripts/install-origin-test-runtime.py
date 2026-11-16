"""Pinned, disposable actual NeoOrigins runtime around the unchanged final JAR."""
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Origin game checks require GitHub-hosted runners')
harness = ROOT / 'dist/packaged-harness'
release = ROOT / 'dist/120-release/Unyielding-1.2.0.jar'
runtime = harness / 'libs/Unyielding-1.2.0.jar'
digest = hashlib.sha256(release.read_bytes()).hexdigest()
assert hashlib.sha256(runtime.read_bytes()).hexdigest() == digest
inputs = [
    ('neoorigins-2.2.27+1.21.1.jar', 'https://cdn.modrinth.com/data/RUtQ9kfe/versions/o7CeCqhv/neoorigins-2.2.27%2B1.21.1.jar', 'a7273acc69ee7b573223fc3073520e9877ca0c91181a7c2c52827e6fa4ad0b894c96f781f02079cce93e6fca080f7c71d9623b68b223ed0546894403048f37d7'),
    ('Origins-Classes-Ex-1.21.1-NeoOrigins-1.0.1.jar', 'https://edge.forgecdn.net/files/8393/343/Origins-Classes-Ex-1.21.1-NeoOrigins-1.0.1.jar', 'c3db2c5b3ac7aaed7756e1986063b8ef50a11e10bf731726bf3c87f9fa32ac4c211344c85a8fd0fb33b156206f25ab6a9b2388e90f78ea8f7f91c402af2d538f'),
    ('Origins-Fantasy-1.21.1-NeoOrigins-1.1.3.jar', 'https://edge.forgecdn.net/files/8816/68/Origins-Fantasy-1.21.1-NeoOrigins-1.1.3.jar', '7746476e70bb14ddb56ab15b7f231c237ee8870bbd6b94fca85b0bcb03b87095f8ece8564c84cf4eb2a8a7596703c1472181285c2664be38a8d6d3ff5fe08600'),
    ('Origins-Furries-1.21.1-NeoOrigins-1.0.3.jar', 'https://edge.forgecdn.net/files/8891/687/Origins-Furries-1.21.1-NeoOrigins-1.0.3.jar', '86426b9dca7a2c23103337b6305efe0dd31ae8a8e50a6268715d1efdb838998ab81a5ce1241edd3a8f09dc6b8e7fba8db3f4a12763d60b769fb3a983d40ec738'),
]
manifest = {'release_sha256': digest, 'integration_mods': []}
for filename, url, sha in inputs:
    target = harness / 'libs' / filename
    if not target.exists():
        for attempt in range(3):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Unyielding-Origin-CI/1.0'}), timeout=120) as response:
                    target.write_bytes(response.read())
                break
            except (OSError, TimeoutError):
                if attempt == 2:
                    raise
                time.sleep(3 * (attempt + 1))
    assert hashlib.sha512(target.read_bytes()).hexdigest() == sha, filename
    manifest['integration_mods'].append({'file': filename, 'url': url, 'sha512': sha})
certificate_path = 'assets/unyieldingquests/jail-certified-runtime.json'
with zipfile.ZipFile(release) as jar:
    certificate_bytes = jar.read(certificate_path)
certificate = json.loads(certificate_bytes)
assert certificate_bytes == (ROOT / 'unyielding-quests/src/main/resources' / certificate_path).read_bytes()
parents = {}
for proof in certificate['embedded']:
    parents.setdefault((proof['parent'], proof['parent_sha512']), []).append(proof)
for (filename, parent_sha), proofs in parents.items():
    parent = harness / 'libs' / filename
    assert hashlib.sha512(parent.read_bytes()).hexdigest() == parent_sha, f'Uncertified embedded parent: {filename}'
    with zipfile.ZipFile(parent) as jar:
        for proof in proofs:
            assert hashlib.sha512(jar.read(proof['entry'])).hexdigest() == proof['sha512'], proof
            assert proof['sha512'] in certificate['sha512']
manifest['custody_certificate_sha256'] = hashlib.sha256(certificate_bytes).hexdigest()
manifest['verified_embedded_binaries'] = len(certificate['embedded'])
for name in ('OriginCustodyPort', 'OriginCustodySmoke', 'CustodyInterfaceSmoke', 'IntegratedJailSmoke', 'ExpandedJailSmoke', 'CertifiedJailSmoke', 'CertifiedPayloadProbe', 'RetainedXpSmoke'):
    source = (ROOT / f'unyielding-quests/src/main/java/dev/unyielding/quests/devtest/{name}.java').read_text(encoding='utf-8')
    source = source.replace('package dev.unyielding.quests.devtest;', 'package dev.unyielding.bundlesmoke;', 1)
    source = source.replace('modid=UnyieldingQuests.MODID', 'modid="uq_bundle_smoke"', 1)
    (harness / f'src/main/java/dev/unyielding/bundlesmoke/{name}.java').write_text(source, encoding='utf-8')
resources = harness / 'src/main/resources/data'
def resource(path, value):
    dest = resources / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value), encoding='utf-8')
for name in ('origin_alpha', 'origin_beta', 'kept_class'):
    resource(f'uq_bundle_smoke/origins/origins/{name}.json', {'name': name, 'description': 'Disposable audited fixture', 'icon': 'minecraft:book', 'powers': [] if name == 'kept_class' else ['uq_bundle_smoke:origin_luck']})
resource('uq_bundle_smoke/origins/powers/origin_luck.json', {'type': 'neoorigins:attribute_modifier', 'attribute': 'minecraft:luck', 'amount': 1.0, 'operation': 'add_value'})
# Extend the real canonical layers without replacing their native/addon choices.
for filename, path, extra, canonical in [
    (inputs[0][0], 'data/neoorigins/origins/origin_layers/origin.json', ['uq_bundle_smoke:origin_alpha', 'uq_bundle_smoke:origin_beta'], 'origin'),
    (inputs[1][0], 'data/origins_classes_ex/origins/origin_layers/class.json', ['uq_bundle_smoke:kept_class'], 'class'),
]:
    with zipfile.ZipFile(harness / 'libs' / filename) as jar:
        layer = json.loads(jar.read(path))
    assert isinstance(layer['origins'], list)
    layer['origins'].extend(extra)
    resource(f'neoorigins/origins/origin_layers/{canonical}.json', layer)
assert hashlib.sha256(runtime.read_bytes()).hexdigest() == digest
(ROOT / 'dist/packaged-smoke/origin-runtime-inputs.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print('Actual origin runtime installed around unchanged assembled JAR:', digest)
