#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
kind="${1:?}"; [[ "$kind" == party || "$kind" == wallet ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-native-resource-result
for phase in 1 2 3; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyWalletProbe=true -Duq.nativeResourceProbe=true -Duq.partyWalletKind=$kind -Duq.partyWalletPhase=$phase -Duq.integratedPhase=$phase"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/native-$kind-phase$phase-logs"
done
python3 - "$kind" <<'PY'
import hashlib,json,os,sys
from pathlib import Path
p=Path('dist/packaged-smoke');kind=sys.argv[1]
r=[json.loads((p/f'native-{kind}-phase{i}.json').read_text()) for i in (1,2,3)]
assert all(x['commit']==os.environ['GITHUB_SHA'] and x['resource']==kind and x['phase']==i for i,x in enumerate(r,1))
assert r[0]['actual_native_death'] and r[1]['actual_native_respawn']
assert all(x['real_disconnect_observed'] for x in r[:2])
pair={}
for k,e in [('companion','UQ_BUNDLE'),('jail','UQ_JAIL')]:
    pair[k]=hashlib.sha256(Path(os.environ[e+'_JAR']).read_bytes()).hexdigest();assert pair[k]==os.environ[e+'_SHA256']
(p/f'native-{kind}-summary.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'sha256':pair,'reports':r,'jail_admission_certified':False},indent=2))
PY
