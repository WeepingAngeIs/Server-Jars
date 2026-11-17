"""Exact approved increment only; reject certificate, non-jail, legacy-fixture drift."""
import hashlib,json,subprocess
from pathlib import Path
m=json.loads(Path('.github/packed-wallet/production-delta.json').read_text())
base=m['base']
def h(data):return hashlib.sha256(data).hexdigest()
def old(name):return subprocess.check_output(['git','show',f'{base}:{name}'])
changed=set(subprocess.check_output(['git','diff','--name-only',base,'HEAD','--','minecraft-jail/src/main'],text=True).splitlines())
assert changed==set(m['changed'])|set(m['added']),changed
for name,pair in m['changed'].items():assert h(old(name))==pair['before'] and h(Path(name).read_bytes())==pair['after'],name
for name,digest in m['added'].items():assert h(Path(name).read_bytes())==digest,name
subprocess.run(['git','diff','--exit-code',base,'HEAD','--','unyielding-quests','combined-unyielding','currency-bridge','iv-substitute-update','model-hotfix','riding-update','settings-update','tutorial-update','minecraft-jail/src/legacySmoke','minecraft-jail/src/main/resources','minecraft-jail/companion-certificate.json'],check=True)
print('Exact Jail-only wallet delta verified; companion certificate, party boundary, non-jail source and legacy fixtures preserved')
