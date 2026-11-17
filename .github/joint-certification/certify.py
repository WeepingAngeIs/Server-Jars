"""One exact companion pin, on a copy of the proven wallet-only Jail binary."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[2]
WALLET_SOURCE = '8e9716d94a932fe5110ae84abdc90adfe0017974'
COMPANION_SOURCE = '51c23f468fa2af1510d93834946269298b7c83f6'
OLD_JAIL = '5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e'
OLD_COMPANION = '61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
NEW_COMPANION = '7a0a1cec09001ca6f47ee6180371842f27ef6d83f7411a7e61fff6a83e74b414'
NEW_PIN = '9a36367d2bd92a1a3acd570de31649c0db02de5aea32d2d933a323212ccb2a35be6fdb48bc7932771c95998121a25b0467a131c39ae879c5c6481154fdddcbd6'
CERT = 'assets/unyieldingquests/jail-certified-runtime.json'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def extend(raw):
    before = json.loads(raw)
    assert NEW_PIN not in before['sha512'], 'Expected an actual new companion pin'
    after = dict(before)
    after['sha512'] = sorted(set(before['sha512']) | {NEW_PIN})
    assert len(after['sha512']) == len(before['sha512']) + 1
    return (json.dumps(after, indent=2) + '\n').encode()


def main():
    old = ROOT / 'dist/inputs/wallet/Minecraft-Jail-1.2.0.jar'
    old_companion = ROOT / 'dist/inputs/wallet/Unyielding-1.2.0.jar'
    companion = ROOT / 'dist/inputs/current/dist/120-release/Unyielding-1.2.0.jar'
    assert sha(old.read_bytes()) == OLD_JAIL
    assert sha(old_companion.read_bytes()) == OLD_COMPANION
    data = companion.read_bytes()
    assert sha(data) == NEW_COMPANION and hashlib.sha512(data).hexdigest() == NEW_PIN
    proof = json.loads((old.parent / 'packed-wallet-runtime.json').read_text())
    assert proof['passed'] and proof['commit'] == WALLET_SOURCE
    assert proof['jars'] == {old.name: OLD_JAIL, old_companion.name: OLD_COMPANION}
    out = ROOT / 'dist/joint-pair'
    out.mkdir(parents=True, exist_ok=True)
    (out / companion.name).write_bytes(data)
    with ZipFile(old) as source, ZipFile(out / old.name, 'w', compression=ZIP_DEFLATED, compresslevel=9) as target:
        assert len(source.namelist()) == len(set(source.namelist()))
        certificate = extend(source.read(CERT))
        for name in sorted(source.namelist()):
            target.writestr(name, certificate if name == CERT else source.read(name))
    # Canonical ZIP metadata, so the exact same inputs always produce one hash.
    from zipfile import ZipInfo
    rewritten = out / 'canonical.jar'
    with ZipFile(out / old.name) as source, ZipFile(rewritten, 'w', compression=ZIP_DEFLATED, compresslevel=9) as target:
        for name in sorted(source.namelist()):
            info = ZipInfo(name, (2026, 11, 17, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, source.read(name), compress_type=ZIP_DEFLATED, compresslevel=9)
    rewritten.replace(out / old.name)
    with ZipFile(old) as source, ZipFile(out / old.name) as target:
        assert source.namelist() and set(source.namelist()) == set(target.namelist())
        changed = [n for n in source.namelist() if source.read(n) != target.read(n)]
        assert changed == [CERT], changed
        before = json.loads(source.read(CERT)); after = json.loads(target.read(CERT))
        expected = dict(before); expected['sha512'] = sorted(set(before['sha512']) | {NEW_PIN})
        assert after == expected
    jars = {p.name: sha(p.read_bytes()) for p in out.glob('*.jar')}
    report = {'wallet_source': WALLET_SOURCE, 'companion_source': COMPANION_SOURCE,
              'input_jail_sha256': OLD_JAIL, 'jars': jars, 'added_sha512': NEW_PIN,
              'changed_jail_entries': changed, 'production_code_changed': False,
              'admissions_default_off': True, 'deployment_authorized': False,
              'scope': 'Pending disposable runtime validation; one exact companion pin only'}
    (out / 'pair-source.json').write_text(json.dumps(report, indent=2) + '\n')
    (out / 'joint-runtime-certificate.json').write_bytes(certificate)
    (out / 'SHA256SUMS').write_text(''.join(f'{v}  {k}\n' for k, v in sorted(jars.items())))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
