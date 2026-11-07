"""Check the distributable JAR and its embedded resources after the cloud build."""
import hashlib
import json
from pathlib import Path
import re
import zipfile


project = Path("unyielding-quests")
properties = (project / "gradle.properties").read_text(encoding="utf-8")
version = re.search(r"^mod_version=(.+)$", properties, re.MULTILINE).group(1).strip()
jar = project / "build/libs" / f"UnyieldingQuests-{version}.jar"
report_dir = project / "build/verification"
report_dir.mkdir(parents=True, exist_ok=True)
resources = project / "src/main/resources"
with zipfile.ZipFile(jar) as archive:
    names = set(archive.namelist())
    assert archive.testzip() is None, "Corrupt ZIP entry"
    assert not any("/devtest/" in name for name in names), "Development test helper leaked into release"
    manifest = archive.read("META-INF/MANIFEST.MF").decode()
    assert f"Implementation-Version: {version}" in manifest, "Wrong built version"
    for name in (
        "dev/unyielding/quests/tutorial/TutorialManager.class",
        "dev/unyielding/quests/client/tutorial/TutorialClient.class",
        "dev/unyielding/quests/client/tutorial/TutorialScreen.class",
        "dev/unyielding/quests/settings/AdminSettings.class",
        "dev/unyielding/quests/client/AdminSettingsScreen.class",
        "dev/unyielding/quests/quest/ProgressSharing.class",
        "dev/unyielding/quests/net/Net$AdminSettingsAction.class",
        "dev/unyielding/quests/net/Net$AdminSettingsSync.class",
    ):
        assert name in names, f"Missing compiled tutorial entry {name}"
    count = 0
    for source in resources.rglob("*"):
        if source.is_file():
            relative = source.relative_to(resources).as_posix()
            assert archive.read(relative) == source.read_bytes(), f"Resource changed in package: {relative}"
            count += 1
    art_count = sum(name.startswith("assets/unyieldingquests/textures/quest_art/") and name.endswith(".png") for name in names)
    assert art_count > 900, "Quest artwork missing from package"
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
report = {
    "status": "passed",
    "version": version,
    "jar": jar.name,
    "sha256": digest,
    "verified_resources": count,
    "quest_pictures": art_count,
    "development_helpers_in_release": False,
}
(report_dir / "package-verification.json").write_text(json.dumps(report, indent=2) + "\n")
(report_dir / "SHA256SUMS.txt").write_text(f"{digest}  {jar.name}\n")
print(json.dumps(report, indent=2))
