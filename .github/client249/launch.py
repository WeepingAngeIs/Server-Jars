"""Launch the official NeoForge/Minecraft client on an isolated hosted runner."""
import argparse
import os
import shutil
import sys
import subprocess
import time
from pathlib import Path

from minecraft_launcher_lib import command, mod_loader, utils

parser = argparse.ArgumentParser()
parser.add_argument("game", type=Path)
parser.add_argument("--install-only", action="store_true")
parser.add_argument("--join", choices=["127.0.0.1:25565"])
parser.add_argument("--name", default="Visitor", choices=["Visitor", "Dev"])
args = parser.parse_args()
if os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    sys.exit("This test needs a fresh GitHub-hosted runner.")
game = args.game.resolve()
game.mkdir(parents=True, exist_ok=True)
launcher = Path(os.environ.get("CLIENT249_HOME", Path(os.environ["RUNNER_TEMP"])/"client249-launcher")).resolve()
launcher.mkdir(parents=True, exist_ok=True)
loader = mod_loader.get_mod_loader("neoforge")
version = loader.get_installed_version("1.21.1","21.1.249")
java = shutil.which("java")
if not (launcher/"versions"/version/(version+".json")).is_file():
    loader.install("1.21.1", str(launcher), loader_version="21.1.249", java=java)
if args.install_only:
    sys.exit(0)
options = utils.generate_test_options()
options.update(username=args.name, executablePath=java, gameDirectory=str(game),
               jvmArguments=["-Xms512m", "-Xmx9G" if args.join else "-Xmx11G", "-XX:+UseG1GC", "-XX:ActiveProcessorCount=4"],
               customResolution=True, resolutionWidth="854", resolutionHeight="480")
if args.join and os.environ.get("UQ_TEST_JOIN_FROM_TITLE") != "1":
    options["quickPlayMultiplayer"] = args.join
args_java = command.get_minecraft_command(version, str(launcher), options)
# Forge can leave a graphical error screen open indefinitely after a loader crash.
# Capturing the first generated report distinguishes that from a working title screen.
with subprocess.Popen(args_java, cwd=game) as client:
    started = time.monotonic()
    while client.poll() is None:
        crash = next((game/"crash-reports").glob("crash-*.txt"), None)
        if crash is not None:
            client.terminate()
            try:
                client.wait(timeout=20)
            except subprocess.TimeoutExpired:
                client.kill()
            sys.exit(f"Client loading failed. See {crash.name}")
        if time.monotonic() - started > 45:
            latest = game/"logs/latest.log"
            if latest.exists():
                with latest.open("rb") as log:
                    log.seek(max(0, latest.stat().st_size-4000))
                    tail = log.read().decode("utf-8", errors="replace").splitlines()
                print("Client status:", tail[-1][:400] if tail else "starting", flush=True)
            started = time.monotonic()
        time.sleep(2)
    sys.exit(client.returncode)
