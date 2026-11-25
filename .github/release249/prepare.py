"""Compile only the disposable focused release observer around the sealed candidate."""
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
source = (root / ".github/native249/full_prepare.py").read_text()
marker = 'subprocess.run(["sh",str(root/"unyielding-quests/gradlew"),"--no-daemon","--console=plain","jar"],'
assert source.count(marker) == 1
insert = r'''
import hashlib
storage=Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
assert hashlib.sha256(storage.read_bytes()).hexdigest()=="afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
shutil.copy2(storage,project/"libs/combined-storage.jar")
with (project/"build.gradle").open("a") as gradle:
    gradle.write("\ndependencies { compileOnly files('libs/combined-storage.jar') }\n")
shutil.copy2(root/".github/native249/CombinedStorageProof.java",src)
for name in ("PolicySettingsProof.java","Policy249Writes.java","proof/PolicyInventoryObserver.java","proof/PolicyCustodyPortObserver.java"):
    shutil.copy2(root/".github/policy249/java"/name,src/name)
shutil.copy2(root/".github/release249/java/Release249Proof.java",src)
mixins=json.loads(observer_config.read_text())
mixins["mixins"].extend(["PolicyInventoryObserver","PolicyCustodyPortObserver"])
observer_config.write_text(json.dumps(mixins,indent=2))
replace(tick,"FullPackSession.begin(visitor)","Release249Proof.begin(visitor)")
replace(tick,"FullPackSession.resumed(visitor)","Release249Proof.resumed(visitor)")
replace(tick,"                FullPackSession.traceEscape(visitor);","                Release249Proof.observe(visitor);")
replace(tick,"                afterRespawn(dev,visitor);","                Release249Proof.tick(visitor);")
'''
source = source.replace(marker, insert + marker)
exec(compile(source, str(root / ".github/native249/full_prepare.py"), "exec"),
     {"__file__": str(root / ".github/native249/full_prepare.py")})
