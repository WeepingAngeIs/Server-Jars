"""Launch the official NeoForge/Minecraft client on an isolated hosted runner."""
import argparse
import os
import shutil
import sys
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
               jvmArguments=["-Xms512m", "-Xmx5G", "-XX:ActiveProcessorCount=4"],
               customResolution=True, resolutionWidth="854", resolutionHeight="480")
if args.join:
    options["quickPlayMultiplayer"] = args.join
args_java = command.get_minecraft_command(version, str(launcher), options)
os.chdir(game)
os.execv(java, args_java)
