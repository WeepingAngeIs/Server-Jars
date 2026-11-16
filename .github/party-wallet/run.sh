#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
kind="${1:?party or wallet required}"
[[ "$kind" == party || "$kind" == wallet ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_integrated_smoke UQ_RESULT_FILE=/tmp/uq-party-wallet-result
for phase in 1 2 3; do
  export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.partyWalletProbe=true -Duq.partyWalletKind=$kind -Duq.partyWalletPhase=$phase -Duq.integratedPhase=$phase"
  bash .github/scripts/meeting-two-player.sh
  cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/resource-$kind-phase$phase-logs"
done
python3 - "$kind" <<'PY'
import hashlib,json,os,sys
from pathlib import Path
p=Path('dist/packaged-smoke');kind=sys.argv[1]
reports=[json.loads((p/f'resource-{kind}-phase{i}.json').read_text()) for i in (1,2,3)]
assert all(r['commit']==os.environ['GITHUB_SHA'] and r['resource']==kind and r['phase']==i for i,r in enumerate(reports,1))
assert all(r['new_admissions_blocked'] and r['failed_admission_preserved_resource'] for r in reports[:2])
assert all(r['real_disconnect_observed'] for r in reports[:2])
assert reports[1]['restart_after_refusal_preserved_resource']
assert reports[2]['restart_in_quarantine_preserved_resource']
assert all(r['escape_death_blocked'] and r['outside_damage_blocked'] for r in reports[1:])
assert reports[2]['release_attempt_preserved_resource']
pair={}
for key,env in [('companion','UQ_BUNDLE'),('jail','UQ_JAIL')]:
    actual=hashlib.sha256(Path(os.environ[env+'_JAR']).read_bytes()).hexdigest()
    assert actual==os.environ[env+'_SHA256']
    pair[key]=actual
(p/f'resource-{kind}-summary.json').write_text(json.dumps({'commit':os.environ['GITHUB_SHA'],'passed_phases':3,'reports':reports,'production_jars_unchanged':True,'sha256':pair,'new_support_enabled':False},indent=2))
PY
