#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_MEETING_PROVIDER_EVIDENCE="$root/dist/party-provider-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-meeting-provider-result')
source=source.replace('party-harness','meeting-provider-harness').replace('party-confirmation','party-provider-acceptance').replace('uq-party','uq-meeting-provider').replace('UQ_PARTY_PHASE','UQ_MEETING_PROVIDER_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_MEETING_PROVIDER_PHASE=', '  rm -f /tmp/uq-meeting-provider-controls/Dev.ready /tmp/uq-meeting-provider-controls/Visitor.ready /tmp/uq-meeting-provider-controls/Dev.processed /tmp/uq-meeting-provider-controls/Visitor.processed\n  export UQ_MEETING_PROVIDER_PHASE=')
(root/'.github/scripts/party-provider-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/party-provider-acceptance.generated.sh"
