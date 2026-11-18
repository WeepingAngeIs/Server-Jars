"""Fetch the three compile-only JARs, verifying their published checksums."""
import hashlib
import sys
import time
from pathlib import Path
import urllib.error
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

destination = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("unyielding-quests/libs")
destination.mkdir(parents=True, exist_ok=True)
for name, url, algorithm, expected in DEPENDENCIES:
    target = destination / name
    urls = [url]
    if name == "kotlin-stdlib.jar":
        urls.append(url.replace("repo.maven.apache.org/maven2", "maven-central.storage-download.googleapis.com/maven2"))
    partial = target.with_suffix(".part")
    for attempt in range(5):
        request = urllib.request.Request(urls[attempt % len(urls)], headers={"User-Agent": "UnyieldingQuests-Cloud-Build/1.0"})
        digest = hashlib.new(algorithm)
        try:
            with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise SystemExit(f"Published checksum mismatch for {name}")
            partial.replace(target)
            break
        except (urllib.error.URLError, TimeoutError) as error:
            if isinstance(error, urllib.error.HTTPError) and error.code not in (408, 429, 500, 502, 503, 504):
                raise
            if attempt == 4:
                raise
            time.sleep(2 ** attempt)
        finally:
            partial.unlink(missing_ok=True)
    print(f"Verified {name} ({target.stat().st_size:,} bytes)")
