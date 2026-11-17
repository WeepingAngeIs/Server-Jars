#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_DESTINATION_EVIDENCE="$root/dist/destination-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-destination-result')
source=source.replace('party-harness','destination-harness').replace('party-confirmation','destination-acceptance').replace('uq-party','uq-destination').replace('UQ_PARTY_PHASE','UQ_DESTINATION_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_DESTINATION_PHASE=', '  rm -f /tmp/uq-destination-controls/Dev.ready /tmp/uq-destination-controls/Visitor.ready /tmp/uq-destination-controls/Dev.processed /tmp/uq-destination-controls/Visitor.processed\n  export UQ_DESTINATION_PHASE=')
(root/'.github/scripts/destination-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/destination-acceptance.generated.sh"
