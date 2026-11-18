"""Run the existing two-client setup with the actual 21.1.249 kernel and extra custody probes."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
subprocess.run(["python3", ".github/detention/center.py"], cwd=root, check=True)
project = root/"dist/packaged-harness"
src = project/"src/main/java/dev/unyielding/bundlesmoke"

def edit(path, before, after):
    data = path.read_text()
    if data.count(before) != 1:
        raise RuntimeError(f"Unexpected fixture input {path.name}: {before!r}")
    path.write_text(data.replace(before, after))

edit(project/"build.gradle", "    version = '21.1.255'", "    version = '21.1.249'")
shutil.copy2(Path(__file__).with_name("Custody249Proof.java"), src)
script = src/"PlayerOnlySmoke.java"
edit(script, "        CenterBuildProof.place(dev,visitor);",
     "        Custody249Proof.runtime();\n        CenterBuildProof.place(dev,visitor);")
edit(script, '        require(cmd("jail Visitor 6")==1, "Explicit cell admission failed");',
     '        Custody249Proof.refusals(visitor);\n        require(cmd("jail Visitor 6")==1, "Explicit cell admission failed");')
edit(script, "        release(visitor);", """        Custody249Proof.runtime();
        focus(visitor);
        PackedWalletProof.occupiedDestination(visitor);
        flag("occupied_return_kept_conflict_and_escrow");
        release(visitor);""")
