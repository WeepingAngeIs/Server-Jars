"""Add nine custom riding definitions to the exact accepted 1.1.6 release in CI."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import tomllib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPECIES = {
    "mawonna", "mawonna_flower", "covislight", "fairy_sliggoo", "inkyeon",
    "lunavee", "buzzeon", "ghost_jirachi", "creepyeon",
}


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def main() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("Assembly and game checks run only on GitHub-hosted runners.")
    config = json.loads((ROOT / "riding-update/release.json").read_text())
    base_path = ROOT / config["base"]["path"]
    assert digest(base_path.read_bytes()) == config["base"]["sha256"], "Baseline hash changed"
    with zipfile.ZipFile(base_path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        assert len(names) == len(set(names)), "Duplicate baseline archive paths"
        for name in names:
            assert not PurePosixPath(name).is_absolute() and ".." not in PurePosixPath(name).parts
            assert not re.search(r"^META-INF/.*\.(SF|RSA|DSA)$", name, re.I), "Signed input cannot be repackaged"
        baseline = {name: archive.read(name) for name in names}
    original_metadata = tomllib.loads(baseline["META-INF/neoforge.mods.toml"].decode())
    assert len(original_metadata["mods"]) == 9, "Review baseline component changes"
    assert original_metadata["modLoader"] == "javafml"
    output = dict(baseline)
    pack_config = config["pack"]
    url = f"https://raw.githubusercontent.com/{pack_config['repository']}/{pack_config['commit']}/Mawonna.zip"
    request = urllib.request.Request(url, headers={"User-Agent": "Unyielding-Riding-Cloud-Assembly/1.0"})
    with urllib.request.urlopen(request, timeout=180) as response:
        pack_bytes = response.read()
    blob = hashlib.sha1(b"blob " + str(len(pack_bytes)).encode() + b"\0" + pack_bytes).hexdigest()
    assert blob == pack_config["blob"], "Pinned model pack changed"
    pack_entries = {}
    merged_language = "assets/mawonna/lang/en_us.json"
    with zipfile.ZipFile(io.BytesIO(pack_bytes)) as archive:
        all_names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        assert len(all_names) == len(set(all_names)), "Duplicate model-pack paths"
        for name in all_names:
            assert not PurePosixPath(name).is_absolute() and ".." not in PurePosixPath(name).parts
            if not name.startswith(("assets/", "data/", "LICENSES/")) and name != "pack.mcmeta":
                continue
            value = archive.read(name)
            if name in output and output[name] != value:
                assert name == merged_language, "Unexpected baseline/model-pack conflict: " + name
                original = json.loads(output[name])
                incoming = json.loads(value)
                for key in original.keys() & incoming.keys():
                    assert original[key] == incoming[key], "Existing translation changed: " + key
                value = (json.dumps(original | incoming, ensure_ascii=False, indent=2) + "\n").encode()
            output[name] = value
            pack_entries[name] = digest(value)
    assert len(pack_entries) > 100 and merged_language in pack_entries
    targets = set()
    additions = {}
    for source in sorted((ROOT / "riding-update/data").rglob("*.json")):
        name = source.relative_to(ROOT / "riding-update").as_posix()
        assert name.startswith("data/cu_rideable/species_additions/custom_")
        value = source.read_bytes()
        data = json.loads(value)
        assert set(data) == {"target", "riding"}, "Riding must not overwrite other species properties"
        species = data["target"].removeprefix("cobblemon:")
        assert species in SPECIES and species not in targets
        assert data["target"] == "cobblemon:" + species
        assert data["riding"]["seats"] == [{"locator": "middle"}]
        assert data["riding"]["behaviours"]
        assert name not in output, "Unexpected collision with accepted baseline"
        targets.add(species)
        output[name] = value
        additions[name] = digest(value)
    assert targets == SPECIES, "Missing or extra custom species"
    manifest = baseline["META-INF/MANIFEST.MF"].decode()
    assert re.search(r"^Implementation-Version: .+$", manifest, re.M)
    output["META-INF/MANIFEST.MF"] = re.sub(
        r"(?m)^Implementation-Version: [^\r\n]+", "Implementation-Version: " + config["version"], manifest
    ).encode()
    changed = {name for name in baseline if output.get(name) != baseline[name]}
    assert changed == {"META-INF/MANIFEST.MF", merged_language}, "Unrelated production payload changed"
    assert output["META-INF/neoforge.mods.toml"] == baseline["META-INF/neoforge.mods.toml"]
    destination = ROOT / config["output"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(output.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, content)
    with zipfile.ZipFile(destination) as archive:
        assert len(archive.namelist()) == len(output)
        assert all(archive.read(name) == value for name, value in output.items())
    report = {
        "status": "passed", "version": config["version"], "output": destination.name,
        "sha256": digest(destination.read_bytes()), "base": config["base"],
        "base_entries_preserved": len(baseline) - len(changed),
        "changed_existing_entries": sorted(changed),
        "production_modules": {mod["modId"]: mod["version"] for mod in original_metadata["mods"]},
        "riding_additions": additions, "native_riding_overrides_added": 0,
        "reference": config["reference"], "embedded_model_pack": config["pack"],
        "embedded_pack_sha256": digest(pack_bytes), "embedded_pack_entries": pack_entries,
    }
    (destination.parent / "assembly-report.json").write_text(json.dumps(report, indent=2) + "\n")
    (destination.parent / "SHA256SUMS").write_text(report["sha256"] + "  " + destination.name + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
