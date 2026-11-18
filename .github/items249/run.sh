#!/usr/bin/env bash
set -euo pipefail
bash .github/native249/run.sh
python3 - <<'PY'
import hashlib
import json
from pathlib import Path
p=Path("dist/packaged-smoke")
providers=json.loads((p/"items249-inputs.json").read_text())["providers"]
for phase in (1,2):
    evidence=json.loads((p/f"center-phase{phase}.json").read_text())
    runtime=json.loads((p/f"runtime249-phase{phase}.json").read_text())
    assert set(providers.values()) <= {row["sha512"] for row in runtime.values()}
    excluded={"minecraft","minecraftjail","neoforge","uq_bundle_smoke"}
    rows=sorted(f"{name}={row['version']}:{row['sha512']}" for name,row in runtime.items() if name not in excluded)
    assert hashlib.sha256("\n".join(rows).encode()).hexdigest()=="681fb2244140c28863053b222ae32aeedd0c1b4379d8023b4a0e867a57fdf871"
    checks=(["actual_locker_correct_two_sources_and_no_live_handles","intact_native_bags_and_food_admission",
             "cached_native_handler_denied_without_mutating_backpack","refused_duplicate_backpack_handle","intact_native_bags_and_food_respawn"]
            if phase==1 else ["intact_native_bags_and_food_reconnect","restored_filled_bags_exact_slots_uuid_storage_and_food",
                              "ordinary_death_kept_stock_food_reset"])
    assert all(evidence.get(check) is True for check in checks), checks
    print(f"Verified native item and food evidence in phase {phase}")
PY
