#!/usr/bin/env bash
set -euo pipefail
bash .github/detention/center.sh
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

folder = Path("dist/packaged-smoke")
required = {1: ["refused_auto_exchange_wallet", "refused_equipped_wallet", "refused_multiple_wallets"],
            2: ["occupied_return_kept_conflict_and_escrow"]}
hash_file = lambda p: hashlib.sha512(Path(p).read_bytes()).hexdigest()
for phase, checks in required.items():
    proof = json.loads((folder/f"center-phase{phase}.json").read_text())
    runtime = json.loads((folder/f"runtime249-phase{phase}.json").read_text())
    assert proof["actual_neoforge_21_1_249"] and all(proof[k] for k in checks)
    assert runtime["neoforge"]["version"] == "21.1.249"
    assert runtime["minecraftjail"]["sha512"] == hash_file(os.environ["UQ_JAIL_JAR"])
    assert runtime["unyieldingquests"]["sha512"] == hash_file(os.environ["UQ_BUNDLE_JAR"])
    print(f"PASS: NeoForge 21.1.249, phase {phase}, {len(runtime)} mod IDs including the test harness")
PY
