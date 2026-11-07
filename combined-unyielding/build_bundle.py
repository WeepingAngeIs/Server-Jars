"""Combine the two reviewed releases on GitHub, preserving all gameplay files exactly."""
import hashlib
import json
import os
from pathlib import Path
import re
import tomllib
import zipfile

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Bundle assembly and validation run only on GitHub-hosted runners.")

ROOT = Path(__file__).resolve().parents[1]
release = json.loads((ROOT / "combined-unyielding/release.json").read_text())
MODS = "META-INF/neoforge.mods.toml"
MANIFEST = "META-INF/MANIFEST.MF"
CHANGED = {MODS, MANIFEST}


def read_input(info):
    path = ROOT / info["path"]
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if "verification_report" in info:
        verified = json.loads((ROOT / info["verification_report"]).read_text())
        assert verified["status"] == "passed" and verified["version"] == info["version"]
        assert verified["jar"] == path.name and verified["development_helpers_in_release"] is False
        assert digest == verified["sha256"], "Verified quest artifact changed before assembly"
        info["sha256"] = digest
    assert digest == info["sha256"], f"Input checksum mismatch: {path}"
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f"Corrupt input: {path}"
        files = [item.filename for item in archive.infolist() if not item.is_dir()]
        assert len(files) == len(set(files)), f"Duplicate input entries: {path}"
        assert not any(re.search(r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$", name, re.I) for name in files), "Signed JAR needs a source rebuild"
        assert not any(name.startswith("/") or ".." in Path(name).parts for name in files), "Unsafe archive path"
        return {name: archive.read(name) for name in files}


original = read_input(release["base"])
quests = read_input(release["quests"])
assert set(original).intersection(quests) == CHANGED, "Unexpected overlap between inputs; review before merging"
base_meta = tomllib.loads(original[MODS].decode())
quest_text = quests[MODS].decode()
quest_meta = tomllib.loads(quest_text)
assert base_meta["modLoader"] == quest_meta["modLoader"] == "javafml"
assert base_meta["loaderVersion"] == quest_meta["loaderVersion"] == "[4,)"
assert base_meta["license"] == quest_meta["license"] == "MIT"
assert [item["modId"] for item in quest_meta["mods"]] == ["unyieldingquests"]
assert quest_meta["mods"][0]["version"] == "${file.jarVersion}"
quest_text = quest_text.replace("${file.jarVersion}", release["quests"]["version"])
quest_body = quest_text[quest_text.index("[[mods]]"):]
metadata = original[MODS].decode().rstrip() + "\n\n# Quest module: original identifiers and explicit component version.\n" + quest_body
parsed = tomllib.loads(metadata)
assert [item["modId"] for item in parsed["mods"]] == release["mod_ids"]
assert len(release["mod_ids"]) == len(set(release["mod_ids"])) == 8
assert parsed["mixins"] == base_meta["mixins"]
assert parsed["accessTransformers"] == base_meta["accessTransformers"]
for mod in base_meta["mods"]:
    assert next(item for item in parsed["mods"] if item["modId"] == mod["modId"]) == mod
for mod_id, dependencies in base_meta["dependencies"].items():
    assert parsed["dependencies"][mod_id] == dependencies
assert parsed["dependencies"]["unyieldingquests"] == quest_meta["dependencies"]["unyieldingquests"]
assert "${" not in metadata

entries = original | quests
entries[MODS] = metadata.encode()
entries[MANIFEST] = (
    "Manifest-Version: 1.0\r\n"
    "FMLModType: MOD\r\n"
    "Implementation-Title: Unyielding\r\n"
    f"Implementation-Version: {release['version']}\r\n"
    f"Unyielding-Quests-Version: {release['quests']['version']}\r\n\r\n"
).encode()
assert not any("/quests/devtest/" in name for name in entries), "Quest test helpers leaked into release"
assert sum(name.startswith("assets/unyieldingquests/textures/quest_art/") and name.endswith(".png") for name in entries) == 923
assert {name for name in entries if name.startswith("data/cusoultome/recipe/")} == {
    "data/cusoultome/recipe/soul_tome.json", "data/cusoultome/recipe/soul_tome_plain.json"
}, "Approved base recipe inventory changed"

output = ROOT / release["output"]
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, payload in sorted(entries.items()):
        info = zipfile.ZipInfo(name, date_time=(2026, 11, 7, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        archive.writestr(info, payload)

with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(entries)
    for source in (original, quests):
        for name, payload in source.items():
            if name not in CHANGED:
                assert archive.read(name) == payload, f"Source gameplay file changed: {name}"

digest = hashlib.sha256(output.read_bytes()).hexdigest()
report = {
    "status": "passed",
    "source_commit": os.environ.get("GITHUB_SHA"),
    "cloud_run": f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
    "name": release["name"],
    "version": release["version"],
    "quest_version": release["quests"]["version"],
    "sha256": digest,
    "inputs": {key: release[key] for key in ("base", "quests")},
    "mod_ids": release["mod_ids"],
    "modified_entries": sorted(CHANGED),
    "preserved_base_entries": len(original) - len(CHANGED),
    "preserved_quest_entries": len(quests) - len(CHANGED),
    "quest_pictures": 923,
    "mixins": parsed["mixins"],
    "runtime_requirements": {"minecraft": "1.21.1", "neoforge": ">=21.1.248", "cobblemon": ">=1.8.4,<1.9", "java": 21},
}
(output.parent / "assembly-report.json").write_text(json.dumps(report, indent=2) + "\n")
(output.parent / "SHA256SUMS.txt").write_text(f"{digest}  {output.name}\n")
print(json.dumps(report, indent=2))
