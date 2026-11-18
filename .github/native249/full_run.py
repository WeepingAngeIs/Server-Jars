"""Run an ordinary current-pack client against a separate ordinary NeoForge dedicated server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

root = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
scratch = Path(os.environ["RUNNER_TEMP"]).resolve()
assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
server = scratch/"pack249-server"
client = scratch/"pack249-client-native"
report = root/"dist/packaged-smoke"
harness = report/"fullpack-stimulus.jar"
args = argparse.ArgumentParser()
args.add_argument('candidate', type=Path)
args.add_argument('--phase', type=int, choices=[1,2])
options = args.parse_args()
candidate = options.candidate.resolve()
phases = (options.phase,) if options.phase else (1,2)
world = server/"jail_certified_smoke"
marker = Path("/tmp/uq-player-only-result")
if (server/"mods/pack249-census.jar").exists() or (phases[0]==1 and world.exists()):
    raise RuntimeError("Full-pack admission requires a fresh hosted world and no census mod")
if not (server/"libraries/net/neoforged/neoforge/21.1.249/unix_args.txt").is_file():
    raise RuntimeError("Provision the dedicated server with PACK249_INSTALL_ONLY=1")
if not (client/"mods/Unyielding-1.2.0.jar").is_file():
    raise RuntimeError("Provision the full client at RUNNER_TEMP/pack249-client-native")

def digest(path, algorithm="sha256"):
    with path.open("rb") as f:
        return hashlib.file_digest(f,algorithm).hexdigest()

for game in (server,client):
    assert (game/"mods").is_dir()
    for f in (candidate,harness):
        dest=game/"mods"/f.name
        if dest.exists():
            assert digest(dest)==digest(f),dest
        else:
            shutil.copy2(f,dest)

companion = server/"mods/Unyielding-1.2.0.jar"
assert digest(companion) == "ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"
assert digest(client/"mods"/companion.name)==digest(companion)
assert len(list((client/"mods").glob("*.jar"))) == 367, "Expected all 365 pack files plus candidate and test JAR"
if phases[0]==2:
    proof=json.loads((report/"center-phase1.json").read_text())
    assert (world/"level.dat").is_file() and proof["commit"]==os.environ["GITHUB_SHA"]
    assert proof["jail_sha256"]==digest(candidate) and proof["companion_sha256"]==digest(companion)
    assert proof["escape_native_respawn_in_cell_six_without_client_action"]
    checkpoint=json.loads((report/"full249-first-session-saved.json").read_text())
    assert checkpoint=={"commit":os.environ["GITHUB_SHA"],"origin_config":digest(server/"config/neoorigins/admin.toml")}
accepted = root/"dist/packaged-harness/run-server/eula.txt"
assert "eula=true" in accepted.read_text(encoding="utf-8-sig").replace(" ","")
server_properties="""online-mode=false
server-ip=127.0.0.1
server-port=25565
level-name=jail_certified_smoke
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
spawn-protection=0
difficulty=peaceful
spawn-monsters=false
max-players=2
view-distance=2
simulation-distance=2
generate-structures=false
enable-rcon=false
enable-query=false
max-tick-time=-1
"""
if phases[0]==1:
    shutil.copy2(accepted,server/"eula.txt")
    (server/"server.properties").write_text(server_properties)
    (client/"options.txt").write_text("guiScale:2\nrenderDistance:2\nsimulationDistance:2\nmaxFps:30\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\ntutorialStep:none\n")
origin_config=server/"config/neoorigins/admin.toml"
original_origin_config=digest(origin_config)
base_env=dict(os.environ,UQ_PACKAGED_JAIL_SMOKE="1",UQ_TEST_JOIN_FROM_TITLE="1",UQ_JAIL_JAR=str(server/"mods"/candidate.name),
              UQ_BUNDLE_JAR=str(companion),UQ_JAIL_SHA256=digest(candidate),UQ_BUNDLE_SHA256=digest(companion),
              LIBGL_ALWAYS_SOFTWARE="true",ALSOFT_DRIVERS="null")

def stop_group(proc):
    if proc is None:
        return
    try:
        os.killpg(proc.pid,signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        proc.wait(timeout=45)
    except subprocess.TimeoutExpired:
        os.killpg(proc.pid,signal.SIGKILL)
        proc.wait(timeout=15)

def check_alive(proc, name, log):
    if proc.poll() is not None:
        raise RuntimeError(f"{name} exited with {proc.returncode}. Read {log.name}")

def tail(path, limit=24576):
    if not path.exists():
        return ""
    with path.open("rb") as data:
        data.seek(max(0,path.stat().st_size-limit))
        return data.read().decode(errors="replace")

for phase in phases:
    logs=report/f"full249-phase{phase}-logs"
    logs.mkdir()
    marker.unlink(missing_ok=True)
    env=dict(base_env,JAVA_TOOL_OPTIONS=f"-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.playerOnly=true -Duq.playerOnlyPhase={phase} -Duq.certifiedPhase={phase}")
    with (logs/"server.log").open("w") as serverlog,(logs/"client.log").open("w") as clientlog:
        srv=cli=None
        try:
            srv=subprocess.Popen(["java","-Xms512m","-Xmx3G","-Dneoforge.readTimeout=180","@libraries/net/neoforged/neoforge/21.1.249/unix_args.txt","nogui"],
                                 cwd=server,env=env,stdin=subprocess.PIPE,stdout=serverlog,stderr=subprocess.STDOUT,
                                 start_new_session=True,text=True)
            for attempt in range(450):
                check_alive(srv,"Server",logs/"server.log")
                if "Done (" in (logs/"server.log").read_text(errors="replace"):
                    break
                time.sleep(2)
            else:
                raise TimeoutError("Dedicated pack server did not start in 15 minutes")
            cli=subprocess.Popen(["xvfb-run","-a","-s","-screen 0 800x600x24",sys.executable,
                                  str(root/".github/client249/launch.py"),str(client),"--join","127.0.0.1:25565","--name","Visitor"],
                                 cwd=root,env=env,stdout=clientlog,stderr=subprocess.STDOUT,start_new_session=True)
            last_state=""
            since=time.monotonic()
            for attempt in range(600):
                check_alive(srv,"Server",logs/"server.log")
                check_alive(cli,"Client",logs/"client.log")
                if attempt % 15 == 0:
                    memory = {line.split(":",1)[0]:line.split(":",1)[1].strip() for line in Path("/proc/meminfo").read_text().splitlines() if line.startswith(("MemAvailable:","SwapFree:"))}
                    print("Hosted client/server memory", memory, flush=True)
                    client_tail=tail(logs/"client.log")
                    game_tail=tail(client/"logs/latest.log")
                    live=(report/f"client249-phase{phase}-status.txt")
                    screen=live.read_text().strip() if live.exists() else "waiting for first observed client tick"
                    print(f"Full .249 phase {phase} at {2*attempt}s: {screen}",flush=True)
                    if screen!=last_state:
                        last_state=screen
                        since=time.monotonic()
                    age=time.monotonic()-since
                    if phase==1 and not (report/"full249-onboarding-complete.txt").exists() and screen.startswith("screen=") and "overlay=none" in screen:
                        name=screen.split()[0].removeprefix("screen=").rsplit(".",1)[-1]
                        loading={"GenericMessageScreen","ReceivingLevelScreen","ConnectScreen","ProgressScreen","LoadingOverlay","LevelLoadingScreen"}
                        if name=="none" and "world=true" in screen and age>=180:
                            raise RuntimeError("Joined world but first-join onboarding did not finish within three idle minutes: "+screen)
                        if name not in loading and name!="none" and age>=100:
                            raise RuntimeError("Stable client screen blocked normal login/onboarding: "+screen)
                    for message in ("ModLoadingErrorScreen", "LoadingErrorScreen", "ModLoadingFailedScreen",
                                    "This channel is missing on the server side, but required on the client", "Failed to complete lifecycle event CONSTRUCT", "Crash report saved to:", "java.lang.OutOfMemoryError"):
                        if message in client_tail or message in game_tail or message in screen:
                            raise RuntimeError("Client mod loading failure: "+message+"; see full client log")
                    if "DisconnectedScreen" in screen or "QuickPlayErrorScreen" in screen:
                        raise RuntimeError("Official client disconnected or failed Quick Play: "+screen)
                    if "Visitor lost connection:" in tail(logs/"server.log"):
                        raise RuntimeError("Official client disconnected before fixture completion")
                if marker.exists():
                    value=marker.read_text()
                    if value.startswith("failed:"):
                        raise RuntimeError(value)
                    if value.startswith("passed:"):
                        break
                time.sleep(2)
            else:
                raise TimeoutError("Full client did not connect and complete custody checks within 20 minutes")
            # Disconnect before saving, then wait for the dedicated server's own shutdown.
            stop_group(cli)
            cli=None
            srv.stdin.write("save-all flush\nstop\n")
            srv.stdin.flush()
            if srv.wait(timeout=150)!=0:
                raise RuntimeError("Server failed to save and stop")
            serverlog.flush()
            if "All dimensions are saved" not in (logs/"server.log").read_text(errors="replace"):
                raise RuntimeError("Missing native save completion")
        finally:
            stop_group(cli)
            stop_group(srv)
            for game,name in ((client,"client"),(server,"server")):
                latest=game/"logs/latest.log"
                if latest.exists():
                    shutil.copy2(latest,logs/f"{name}-latest.log")
    proof=json.loads((report/f"center-phase{phase}.json").read_text())
    assert proof["commit"]==os.environ["GITHUB_SHA"] and proof["jail_sha256"]==digest(candidate)
    assert proof["actual_neoforge_21_1_249"] and proof["companion_sha256"]==digest(companion)
    assert digest(origin_config)==original_origin_config, "Origin admin config drifted"
    if phase==1:
        assert proof["native_human_warrior_selection_without_unique_claims"]
        assert (report/"full249-onboarding-complete.txt").is_file()
        timing=json.loads((report/"full249-respawn-timing.json").read_text())
        assert 0 < timing["server_ticks"] <= 80 and timing["elapsed_ms"] > 0,timing
        client_view=json.loads((report/"player-only-client-Visitor-1.json").read_text())
        assert client_view["respawn_actions_sent"]==0 and client_view["kill_packets"]==client_view["respawn_packets_after_kill"]==1
        respawn, = client_view["verified_respawns"]
        assert respawn["alive"] and respawn["no_modal_ticks"]>=20 and respawn["dead_player_object"]!=respawn["alive_player_object"]
    for check in (["refused_equipped_wallet","refused_multiple_wallets","refused_auto_exchange_wallet","refused_twilight_pending_charm_inventory",
                   "refused_nonzero_so_bonus","refused_nonzero_so_cycle",
                   "preview_place_undo_and_six_intact_cells","escape_native_respawn_in_cell_six_without_client_action"]
                  if phase==1 else ["occupied_return_kept_conflict_and_escrow","full_wallet_and_equipment_return_after_restart","native_persisted_progress_survived_clone_and_restart"]):
        assert proof[check],check
    if phase==1:
        (report/"full249-first-session-saved.json").write_text(json.dumps({"commit":os.environ["GITHUB_SHA"],"origin_config":original_origin_config}))
    print(f"Exact current full-pack client and dedicated server passed native custody session {phase}.",flush=True)
