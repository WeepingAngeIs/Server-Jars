"""Fingerprint generated synthetic defaults only. Never upload configuration contents."""
import hashlib
import json
import os
from pathlib import Path

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Hosted disposable environment only')
root=Path(__file__).resolve().parents[2]
harness=root/'dist/vein-harness/run-server'
rows=[]
for relative in ('config/curanks/ranks.json','config/openpartiesandclaims-server.toml','world/serverconfig/openpartiesandclaims-server.toml'):
    path=harness/relative
    row={'generated_relative':relative,'exists':path.is_file()}
    if path.is_file():
        value=path.read_text(encoding='utf-8-sig').replace('\r\n','\n').rstrip()
        row['normalized_sha256']=hashlib.sha256(value.encode()).hexdigest()
    rows.append(row)
output=root/'dist/vein-protection/generated-default-fingerprints.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(rows,indent=2)+'\n')
