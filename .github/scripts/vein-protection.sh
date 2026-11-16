#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_VEIN_EVIDENCE="$root/dist/vein-protection"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
source=source.replace('party-harness','vein-harness').replace('party-confirmation','vein-protection').replace('uq-party','uq-vein')
source=source.replace('for phase in 1 2; do','for phase in 1; do')
needle="source=source.replace('max-players=4'"
assert needle in source
source=source.replace(needle,"source=source.replace('spawn-protection=0','spawn-protection=16')\n"+needle)
(root/'.github/scripts/vein-protection.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/vein-protection.generated.sh"
