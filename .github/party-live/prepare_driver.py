"""Adapt a COPY of the shared launcher for explicit planned JVM halts only."""
import hashlib
from pathlib import Path

root=Path(__file__).resolve().parents[2]
source=(root/'.github/scripts/meeting-two-player.sh').read_bytes()
assert hashlib.sha1(b'blob '+str(len(source)).encode()+b'\0'+source).hexdigest()=='559dd050f97dc4e704ead06a4940e9c51e9504db', 'Shared launcher changed; inspect before adapting'
text=source.decode()
needle='  # Ask our own server to stop and finish native world/player saves first.\n'
assert text.count(needle)==1
insert=r'''  # A planned Runtime.halt is not a request for graceful-save readiness.
  # Await and verify the actual owned process exit before the generic cleanup
  # can race the exiting JVM. No success is accepted for an arbitrary exit.
  if [[ -n "${UQ_EXPECTED_HALT_CODE:-}" ]]; then
    [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted && -n "$server_pid" ]] || return 1
    if ! await_group "$server_pid"; then
      echo 'Planned crash did not terminate the owned process group' >&2
      diagnose_group "$server_pid"
      kill -- -"$server_pid" 2>/dev/null || true
      await_group "$server_pid" || true
      return 1
    fi
    if wait "$server_pid"; then actual_exit=0; else actual_exit=$?; fi
    python3 "$repository/.github/party-live/check_halt.py" "$actual_exit" "$UQ_EXPECTED_HALT_CODE" > "$logs/planned-halt.json" || return 1
  fi
'''
text=text.replace(needle,insert+needle)
out=root/'dist/party-live-harness';out.mkdir(parents=True,exist_ok=True)
(out/'meeting-two-player.sh').write_text(text)
proof=root/'dist/packaged-smoke';proof.mkdir(parents=True,exist_ok=True)
(proof/'planned-halt-driver.sh').write_text(text)
(proof/'planned-halt-driver.sha256').write_text(hashlib.sha256(text.encode()).hexdigest()+'\n')
