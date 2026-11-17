#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_GROUND_EVIDENCE="$root/dist/ground-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-ground-result')
source=source.replace('party-harness','ground-harness').replace('party-confirmation','ground-acceptance').replace('uq-party','uq-ground').replace('UQ_PARTY_PHASE','UQ_GROUND_PHASE')
source=source.replace('  export UQ_GROUND_PHASE=', '  rm -f /tmp/uq-ground-controls/Dev.ready /tmp/uq-ground-controls/Visitor.ready\n  export UQ_GROUND_PHASE=')
(root/'.github/scripts/ground-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/ground-acceptance.generated.sh"
