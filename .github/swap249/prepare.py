"""Build a hosted-only observer around the unchanged Jail and Storage JARs."""
import hashlib
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
storage = Path(os.environ["COMBINED_STORAGE_JAR"]).resolve()
assert hashlib.sha256(storage.read_bytes()).hexdigest() == "afd62612c2f09c6bf156831b0e798da58ef0fd1632297dc936ea69e11b66e91e"
source = (root / ".github/native249/full_prepare.py").read_text()
marker = 'subprocess.run(["sh",str(root/"unyielding-quests/gradlew"),"--no-daemon","--console=plain","jar"],'
assert source.count(marker) == 1
insert = '''
shutil.copy2(root/".github/native249/CombinedStorageProof.java", src)
shutil.copy2(root/".github/swap249/SwapPreflight.java", src)
shutil.copy2(storage, project/"libs/combined-storage.jar")
with (project/"build.gradle").open("a") as gradle:
    gradle.write("\\ndependencies { compileOnly files('libs/combined-storage.jar') }\\n")
full = src/"FullPackSession.java"
body = full.read_text()
start = body.index("    static void begin(ServerPlayer player) throws Exception {")
end = body.index("    static void resumed(ServerPlayer player) throws Exception {",start)
body = body[:start] + "    static void begin(ServerPlayer player) throws Exception { SwapPreflight.run(player); }\\n" + body[end:]
full.write_text(body)
'''
source = source.replace(marker, insert + marker)
exec(compile(source, str(root / ".github/native249/full_prepare.py"), "exec"),
     {"__file__": str(root / ".github/native249/full_prepare.py"), "storage": storage})
