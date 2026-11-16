"""Review-time certificate restrictions, independent of runtime admission checks."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
PROOF = ROOT / 'minecraft-jail/companion-certificate.json'
CERTIFICATE = 'assets/unyieldingquests/jail-certified-runtime.json'

def validate_extension(before, after):
    proof = json.loads(PROOF.read_text())
    assert proof['source_commit'] == '0215b636b0897b8a7b23ea319f3aec436011ba4b'
    assert len(proof['artifacts']) == 2
    pins = {a['sha512'] for a in proof['artifacts']}
    assert len(pins) == 2 and all(len(x) == 128 and set(x) <= set('0123456789abcdef') for x in pins)
    expected = json.loads(before)
    expected['sha512'] = sorted(set(expected['sha512']) | pins)
    assert json.loads(after) == expected, 'Certificate drift beyond the two reviewed companion byte hashes'

def verify_companion(path):
    proof = json.loads(PROOF.read_text())
    data = Path(path).read_bytes()
    sha256, sha512 = hashlib.sha256(data).hexdigest(), hashlib.sha512(data).hexdigest()
    assert any(a['sha256'] == sha256 and a['sha512'] == sha512 and a['bytes'] == len(data)
               for a in proof['artifacts']), 'Companion is not an exact reviewed binary'
    h = hashlib.sha256()
    with ZipFile(path) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'Duplicate companion members'
        for name in sorted(names):
            encoded = name.encode(); content = jar.read(name)
            h.update(len(encoded).to_bytes(8, 'big')); h.update(encoded)
            h.update(len(content).to_bytes(8, 'big')); h.update(content)
    assert h.hexdigest() == proof['entry_sha256'], 'Reviewed companion content changed'
    return {'sha256': sha256, 'sha512': sha512, 'entry_sha256': h.hexdigest()}
