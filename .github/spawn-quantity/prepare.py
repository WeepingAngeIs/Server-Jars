"""Overlay only the saved patch on the exact combined baseline."""
from copy import copy
import hashlib
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parents[2]
baseline = root / "ci-inputs/Unyielding-1.2.0-configtopia-spawn-baseline.jar"
expected = "e4f34f6a4640fa6c59fc8efe3d2253d0b701bd83371fd960c5d38cfac73ccf2c"
assert hashlib.sha256(baseline.read_bytes()).hexdigest() == expected
module, = (root / "unyielding-quests/build/libs").glob("UnyieldingQuests-*.jar")
target = root / "dist/120-release/Unyielding-1.2.0.jar"
target.parent.mkdir(parents=True)
mixin = "dev/unyielding/quests/mixin/PokemonSpawnQuantityMixin.class"
config = "assets/unyieldingquests/pokemon.mixins.json"
with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(module) as built, zipfile.ZipFile(target, "w") as new:
    original = old.read(config)
    marker = b'"mixins": ['
    assert original.count(marker) == 1
    replacement = original.replace(marker, marker + b'\n    "PokemonSpawnQuantityMixin",')
    assert replacement.split() == built.read(config).split()
    assert mixin not in old.namelist()
    for item in old.infolist():
        new.writestr(copy(item), replacement if item.filename == config else old.read(item))
    new.writestr(copy(built.getinfo(mixin)), built.read(mixin))
with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(target) as new:
    assert len(new.namelist()) == len(set(new.namelist())) and new.testzip() is None
    assert set(new.namelist()) == set(old.namelist()) | {mixin}
    assert all(old.read(n) == new.read(n) for n in old.namelist() if n != config)
evidence = root / "dist/spawn-evidence"
evidence.mkdir()
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(evidence / "SHA256SUMS.txt").write_text(f"{digest}  {target.name}\n")
setup = root / ".github/scripts/prepare-packaged-server.py"
runtime_setup = setup.read_text().split("namespace['main']()", 1)[0] + "namespace['main']()\n"
exec(compile(runtime_setup, str(setup), "exec"), {"__file__": str(setup)})
harness = root / "dist/packaged-harness"
shutil.copy2(root / ".github/spawn-quantity/BundleSmoke.java",
             harness / "src/main/java/dev/unyielding/bundlesmoke/BundleSmoke.java")
settings = """online-mode=false
server-ip=127.0.0.1
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
"""
(harness / "run-server/server.properties").write_text(settings)
