"""Compare two completed hosted observations without modifying either result."""
import json
from pathlib import Path

root=Path("swap-results")
def load(variant,name):
    files=list((root/variant).rglob(name))
    assert len(files)==1,(variant,name,files)
    return json.loads(files[0].read_text())

baseline=load("baseline","swap-preflight-baseline.json")
spawn=load("spawn","swap-preflight-spawn.json")
assert baseline["jail_sha256"]==spawn["jail_sha256"]=="8e4e710979d82fc3e12c1931fea3f718173e1efb52bc6e3aa86a02b65ef8aaea"
assert baseline["companion_sha256"]=="e4f34f6a4640fa6c59fc8efe3d2253d0b701bd83371fd960c5d38cfac73ccf2c"
assert spawn["companion_sha256"]=="9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904"
for row in (baseline,spawn):
    assert row["real_connected_player"] and row["actual_preflight_command_refused"]
    assert row["actual_admission_command_refused"] and row["inventory_and_custody_unchanged"]
    assert row["spawn_counts"]["single"]["new_distinct_entities"]==1
for key,expected in (("count_five",5),("alias_five",5),("level_three",3)):
    assert spawn["spawn_counts"][key]["new_distinct_entities"]==expected

def normalized_files(variant,side):
    doc=load(variant,f"swap-{variant}-{side}-before-start.json")
    return {row["path"]:row["sha256"] for row in doc["files"]
            if row["path"] not in {"mods/Unyielding-1.2.0.jar","mods/fullpack-stimulus.jar"}}
for side in ("server","client"):
    a,b=normalized_files("baseline",side),normalized_files("spawn",side)
    assert a==b,(side,"Other startup inputs differed",sorted(set(a)^set(b))[:20],
                 [key for key in a.keys()&b.keys() if a[key]!=b[key]][:20])

out={"fixture":"published Cobblemon Unyielding 1.0.14 + exact roommedia.1 + exact Jail beta 4 test 1; one companion swapped",
     "minecraft":"1.21.1","neoforge":"21.1.249","jail_sha256":spawn["jail_sha256"],
     "baseline_sha256":baseline["companion_sha256"],"spawn_sha256":spawn["companion_sha256"],
     "storage_sha256":"afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e",
     "other_startup_inputs_identical":True,
     "baseline_roster_refusal":baseline["roster_refusal"],"spawn_roster_refusal":spawn["roster_refusal"],
     "baseline_counts":baseline["spawn_counts"],"spawn_counts":spawn["spawn_counts"]}
Path("swap-comparison.json").write_text(json.dumps(out,indent=2)+"\n")
print(json.dumps(out,indent=2))
