"""Boot a real disposable dedicated server twice; keep the candidate out of user profiles."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "dist/optional"
SERVER = WORK / "server"
EVIDENCE = WORK / "evidence"
MODS = SERVER / "mods"
NEO = "21.1.255"
STORAGE = {
    "repository": "WeepingAngeIs/zSortange",
    "commit": "f891228926d0ca30ce8e3f1d2a124db9cd50e84b",
    "run": 44977339522,
    "id": 14736029518,
    "zip": "5783a1394fe7b79739abdcc3cc414de4e7abe905b43570d795e0b4076843f0e0",
    "jar": "8317c78143496bd6edaac5c1ea5880633c08a84c3de997acea84c9abe4dab53b",
}

UWU_REQUIREMENTS = (
    ("lootr", "https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar",
     "091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e"),
    ("another_furniture", "https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar",
     "c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd"),
)

def fetch(url, path):
    subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "180", url, "-o", str(path)], check=True)

def hashfile(path, algorithm="sha256"):
    with path.open("rb") as file:
        return hashlib.file_digest(file, algorithm).hexdigest()

def checked_artifact(binding, target):
    repository, artifact = binding["repository"], binding["id"]
    metadata = json.loads(subprocess.check_output(["gh", "api", f"repos/{repository}/actions/artifacts/{artifact}"]))
    assert metadata["workflow_run"]["id"] == binding["run"]
    assert metadata["workflow_run"]["head_sha"] == binding["commit"]
    assert not metadata["expired"] and metadata["digest"] == "sha256:" + binding["zip"]
    archive = WORK / f"provider-{artifact}.zip"
    with archive.open("wb") as output:
        subprocess.run(["gh", "api", f"repos/{repository}/actions/artifacts/{artifact}/zip"], check=True, stdout=output)
    assert hashfile(archive) == binding["zip"]
    with zipfile.ZipFile(archive) as z:
        if "META-INF/neoforge.mods.toml" in z.namelist():
            shutil.copyfile(archive, target)
        else:
            jars = [i for i in z.infolist() if i.filename.endswith(".jar") and i.file_size < 256 * 1024**2]
            matches = 0
            for entry in jars:
                with z.open(entry) as source, target.open("wb") as dest:
                    shutil.copyfileobj(source, dest)
                if hashfile(target) == binding["jar"]:
                    matches += 1
                    break
            assert matches == 1, "Missing pinned optional mod"
    assert hashfile(target) == binding["jar"]
    return dict(binding)

def run_logged(command, outfile, timeout, env=None):
    with outfile.open("w", encoding="utf-8") as log:
        result = subprocess.run(command, cwd=SERVER, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
    lines = outfile.read_text(errors="replace").splitlines()
    print("\n".join(lines[-22:]), flush=True)
    result.check_returncode()

def main(mode):
    if os.getenv("GITHUB_ACTIONS") != "true" or os.getenv("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("GitHub-hosted runner required")
    assert mode in ("absent", "storage", "uwu", "both")
    MODS.mkdir(parents=True)
    EVIDENCE.mkdir(parents=True)
    module = list((WORK / "module").glob("UnyieldingQuests-*.jar"))
    assert len(module) == 1
    probe = WORK / "module/ConfigtopiaOptionalProbe.jar"
    shutil.copy2(module[0], MODS)
    shutil.copy2(probe, MODS)
    provenance = {"mode": mode, "source": os.environ["GITHUB_SHA"], "quests": hashfile(module[0]), "probe": hashfile(probe)}
    if mode in ("storage", "both"):
        provenance["storage"] = checked_artifact(STORAGE, MODS / "anya-storage-plus.jar")
    if mode in ("uwu", "both"):
        bindings = json.loads((Path(__file__).parent / "uwu-candidate.json").read_text())
        provenance["uwu"] = checked_artifact(bindings, MODS / "UwU-Dimension.jar")
        for name, url, digest in UWU_REQUIREMENTS:
            destination = MODS / f"{name}.jar"
            fetch(url, destination)
            assert hashfile(destination) == digest, name
            provenance[name] = digest
    (EVIDENCE / "inputs.json").write_text(json.dumps(provenance, indent=2))
    repository = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEO}/neoforge-{NEO}-installer.jar"
    installer = WORK / "neoforge-installer.jar"
    subprocess.run(["curl", "-fsSL", "--retry", "3", repository, "-o", str(installer)], check=True)
    sha1 = subprocess.check_output(["curl", "-fsSL", "--retry", "3", repository + ".sha1"], text=True).strip().split()[0]
    assert hashfile(installer, "sha1") == sha1
    run_logged(["java", "-jar", str(installer), "--installServer"], EVIDENCE / "installer.log", 600)
    (SERVER / "eula.txt").write_text("eula=true\n")
    (SERVER / "server.properties").write_text("online-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-seed=486\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
    args = f"@libraries/net/neoforged/neoforge/{NEO}/unix_args.txt"
    for phase in (("first", "reopen") if mode != "absent" else ("first",)):
        result = EVIDENCE / f"{phase}.json"
        env = dict(os.environ, UQ_OPTIONAL_MODE=mode, UQ_OPTIONAL_PHASE=phase, UQ_OPTIONAL_RESULT=str(result))
        run_logged(["java", "-Xms512m", "-Xmx3G", args, "nogui"], EVIDENCE / f"{phase}-server.log", 470, env)
        assert result.is_file(), "Server exited without a probe result"
        report = json.loads(result.read_text())
        print(json.dumps(report, indent=2), flush=True)
        assert report["status"] == "PASS" and report["checks"], report
if __name__ == "__main__":
    main(sys.argv[1])
