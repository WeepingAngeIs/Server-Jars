#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_CONFIG_AUDIT_EVIDENCE="$root/dist/config-audit-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-config-audit-result')
source=source.replace('party-harness','config-audit-harness').replace('party-confirmation','config-audit-acceptance').replace('uq-party','uq-config-audit').replace('UQ_PARTY_PHASE','UQ_CONFIG_AUDIT_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_CONFIG_AUDIT_PHASE=', '  rm -f /tmp/uq-config-audit-controls/Dev.ready /tmp/uq-config-audit-controls/Visitor.ready /tmp/uq-config-audit-controls/Dev.processed /tmp/uq-config-audit-controls/Visitor.processed\n  export UQ_CONFIG_AUDIT_PHASE=')
(root/'.github/scripts/config-audit-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/config-audit-acceptance.generated.sh"
