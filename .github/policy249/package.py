"""Seal one hosted-built Jail candidate; preserve prior compatibility provenance."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

assert os.environ.get("GITHUB_ACTIONS") == "true"
assert os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
assert commit == os.environ["GITHUB_SHA"]
name = "Minecraft-Jail-1.3.0-beta.4-test.1.jar"
jar = root / "minecraft-jail/build/libs" / name
old = root / "previous-evidence/candidate/dist/migration-build" / name
old_sha = "8e4e710979d82fc3e12c1931fea3f718173e1efb52bc6e3aa86a02b65ef8aaea"
assert hashlib.sha256(old.read_bytes()).hexdigest() == old_sha
candidate = root / "candidate"
candidate.mkdir()
shutil.copy2(jar, candidate / name)
sha = hashlib.sha256(jar.read_bytes()).hexdigest()
assert sha != old_sha, "The policy candidate must contain its new implementation"
with zipfile.ZipFile(old) as previous, zipfile.ZipFile(jar) as built:
    before = {n: hashlib.sha256(previous.read(n)).hexdigest() for n in previous.namelist() if not n.endswith("/")}
    after = {n: hashlib.sha256(built.read(n)).hexdigest() for n in built.namelist() if not n.endswith("/")}
    assert not any("devtest/" in n or "bundlesmoke/" in n for n in after)
    delta = {"added": sorted(after.keys()-before.keys()), "removed": sorted(before.keys()-after.keys()),
             "changed": sorted(n for n in before.keys() & after.keys() if before[n] != after[n])}
    (candidate / "JAR_ENTRY_DIFF.json").write_text(json.dumps(delta, indent=2)+"\n")
(candidate / "SOURCE_COMMIT.txt").write_text(commit+"\n")
(candidate / "SHA256SUMS.txt").write_text(f"{sha}  {name}\n")
provenance = {
    "candidate_commit": commit, "jar": name, "jar_sha256": sha,
    "run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "runner": "ubuntu-24.04", "minecraft": "1.21.1", "neoforge": "21.1.249",
    "parent_source": "547be9a6b2ce147f1afcd771eae420fe266eabd7",
    "roster_candidate_source": "085e2fba2b5b3bba71d028e7da64dd502590d921",
    "roster_candidate_sha256": "a8b76f433f36dc3e1607bcd9526832386973a7eaa007ae150307fa0334bafec0",
    "roster_candidate_reused_run": "https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45478889426",
    "prior_jail_sha256": old_sha,
    "companion_artifact_id": 14925506112,
    "companion_sha256": "9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904",
    "storage_sha256": "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e",
    "separate_roster_pin": "56172e09426f12aa24a50db27bc03755ce6d7186370febe8ba745acc597dbbee",
    "unapproved_control_roster": "8e0a25e7a8454bc38fffc25596f6a4c26820dd1295bcaf27a13a7240c0906d43",
    "prior_paired_probe": "https://github.com/WeepingAngeIs/Server-Jars/actions/runs/45471190783",
    "acceptance_status": "Built only; consult this run's separate terminal gameplay artifacts",
    "scope": "Disposable hosted published-pack candidate; private/Windows TEST and occupied live worlds unverified"
}
(candidate / "PROVENANCE.json").write_text(json.dumps(provenance, indent=2)+"\n")
subprocess.run(["git", "archive", "--format=zip", "--output="+str(candidate/"source.zip"), "HEAD",
                "AGENTS.md", "minecraft-jail", ".github/policy249", ".github/workflows/jail-policy249.yml"], cwd=root, check=True)
print(json.dumps(provenance, indent=2))
