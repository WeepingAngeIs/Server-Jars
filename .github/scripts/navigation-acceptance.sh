#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
export UQ_NAVIGATION_EVIDENCE="$root/dist/navigation-acceptance"
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/party-confirmation.sh').read_text()
for expected in ('/tmp/uq-party-confirmation-result', 'for phase in 1 2;', 'party-harness', 'UQ_PARTY_PHASE'):
    assert expected in source, expected
source=source.replace('/tmp/uq-party-confirmation-result','/tmp/uq-navigation-result')
source=source.replace('party-harness','navigation-harness').replace('party-confirmation','navigation-acceptance').replace('uq-party','uq-navigation').replace('UQ_PARTY_PHASE','UQ_NAVIGATION_PHASE')
source=source.replace('for phase in 1 2;', 'for phase in 1;')
source=source.replace('  export UQ_NAVIGATION_PHASE=', '  rm -f /tmp/uq-navigation-controls/Dev.ready /tmp/uq-navigation-controls/Visitor.ready /tmp/uq-navigation-controls/Dev.processed /tmp/uq-navigation-controls/Visitor.processed\n  export UQ_NAVIGATION_PHASE=')
# Adapt only the disposable display, preserving the upstream process/save lifecycle.
anchor="init=(root/'.github/scripts/meeting-two-player.gradle').read_text()"
assert source.count(anchor)==1
source=source.replace(anchor, anchor+"\nassert init.count(\"'--width', '640', '--height', '480'\")==2\ninit=init.replace(\"'--width', '640', '--height', '480'\",\"'--width', '1024', '--height', '768'\")\nassert source.count('640x480x24')==2\nsource=source.replace('640x480x24','1024x768x24')")
(root/'.github/scripts/navigation-acceptance.generated.sh').write_text(source)
PY
bash "$root/.github/scripts/navigation-acceptance.generated.sh"
