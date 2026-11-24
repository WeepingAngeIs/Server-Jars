"""Run the combined custody fixture with the inspected companion from startup."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
source=(root/".github/native249/combined_run.py").read_text()
marker="source = source.replace('== 367, \"Expected all 365 pack files plus candidate and test JAR\"'"
assert source.count(marker)==1
extra=r'''
swap_input=Path(os.environ["UQ_SWAP_JAR"]).resolve()
expected="9dbfb659a2ba2c995d0f3974fa48cf9f37b9b5f8162c3e2e4faf2af9946e0904"
assert hashlib.sha256(swap_input.read_bytes()).hexdigest()==expected
needle='companion = server/"mods/Unyielding-1.2.0.jar"'
assert source.count(needle)==1
injection="""
for game in (server,client):
    target=game/"mods/Unyielding-1.2.0.jar"
    if digest(target)=="ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c":
        target.unlink()
        shutil.copy2(swap_input,target)
    assert digest(target)==expected
"""
source=source.replace(needle,injection+needle)
old='assert digest(companion) == "ec3ae2c48399e40ffa615ddec85408bc29b2479c5a35eb7236c3e036ebdf762c"'
assert source.count(old)==1
source=source.replace(old,'assert digest(companion) == expected')
before='for phase in phases:'
assert source.count(before)==1
capture="""
sys.path.insert(0,str(root/".github/swap249"))
from manifest import capture
for game,side in ((server,"server"),(client,"client")):
    capture(report,game,side,f"phase{phases[0]}-before-start")
"""
source=source.replace(before,capture+"\n"+before)
after='    proof=json.loads((report/f"center-phase{phase}.json").read_text())'
assert source.count(after)==1
source=source.replace(after,'    for game,side in ((server,"server"),(client,"client")): capture(report,game,side,f"phase{phase}-after-stop")\n'+after)
# Keep native crash reports as evidence even when the observer itself fails.
closing='            stop_group(srv)\n            for game,name in ((client,"client"),(server,"server")):'
assert source.count(closing)==1
source=source.replace(closing,'            stop_group(srv)\n            for crash in client.glob("hs_err_pid*.log"):\n                shutil.copy2(crash,logs/crash.name)\n            for game,name in ((client,"client"),(server,"server")):')
'''
source=source.replace(marker,extra+'\n'+marker)
checks='    if phase == 1:\n        assert proof["active_storage_lease_refused_without_change"]'
assert source.count(checks)==1
source=source.replace(checks,'    if phase == 1:\n        assert proof["teleport_requests_refused_or_preserved_as_appropriate"]\n        assert proof["held_teleport_requests_checked_without_mutation"]\n        assert proof["active_storage_lease_refused_without_change"]')
source=source.replace('{"__file__": str(root/".github/native249/full_run.py"), "storage": storage}',
                      '{"__file__": str(root/".github/native249/full_run.py"), "storage": storage, "swap_input": swap_input, "expected": expected}')
exec(compile(source,str(root/".github/native249/combined_run.py"),"exec"),
     {"__file__":str(root/".github/native249/combined_run.py")})
