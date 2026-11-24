"""Run one exact companion variant with the real full-pack client and server."""
import hashlib
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
companion = Path(os.environ["UQ_SWAP_JAR"]).resolve()
variant = os.environ["UQ_SPAWN_VARIANT"]
expected = {"baseline":"e4f34f6a4640fa6c59fc8efe3d2253d0b701bd83371fd960c5d38cfac73ccf2c",
            "spawn":"9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904"}[variant]
assert hashlib.sha256(storage.read_bytes()).hexdigest() == "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
assert hashlib.sha256(companion.read_bytes()).hexdigest() == expected
source = (root / ".github/native249/full_run.py").read_text()

def replace(old, new):
    global source
    assert source.count(old) == 1, old
    source = source.replace(old, new)

marker = 'companion = server/"mods/Unyielding-1.2.0.jar"'
insert = '''
for game in (server,client):
    target=game/"mods/Unyielding-1.2.0.jar"
    assert digest(target)=="ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"
    target.unlink()
    shutil.copy2(swap_input,target)
    assert digest(target)==expected
    dest=game/"mods"/storage.name
    assert not dest.exists()
    shutil.copy2(storage,dest)
    assert digest(dest)=="afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
'''
replace(marker,insert+marker)
replace('assert digest(companion) == "ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"',
        'assert digest(companion) == expected')
replace('== 367, "Expected all 365 pack files plus candidate and test JAR"',
        '== 368, "Expected published client files plus candidate, test JAR and Storage"')
manifest = '''
def manifest(game, side, stage):
    rows=[]
    for sub in ("mods","config","defaultconfigs","kubejs","datapacks","resourcepacks"):
        path=game/sub
        if not path.exists(): continue
        for file in sorted(x for x in path.rglob("*") if x.is_file()):
            row={"path":file.relative_to(game).as_posix(),"size":file.stat().st_size,"sha256":digest(file)}
            if sub=="mods" and file.suffix==".jar": row["sha512"]=digest(file,"sha512")
            rows.append(row)
    for name in ("server.properties","options.txt"):
        file=game/name
        if file.is_file(): rows.append({"path":name,"size":file.stat().st_size,"sha256":digest(file)})
    (report/f"swap-{variant}-{side}-{stage}.json").write_text(json.dumps({"side":side,"stage":stage,"files":rows},indent=2))

for game,side in ((server,"server"),(client,"client")):
    manifest(game,side,"before-start")
'''
replace('for phase in phases:',manifest+'\nfor phase in phases:')
stop = '    proof=json.loads((report/f"center-phase{phase}.json").read_text())'
assert source.count(stop) == 1
source = source.split(stop,1)[0] + '''
    for game,side in ((server,"server"),(client,"client")):
        manifest(game,side,"after-stop")
    proof=json.loads((report/f"swap-preflight-{variant}.json").read_text())
    assert proof["commit"]==os.environ["GITHUB_SHA"] and proof["jail_sha256"]==digest(candidate)
    assert proof["companion_sha256"]==expected and proof["real_connected_player"]
    assert proof["actual_preflight_command_refused"] and proof["actual_admission_command_refused"]
    assert proof["inventory_and_custody_unchanged"]
    runtime=json.loads((report/f"runtime249-phase{phase}.json").read_text())
    assert runtime["neoforge"]["version"]=="21.1.249"
    assert runtime["anyastorage"]["sha512"]==digest(storage,"sha512")
    assert any(row["sha512"]==digest(companion,"sha512") for row in runtime.values())
    print("Unchanged Jail refused the unapproved "+variant+" roster without moving inventory.",flush=True)
'''
exec(compile(source,str(root/".github/native249/full_run.py"),"exec"),
     {"__file__":str(root/".github/native249/full_run.py"),"storage":storage,
      "swap_input":companion,"variant":variant,"expected":expected})
