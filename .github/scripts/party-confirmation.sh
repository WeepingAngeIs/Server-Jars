#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root=$(cd "$(dirname "$0")/../.." && pwd)
# Adapt only paths and the existing memory switch, never any production classes.
python3 - "$root" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
source=(root/'.github/scripts/meeting-two-player.sh').read_text()
source=source.replace('project="$repository/unyielding-quests"','project="$repository/dist/party-harness"')
source=source.replace('logs="$repository/meeting-two-player-logs"','logs="$repository/dist/party-confirmation/player-logs"')
# Each adaptation is asserted so a changed upstream runner cannot silently run a development JAR.
assert 'project="$repository/dist/party-harness"' in source
source=source.replace('max-players=4','allow-flight=true\nmax-players=4')
init=(root/'.github/scripts/meeting-two-player.gradle').read_text()
init=init.replace("def clientHeap = System.getenv('UQ_PACKAGED_JAIL_SMOKE') == '1' ? '-Xmx2200m' : '-Xmx1200m'", "def clientHeap = '-Xmx2200m'")
(root/'.github/scripts/party-confirmation.generated.gradle').write_text(init)
source=source.replace('meeting-two-player.gradle','party-confirmation.generated.gradle')
(root/'.github/scripts/party-confirmation-runner.generated.sh').write_text(source)
PY
export UQ_RESULT_FILE=/tmp/uq-party-confirmation-result
for phase in 1 2; do
  mkdir -p /tmp/uq-party-controls
  rm -f /tmp/uq-party-controls/Dev.txt /tmp/uq-party-controls/Dev.ack /tmp/uq-party-controls/Visitor.txt /tmp/uq-party-controls/Visitor.ack
  export UQ_PARTY_PHASE="$phase"
  bash "$root/.github/scripts/party-confirmation-runner.generated.sh"
  cp -a "$root/dist/party-confirmation/player-logs" "$root/dist/party-confirmation/phase-$phase"
done
