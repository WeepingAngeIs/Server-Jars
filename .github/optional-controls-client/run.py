"""Exercise the packaged settings menus in a temporary, hosted Minecraft client."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / ".github/optional-controls"
sys.path.insert(0, str(FIXTURE))
from dedicated import UWU_REQUIREMENTS, checked_artifact, fetch, hashfile


def main():
    if os.getenv("GITHUB_ACTIONS") != "true" or os.getenv("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("Requires a GitHub-hosted runner")
    project = ROOT / "unyielding-quests"
    stage = ROOT / "dist/optional"
    mods = stage / "client-mods"
    evidence = stage / "client-evidence"
    mods.mkdir(parents=True)
    evidence.mkdir(parents=True)
    binding = json.loads((FIXTURE / "uwu-candidate.json").read_text())
    checked_artifact(binding, mods / "uwu.jar")
    module, = (stage / "module").glob("UnyieldingQuests-*.jar")
    shutil.copyfile(module, mods / "quests.jar")
    for name, url, expected in UWU_REQUIREMENTS:
        target = mods / f"{name}.jar"
        fetch(url, target)
        assert hashfile(target) == expected, name
    provenance = {"commit": os.environ["GITHUB_SHA"], "provider": binding,
                  "files": {p.name: hashfile(p) for p in sorted(mods.glob("*.jar"))}}
    (evidence / "inputs.json").write_text(json.dumps(provenance, indent=2))
    game = project / "build/optional-client"
    (game / "config").mkdir(parents=True)
    (game / "options.txt").write_text(
        "version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:3\n"
        "simulationDistance:5\nmaxFps:40\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\n")
    (game / "config/fml.toml").write_text("earlyWindowControl = false\n")
    log = evidence / "client.log"
    command = ["timeout", "--kill-after=15s", "23m", "xvfb-run", "-a", "-s", "-screen 0 1280x720x24",
               "bash", "gradlew", "--console=plain", "--no-daemon", "--max-workers=2", "-I",
               "../.github/optional-controls-client/client.gradle", "runConfigtopiaUi"]
    with log.open("w") as output:
        result = subprocess.run(command, cwd=project, stdout=output, stderr=subprocess.STDOUT,
                                env={**os.environ, "LIBGL_ALWAYS_SOFTWARE": "1", "ALSOFT_DRIVERS": "null"})
    print("\n".join(log.read_text(errors="replace").splitlines()[-60:]), flush=True)
    result.check_returncode()
    report = json.loads((evidence / "results.json").read_text())
    assert report == {"cupidBowCharmSeconds": 91, "throwCooldownSeconds": 2.5}
    for image in ("choose-weapon", "cupid-seconds", "dirk-slider-and-seconds"):
        assert (evidence / f"{image}.png").stat().st_size > 1024, image


if __name__ == "__main__":
    main()
