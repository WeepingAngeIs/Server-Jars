#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_certified_smoke UQ_RESULT_FILE=/tmp/uq-player-only-result
for phase in 1 2; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.playerOnly=true -Duq.playerOnlyPhase=$phase -Duq.certifiedPhase=$phase"
  status=0
  bash .github/scripts/meeting-two-player.sh || status=$?
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/center-phase$phase-logs"
  if ((status)); then
    tail -100 dist/packaged-smoke/jail-player-logs/prepare.log || true
    exit "$status"
  fi
done
python3 - <<'PY'
import json, os
from pathlib import Path
directory = Path("dist/packaged-smoke")
rows = []
for phase, observations in enumerate((
    ["preview_place_undo_and_six_intact_cells","explicit_automatic_distinct_cells_and_no_clobber",
     "escape_native_respawn_in_cell_six_without_client_action"],
    ["restart_preserved_assignments_and_both_exact_inventories_and_wallet","released_cell_reused_by_other_player_and_closed"]
), start=1):
    data = json.loads((directory/f"center-phase{phase}.json").read_text())
    assert data["phase"] == phase and data["commit"] == os.environ["GITHUB_SHA"]
    assert data["jail_sha256"] == os.environ["UQ_JAIL_SHA256"]
    assert data["companion_sha256"] == os.environ["UQ_BUNDLE_SHA256"]
    assert all(data.get(key) is True for key in observations)
    logs = directory/f"center-phase{phase}-logs"
    server = (logs/"server.log").read_text()
    assert (logs/"shutdown-ready.txt").exists()
    for name in ("Dev", "Visitor"):
        assert f"{name} joined the game" in server
        view = json.loads((directory/f"player-only-client-{name}-{phase}.json").read_text())
        assert view["respawn_actions_sent"] == 0
    assert "Cell 1  Occupied · Dev" in server and "Cell 6  Occupied · Visitor" in server
    if phase == 2:
        assert "Cell 1  Available" in server and "Cell 6  Available" in server
    rows.append(data)
print(json.dumps(rows, indent=2))
PY
