"""Fetch the three compile-only JARs, verifying their published checksums."""
import hashlib
from pathlib import Path
import urllib.request


DEPENDENCIES = [
    (
        "cobblemon.jar",
        "https://cdn.modrinth.com/data/MdwFAVRL/versions/xsVHrAsY/Cobblemon-neoforge-1.8.4%2B1.21.1.jar",
        "sha512",
        "060579fb8ac67ddd131c9cc9076ef512cda9e66e7a584c7c82326be9ef0cbb69ce6c99ff772aa78f990dc1b771bac02f3bbba68a9c7ff68c557009c64f733d04",
    ),
    (
        "emi.jar",
        "https://cdn.modrinth.com/data/fRiHVvU7/versions/5sIPA1To/emi-1.1.24%2B1.21.1%2Bneoforge.jar",
        "sha512",
        "f4c45ed686fe617311d6debadcb6ace5963e66f8cfe0cfed398231f081f87407070efc7a73c8b695d158f1d87a97180737b9107a46771e77df2bc9a7dc715ebd",
    ),
    (
        "kotlin-stdlib.jar",
        "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.4.0/kotlin-stdlib-2.4.0.jar",
        "sha1",
        "4e9f4b531dce5c7dee58ed2de9a8b9a763c8dc6c",
    ),
]

destination = Path("unyielding-quests/libs")
destination.mkdir(parents=True, exist_ok=True)
for name, url, algorithm, expected in DEPENDENCIES:
    target = destination / name
    request = urllib.request.Request(url, headers={"User-Agent": "UnyieldingQuests-Cloud-Build/1.0"})
    digest = hashlib.new(algorithm)
    with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != expected:
        target.unlink()
        raise SystemExit(f"Published checksum mismatch for {name}")
    print(f"Verified {name} ({target.stat().st_size:,} bytes)")
