#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_REWARD_CLIENT_EVIDENCE="$root/dist/reward-client-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-reward-client-result')
source=source.replace('party-harness','reward-client-harness').replace('party-confirmation','reward-client-acceptance').replace('uq-party','uq-reward-client').replace('UQ_PARTY_PHASE','UQ_REWARD_CLIENT_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_REWARD_CLIENT_PHASE=', '  rm -f /tmp/uq-reward-client-controls/Dev.ready /tmp/uq-reward-client-controls/Visitor.ready /tmp/uq-reward-client-controls/Dev.processed /tmp/uq-reward-client-controls/Visitor.processed\n  export UQ_REWARD_CLIENT_PHASE=')
(root/'.github/scripts/reward-client-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/reward-client-acceptance.generated.sh"
