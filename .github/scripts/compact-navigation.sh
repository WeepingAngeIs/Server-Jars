#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_COMPACT_EVIDENCE="$root/dist/compact-navigation"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-compact-result')
source=source.replace('party-harness','compact-harness').replace('party-confirmation','compact-navigation').replace('uq-party','uq-compact').replace('UQ_PARTY_PHASE','UQ_COMPACT_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_COMPACT_PHASE=', '  rm -f /tmp/uq-compact-controls/Dev.ready /tmp/uq-compact-controls/Visitor.ready /tmp/uq-compact-controls/Dev.processed /tmp/uq-compact-controls/Visitor.processed\n  export UQ_COMPACT_PHASE=')
# The inherited runner's native640x480 GUI2 display is the compact viewport.
(root/'.github/scripts/compact-navigation.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/compact-navigation.generated.sh"
