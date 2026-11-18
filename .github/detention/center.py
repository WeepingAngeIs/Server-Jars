"""Prepare a fresh two-client center scenario against the packaged production mod."""
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
subprocess.run(["python3", ".github/jail-production/prepare.py"], cwd=root, check=True)
src = root / "dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke"

# The old single-player stimulus configured unique classes. Seed both users under
# ordinary nonunique class rules before admission, keeping all production audits.
fixture = src / "CertifiedJailSmoke.java"
text = fixture.read_text()
unique = 'set(List.of(main.toString(),classes.toString()))'
assert text.count(unique) == 1
text = text.replace(unique, "set(List.of())")
claim = 'for(var choice:Map.of(main,human,classes,warrior).entrySet())'
assert text.count(claim) == 1
text = "\n".join(line for line in text.split("\n") if claim not in line)
release = 'require(command("unjail Visitor")==0&&Arrays.equals(before,items(last,null).current().inventory())'
assert text.count(release) == 1
text = text.replace(release, 'require(command("unjail "+target.getGameProfile().getName())==0&&Arrays.equals(before,items(last,null).current().inventory())')
fixture.write_text(text)
for name in ("PlayerOnlySmoke.java", "CenterBuildProof.java"):
    shutil.copy2(Path(__file__).with_name(name), src / name)
report = root / "dist/packaged-smoke/center-harness-sha256.json"
report.write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in (src/"PlayerOnlySmoke.java", src/"CenterBuildProof.java", fixture)}, indent=2))
