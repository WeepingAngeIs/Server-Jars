#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
export UQ_PACKAGED_JAIL_SMOKE=1 LIBGL_ALWAYS_SOFTWARE=true ALSOFT_DRIVERS=null
export UQ_WORLD_NAME=jail_certified_smoke UQ_RESULT_FILE=/tmp/uq-player-only-result
candidate="${MIGRATION_CANDIDATE:-$GITHUB_WORKSPACE/candidate/dist/migration-build/Minecraft-Jail-1.2.0.jar}"
old="${MIGRATION_PREDECESSOR:-$GITHUB_WORKSPACE/old-evidence/dist/production-candidate/Minecraft-Jail-1.2.0.jar}"
export MIGRATION_OLD_SHA512=$(sha512sum "$old" | cut -d' ' -f1)
export MIGRATION_CANDIDATE_SHA512=$(sha512sum "$candidate" | cut -d' ' -f1)
[[ $(sha256sum "$old" | cut -d' ' -f1) == "${MIGRATION_OLD_SHA256:-c04a0564022ffdcfd1e9c0710ffd024260994498ea786cbab68711159fdc2883}" ]]
for phase in 1 2 3 4 5; do
 if [[ "$phase" == 1 ]]; then cp "$old" "$UQ_JAIL_JAR"; else cp "$candidate" "$UQ_JAIL_JAR"; fi
 export UQ_JAIL_SHA256=$(sha256sum "$UQ_JAIL_JAR" | cut -d' ' -f1)
 export JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.playerOnly=true -Duq.playerOnlyPhase=$phase -Duq.certifiedPhase=$phase"
 if bash .github/scripts/meeting-two-player.sh; then status=0; else status=$?; fi
 cp -a dist/packaged-smoke/jail-player-logs "dist/packaged-smoke/migration-phase$phase-logs"
 [[ "$status" == 0 ]] || exit "$status"
 if [[ "$phase" == 1 ]]; then
  # The harness has stopped every owned process and released the world lock.
  tar -czf dist/packaged-smoke/synthetic-old-save.tar.gz -C dist/packaged-harness/run-server jail_certified_smoke
  mkdir -p dist/packaged-harness/run-server/jail_certified_smoke/jail-migration-inputs
  cp "$old" dist/packaged-harness/run-server/jail_certified_smoke/jail-migration-inputs/old.jar
 fi
done
python3 - <<'PY'
import json,pathlib
p=pathlib.Path('dist/packaged-smoke');r=[json.loads((p/f'migration-phase{i}.json').read_text()) for i in range(1,6)]
required={2:['unapproved_old_receipt_refused','prepared_interruption'],3:['prepared_restart_idempotent','tampered_backup_refused','wrong_proposal_refused','before_commit_interruption'],4:['commit_restart_and_repeated_commit_safe','migrated_escape_native_autorespawn_without_button'],5:['exact_return_after_migration_restart','no_duplicate_return']}
for n,keys in required.items():
 for k in keys: assert r[n-1].get(k) is True,(n,k)
assert all(x['pokemon_modified'] is False for x in r)
(p/'migration-summary.json').write_text(json.dumps({'phases':r,'scope':'synthetic occupied predecessor only; private saves unverified','deployment':False},indent=2))
PY
