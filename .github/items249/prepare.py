"""Exact stock content providers in the tested bounded NeoForge two-client fixture."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile

root = Path(__file__).resolve().parents[2]
subprocess.run(["python3", ".github/native249/prepare.py"], cwd=root, check=True)
project = root/"dist/packaged-harness"
java = project/"src/main/java/dev/unyielding/bundlesmoke"
lib = importlib.util.spec_from_file_location("pack249", root/".github/pack249/provision.py")
pack = importlib.util.module_from_spec(lib)
lib.loader.exec_module(pack)
cache = Path(os.environ["RUNNER_TEMP"])/"items249-published"
cache.mkdir(exist_ok=True)
providers = {"sophisticatedbackpacks-1.21.1-3.26.21.2249.jar","sophisticatedcore-1.21.1-1.5.35.2473.jar",
             "Spice of Life Onion_NEOFORGE_v1.5.6_mc1.21.1.jar","CreativeCore_NEOFORGE_v2.13.49_mc1.21.1.jar"}
selected = [row for row in pack.PACK["mods"] if row["name"] in providers]
assert len(selected)==len(providers)
for row in selected:
    shutil.copyfile(pack.retrieve(row,cache),project/"libs"/row["name"])
overrides=json.loads((root/".github/pack249/overrides.json").read_text())
food_settings="config/solonion.json"
food_pack="datapacks/SOL_Onion_64_Food_Heart_Cycles_Datapack_v1.0.zip"
with ZipFile(pack.retrieve(pack.PACK["pack"],cache)) as release:
    for resource in (food_settings,food_pack):
        body=release.read("overrides/"+resource)
        assert hashlib.sha256(body).hexdigest()==overrides[resource]
        targets=([project/game/resource for game in ("run","run-visitor","run-server")] if resource==food_settings
                 else [project/"run-server/jail_certified_smoke"/resource])
        for target in targets:
            target.parent.mkdir(exist_ok=True,parents=True)
            target.write_bytes(body)
report={"providers":{r["name"]:r["sha512"] for r in selected},"food_config":overrides[food_settings],"datapack":overrides[food_pack]}
(root/"dist/packaged-smoke/items249-inputs.json").write_text(json.dumps(report,indent=2))
for file in Path(__file__).parent.glob("*.java"):
    shutil.copyfile(file,java/file.name)

def replace(path,old,new):
    source=path.read_text()
    if source.count(old)!=1:
        raise ValueError(f"Fixture input drift in {path.name}: {old}")
    path.write_text(source.replace(old,new))

scenario=java/"PlayerOnlySmoke.java"
replace(scenario,"if (wallet) PackedWalletProof.seed(p);",
        "if (wallet) { PackedWalletProof.seed(p); Items249Proof.seed(p); }")
replace(scenario,"        remember(visitor);","        remember(visitor);\n        Items249Proof.inspectLocker(dev,visitor);")
replace(scenario,'        flag("escape_native_respawn_in_cell_six_without_client_action");',
        '        Items249Proof.held(visitor,"respawn");\n        flag("escape_native_respawn_in_cell_six_without_client_action");')
replace(scenario,"        release(visitor);",
        '        Items249Proof.held(visitor,"reconnect");\n        release(visitor);\n        Items249Proof.returned(visitor);')
replace(scenario,"        Custody249Proof.refusals(visitor);","        Custody249Proof.refusals(visitor);\n        Items249Proof.duplicateRefused(visitor);")
replace(scenario,'        flag("released_cell_reused_by_other_player_and_closed");\n        pass();',
        '        flag("released_cell_reused_by_other_player_and_closed");\n        deadline=dev.server.getTickCount()+200;\n        Items249Food.normalDeath(dev);')
replace(scenario,"                afterRespawn(dev,visitor);","""                if (phase==2) {
                    if (Items249Food.ordinaryComplete(dev)) pass();
                } else afterRespawn(dev,visitor);""")
# Warrior's two HP change remains required. Earned SOL hearts stay with their owner.
checks=java/"CertifiedJailSmoke.java"
replace(checks,"getValue()==20,","getValue()==Items249Food.expectedHealth(target,20),")
replace(checks,"getValue()==22&&JailData.get","getValue()==Items249Food.expectedHealth(target,22)&&JailData.get")
replace(checks,"returned(22);","returned(Items249Food.expectedHealth(target,22));")
