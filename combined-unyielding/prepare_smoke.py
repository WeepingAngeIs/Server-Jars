#!/usr/bin/env python3
"""Prepare a separate final-JAR smoke harness on GitHub-hosted runners only."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / "combined-unyielding/smoke-harness"
EVIDENCE = ROOT / "dist/combined-smoke"
DEPENDENCIES = {
    "Cobblemon 1.8.4 NeoForge": "xsVHrAsY",
    "Kotlin for Forge 5.12.0": "uhJhCT7X",
    "Mega Showdown 1.3.3": "TrpwiTL5",
    "Accessories 1.1.0-beta.53": "Fb55Fgjz",
    "owo-lib 0.12.15.5-beta.1": "NMCHU6DZ",
    "Architectury 13.0.11": "1IiqEQGl",
}
PACK_COMMIT = "e14c21e0fe72890cc0d1920d3adf290cf649875c"
PACK_BLOB = "f5435230f748e06fb37cde561a622c3e30d48b4b"
PACK_BASE = f"https://raw.githubusercontent.com/WeepingAngeIs/unyielding-mod/{PACK_COMMIT}"
HEADERS = {"User-Agent": "Unyielding-Combined-Cloud-Smoke/1.0 (GitHub Actions)"}


def download(url: str, target: Path) -> bytes:
    target.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=180) as response:
                with target.open("wb") as output:
                    shutil.copyfileobj(response, output)
            return target.read_bytes()
        except (OSError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(3 * (attempt + 1))
    raise AssertionError("Unreachable")


def main() -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("Preparation and game checks run only on GitHub-hosted runners.")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    libs = HARNESS / "libs"
    libs.mkdir(parents=True, exist_ok=True)
    if list(libs.glob("*.jar")):
        raise SystemExit("Use a clean cloud harness; stale runtime JARs are present.")

    release = ROOT / "dist/combined/Unyielding-1.1.0.jar"
    final_jar = libs / release.name
    shutil.copy2(release, final_jar)
    sha256 = hashlib.sha256(final_jar.read_bytes()).hexdigest()
    manifest = [{"label": "Actual combined release JAR", "file": final_jar.name, "sha256": sha256}]
    for label, version in DEPENDENCIES.items():
        request = urllib.request.Request(f"https://api.modrinth.com/v2/version/{version}", headers=HEADERS)
        with urllib.request.urlopen(request, timeout=90) as response:
            info = json.load(response)
        if info["id"] != version:
            raise AssertionError("Unexpected Modrinth version response")
        selected = next((item for item in info["files"] if item.get("primary")), info["files"][0])
        filename = selected["filename"]
        if Path(filename).name != filename or not filename.endswith(".jar"):
            raise AssertionError("Unexpected runtime dependency filename")
        content = download(selected["url"], libs / filename)
        if hashlib.sha512(content).hexdigest() != selected["hashes"]["sha512"]:
            raise AssertionError("Published checksum mismatch: " + filename)
        manifest.append({"label": label, "version_id": version, "file": filename,
                         "url": selected["url"], "sha512": selected["hashes"]["sha512"]})
        print("Verified runtime dependency:", label, flush=True)

    pack = HARNESS / "run/resourcepacks/Mawonna.zip"
    content = download(PACK_BASE + "/Mawonna.zip", pack)
    git_blob = hashlib.sha1(b"blob " + str(len(content)).encode("ascii") + b"\0" + content).hexdigest()
    if git_blob != PACK_BLOB:
        raise AssertionError("Pinned Mawonna pack blob mismatch")
    server_pack = HARNESS / "run-server/world/datapacks/Mawonna.zip"
    server_pack.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(pack, server_pack)
    manifest.append({"label": "Separate, pinned Mawonna datapack/resourcepack", "commit": PACK_COMMIT,
                     "git_blob_sha1": PACK_BLOB, "sha256": hashlib.sha256(content).hexdigest()})

    # Copy the already-accepted EULA from the previous, user-approved runtime setup.
    accepted = download(PACK_BASE + "/source/runtime/eula.txt", HARNESS / "run-server/eula.txt")
    if not re.search(r"^\s*eula\s*=\s*true\s*$", accepted.decode("utf-8-sig"), re.I | re.M):
        raise AssertionError("The pinned runtime EULA is not already accepted")

    source = ROOT / "unyielding-quests/src/main/java/dev/unyielding/quests/devtest"
    target = HARNESS / "src/main/java/dev/unyielding/quests/devtest"
    target.mkdir(parents=True, exist_ok=True)
    for name in ("AutoTest.java", "TutorialSmoke.java", "AdminSettingsSmoke.java", "AdminSettingsServerSmoke.java"):
        text = (source / name).read_text(encoding="utf-8")
        if name == "AdminSettingsServerSmoke.java":
            annotation = "@EventBusSubscriber(modid = UnyieldingQuests.MODID)"
            if annotation not in text:
                raise AssertionError("Server smoke subscriber changed; review the separate test-mod binding")
            text = text.replace(annotation, '@EventBusSubscriber(modid = "uq_bundle_smoke")', 1)
        (target / name).write_text(text, encoding="utf-8")
    (EVIDENCE / "runtime-inputs.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as output:
        output.write(f"UQ_BUNDLE_JAR={final_jar.resolve()}\nUQ_BUNDLE_SHA256={sha256}\n")
    print("Prepared separate final-JAR harness; release SHA256:", sha256)


if __name__ == "__main__":
    main()
