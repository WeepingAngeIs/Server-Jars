"""Compile the one parser compatibility fix on a hosted runner, never locally."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "currency-bridge/patch"
SOURCE = PATCH / "src/com/uwustudio/cobblecurrency/CobbleDollarsValueParser.java"
CLASS_NAME = "com/uwustudio/cobblecurrency/CobbleDollarsValueParser.class"
BRIDGE = ROOT / "currency-bridge/inputs/Cobble-Currency-Bridge-1.0.0.jar"
BRIDGE_SHA256 = "07ecc2628ddfe851145e4579e57a0992209431884fa856dd2b29ebf277905b76"
DEPENDENCIES = [
    {
        "name": "lightmanscurrency-1.21-2.3.0.5.jar",
        "url": "https://cdn.modrinth.com/data/wvMc8AJt/versions/n2ynBeOK/lightmanscurrency-1.21-2.3.0.5.jar",
        "size": 8073046,
        "hash_type": "sha512",
        "hash": "f9eaf1020fe77c36912f7eec1d7e6500d7465e6bdb829e737c4bdb8af6a1213d83771a9e84392d210e675c8e74294fa7f8c1f4f78386f2966896b6891019c451",
        "provenance": "https://api.modrinth.com/v2/version/n2ynBeOK",
    },
    {
        "name": "brigadier-1.3.10.jar",
        "url": "https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar",
        "size": 80082,
        "hash_type": "sha1",
        "hash": "d15b53a14cf20fdcaa98f731af5dda654452c010",
        "provenance": "Minecraft 1.21.1 version manifest, libraries com.mojang:brigadier:1.3.10",
    },
]


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("Compilation runs only on GitHub-hosted runners.")
    if hashlib.sha256(BRIDGE.read_bytes()).hexdigest() != BRIDGE_SHA256:
        raise SystemExit("Finished bridge input differs from the supplied JAR")
    output = PATCH / "build"
    if output.exists():
        raise SystemExit("Patch output already exists; use a clean hosted checkout")
    output.mkdir(parents=True)
    libraries = PATCH / "libs"
    libraries.mkdir(parents=True, exist_ok=True)
    report_dir = ROOT / "dist/currency-smoke"
    report_dir.mkdir(parents=True, exist_ok=True)
    classpath = [BRIDGE]
    verified = []
    for dependency in DEPENDENCIES:
        request = urllib.request.Request(dependency["url"], headers={"User-Agent": "Unyielding-currency-cloud-checks/1.1.1"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if len(data) != dependency["size"] or hashlib.new(dependency["hash_type"], data).hexdigest() != dependency["hash"]:
            raise SystemExit("Dependency verification failed: " + dependency["name"])
        path = libraries / dependency["name"]
        path.write_bytes(data)
        classpath.append(path)
        verified.append({**dependency, "sha256": hashlib.sha256(data).hexdigest()})
    command = ["javac", "-J-Xmx256m", "--release", "21", "-proc:none", "-implicit:none",
               "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(output), str(SOURCE)]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    (report_dir / "patch-compile.log").write_text(result.stdout)
    print(result.stdout)
    if result.returncode:
        raise SystemExit(result.returncode)
    classes = sorted(p.relative_to(output).as_posix() for p in output.rglob("*.class"))
    if classes != [CLASS_NAME]:
        raise SystemExit("Patch compilation produced unexpected classes: " + repr(classes))
    report = {
        "status": "passed",
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "input_bridge_sha256": BRIDGE_SHA256,
        "class": CLASS_NAME,
        "class_sha256": hashlib.sha256((output / CLASS_NAME).read_bytes()).hexdigest(),
        "compiler": subprocess.check_output(["javac", "-version"], text=True).strip(),
        "target_java_release": 21,
        "dependencies": verified,
        "external_dependencies_bundled": False,
    }
    (report_dir / "patch-compile.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"status": "passed", "compiled_classes": classes}, indent=2))


if __name__ == "__main__":
    main()
