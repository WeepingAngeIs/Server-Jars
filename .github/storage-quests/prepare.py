"""Stage the public pack's quest definitions and the two pinned storage mods for a disposable game."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile

from package import BASE, DIST, NEW, RECEIPTS, ROOT, digest

sys.path.insert(0, str(ROOT / ".github/optional-controls"))
import dedicated
sys.path.insert(0, str(ROOT / "combined-unyielding"))
import prepare_smoke

HARNESS = DIST / "harness"
GAME = HARNESS / "game"
SOPHISTICATED = [
    ("9578/162", "sophisticatedcore-1.21.1-1.5.35.2473.jar", "c255cfaca1a939932f622ad3c934a0c379b371fa193b0d06ee3f81a723148e64"),
    ("9578/283", "sophisticatedstorage-1.21.1-1.6.18.2229.jar", "a263cfe0abafea3dd495cd6c7e6fd3c14b5070c35b63960d00c7e58081f4f987"),
    ("9524/812", "sophisticatedbackpacks-1.21.1-3.26.21.2249.jar", "a1b865474155922e3a9e83fc02aa32db968d43d35b2ef31b1fc50537f8536bf5"),
]
STORAGE = dict(repository="WeepingAngeIs/zSortange", run=45524420757, id=14981812608,
               commit="83d363e1c7a13993911da50a2f575bc9eec52dd6",
               zip="da6fbb25ac40ecefe7adc1d9efe48ca621e779d35a336484554ef3effbd60f3e",
               jar="9416a5549f57268ffc582ab37c1026a748c00081f49d930c605e7675a4b59929")


def main():
    if os.getenv("RUNNER_ENVIRONMENT") != "github-hosted":
        raise SystemExit("Run the disposable game on a hosted runner.")
    # Reuse the existing pinned artifact downloader, with a fresh staging directory.
    dedicated.WORK = DIST
    libs = HARNESS / "libs"
    libs.mkdir(parents=True)
    config = GAME / "config"
    config.mkdir(parents=True)
    (GAME / "options.txt").write_text("version:3955\nonboardAccessibility:false\ntutorialStep:none\n"
                                      "renderDistance:3\nsimulationDistance:5\nmaxFps:45\n"
                                      "enableVsync:false\npauseOnLostFocus:false\nguiScale:2\n")
    (config / "fml.toml").write_text("earlyWindowControl = false\n")
    (config / "unyieldingquests-common.toml").write_text('[quests]\nquest_book_source="config_folder"\n'
                                                        '[tutorial]\nshow_on_join=false\n')
    pack = DIST / "pack.zip"
    dedicated.fetch("https://mediafilez.forgecdn.net/files/9606/6/Cobblemon-Unyielding-1.0.14.zip", pack)
    with pack.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha1").hexdigest() == "35fa5b5915c22fe59c0cef86dec0f7602430b4d4"
    prefix = "overrides/config/unyieldingquests/quests/"
    with zipfile.ZipFile(pack) as archive:
        for name in archive.namelist():
            if name.startswith(prefix) and name.endswith(".snbt"):
                rel = Path(name.removeprefix(prefix))
                assert not rel.is_absolute() and ".." not in rel.parts
                file = config / "unyieldingquests/quests" / rel
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(archive.read(name))
    tree = config / "unyieldingquests/quests"
    assert len(list((tree / "chapters").glob("*.snbt"))) == 38
    assert digest(tree / "chapters/storage.snbt") == "75818c86735d5316594ebb18e6e749833178a9e2d5eaf0cafa2cd781a9ff8c2d"
    signature = hashlib.sha256()
    for file in sorted(tree.rglob("*.snbt"), key=lambda p: p.relative_to(tree).as_posix()):
        signature.update(f"{digest(file)}  {file.relative_to(tree).as_posix()}\n".encode())
    (RECEIPTS / "quest-tree.sha256").write_text(signature.hexdigest() + "\n")
    assert signature.hexdigest() == "fca17f9a1c27fd34026c0df2dd6ddef1636de55712b92b328f3b51abb2902fef"
    for slug, name, sha in SOPHISTICATED:
        target = libs / name
        dedicated.fetch("https://edge.forgecdn.net/files/" + slug + "/" + name, target)
        assert digest(target) == sha
    dedicated.checked_artifact(STORAGE, libs / "anya-storage.jar")
    versions = dict(prepare_smoke.DEPENDENCIES)
    versions.update({"CobbleDollars": "Iqiqjfsi", "Lightman's Currency": "n2ynBeOK"})
    for key in versions.values():
        info = json.loads(prepare_smoke.download(f"https://api.modrinth.com/v2/version/{key}", DIST / "version.json"))
        assert info["id"] == key
        file = next((f for f in info["files"] if f.get("primary")), info["files"][0])
        target = libs / Path(file["filename"]).name
        data = prepare_smoke.download(file["url"], target)
        assert hashlib.sha512(data).hexdigest() == file["hashes"]["sha512"]
    shutil.copy2(BASE, libs / BASE.name)
    template = ROOT / "combined-unyielding/smoke-harness"
    shutil.copy2(template / "settings.gradle", HARNESS / "settings.gradle")
    build = (template / "build.gradle").read_text()
    build = build.replace("21.1.255", "21.1.249").replace("file('run')", "file('game')")
    start = build.index("            programArguments.addAll('--quickPlayMultiplayer'")
    end = build.index("\n        }", start)
    build = build[:start] + """            programArguments.addAll('--username', 'QuestReader', '--width', '1280', '--height', '720')
            if (System.getenv('QUEST_PHASE') in ['after', 'restart']) programArguments.addAll('--quickPlaySingleplayer', 'StorageQuestCheck')
            systemProperty 'quests.receipts', file('../receipts').absolutePath""" + build[end:]
    (HARNESS / "build.gradle").write_text(build)
    src = HARNESS / "src/main"
    shutil.copytree(template / "src/main/resources", src / "resources")
    shutil.copytree(Path(__file__).parent / "java", src / "java")
    (RECEIPTS / "inputs.json").write_text(json.dumps({p.name: digest(p) for p in sorted(libs.glob("*.jar"))}, indent=2))


if __name__ == "__main__":
    main()
