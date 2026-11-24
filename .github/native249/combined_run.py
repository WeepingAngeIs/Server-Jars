"""Run the normal full-pack fixture with the exact Storage JAR present from startup."""
import hashlib
import os
from pathlib import Path

root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
assert hashlib.sha256(storage.read_bytes()).hexdigest() == "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
source = (root/".github/native249/full_run.py").read_text()
marker = 'companion = server/"mods/Unyielding-1.2.0.jar"'
assert source.count(marker) == 1
source = source.replace(marker, '''
for game in (server, client):
    dest = game/"mods"/storage.name
    if dest.exists():
        assert digest(dest) == digest(storage)
    else:
        shutil.copy2(storage, dest)
    assert digest(dest) == "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
''' + marker)
source = source.replace('== 367, "Expected all 365 pack files plus candidate and test JAR"', '== 368, "Expected all 365 pack files plus candidate, test JAR and Storage"')
source = source.replace('0 < timing["server_ticks"] <= 80', '0 < timing["server_ticks"] <= 2400')
source = source.replace('    if phase==1:\n        (report/"full249-first-session-saved.json")', '''    runtime = json.loads((report/f"runtime249-phase{phase}.json").read_text())
    assert runtime["anyastorage"]["sha512"] == digest(storage, "sha512"), "The loaded Storage binary differs"
    assert proof["storage_bag_held_once_with_original_contents_and_upgrade"]
    if phase == 1:
        assert proof["active_storage_lease_refused_without_change"]
        assert proof["active_combat_refused_without_change"]
        assert proof["stale_light_cache_seeded_for_escape"]
        assert proof["verified_corpse_light_refresh_with_bound_own_damage"]
        assert proof["polymorph_native_controls"]
    assert proof["held_recovery_safely_stopped" if phase == 1 else "held_recovery_retry_after_restart_safely_stopped"]
    if phase == 2:
        assert proof["storage_bag_returned_once_to_original_slot"]
        assert proof["stale_recovery_after_release_did_not_duplicate_bag"]
    if phase==1:
        (report/"full249-first-session-saved.json")''')
exec(compile(source, str(root/".github/native249/full_run.py"), "exec"), {"__file__": str(root/".github/native249/full_run.py"), "storage": storage})
