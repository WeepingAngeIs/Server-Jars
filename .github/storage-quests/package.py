"""Assemble on the tested Unyielding binary, preserving all unrelated payloads."""
from copy import copy
import hashlib
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "dist/storage-quests"
BASE_HASH = "9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904"
NAME = "Unyielding-1.2.0.jar"
BASE = DIST / "old" / NAME
NEW = DIST / "new" / NAME
RECEIPTS = DIST / "receipts"


def digest(file):
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    assert digest(BASE) == BASE_HASH
    module, = (ROOT / "unyielding-quests/build/libs").glob("UnyieldingQuests-*.jar")
    package = "dev/unyielding/quests/quest/"
    manager = package + "QuestManager.class"
    added_class = package + "StoragePlusChapter.class"
    folders = ("optionalquests/anyastorage/", "data/unyieldingquests/advancement/anya_storage_plus/")
    NEW.parent.mkdir(parents=True)
    RECEIPTS.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(module) as build, zipfile.ZipFile(BASE) as old, zipfile.ZipFile(NEW, "w") as out:
        resources = sorted(n for n in build.namelist() if n.startswith(folders) and not n.endswith("/"))
        assert len(resources) == 6
        additions = [added_class, *resources]
        assert not set(additions).intersection(old.namelist())
        assert old.read(manager) != build.read(manager)
        for info in old.infolist():
            data = build.read(manager) if info.filename == manager else old.read(info)
            out.writestr(copy(info), data)
        for name in additions:
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, build.read(name))

    with zipfile.ZipFile(NEW) as result, zipfile.ZipFile(BASE) as old:
        assert result.testzip() is None and len(result.namelist()) == len(set(result.namelist()))
        assert set(result.namelist()) == set(old.namelist()) | set(additions)
        assert [n for n in old.namelist() if old.read(n) != result.read(n)] == [manager]
    (RECEIPTS / "SHA256SUMS.txt").write_text(f"{digest(NEW)}  {NAME}\n")
    # Return a small binary delta; the hosted check does not distribute the complete mod.
    with (RECEIPTS / "quest-update.patch").open("wb") as patch:
        rc = subprocess.run(["git", "diff", "--no-index", "--binary", "--no-renames",
                             "old/" + NAME, "new/" + NAME], cwd=DIST, stdout=patch).returncode
    assert rc == 1 and (RECEIPTS / "quest-update.patch").stat().st_size < 500_000


if __name__ == "__main__":
    main()
