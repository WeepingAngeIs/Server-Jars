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
port = "new JailEquipmentPort(target,id,retired,true,true)"
assert text.count(port) == 1
text = text.replace(port, 'new JailEquipmentPort(target,id,retired,true,target.getGameProfile().getName().equals("Visitor"))')
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
client = src / "PlayerOnlyClient.java"
text = client.read_text()
anchor = "    @SubscribeEvent public static void tick("
assert text.count(anchor) == 1
text = text.replace(anchor, """
    static int killPackets, respawnPackets;
    public static void killed(Object p) {
        killPackets++; dead=true; deadObject=p;
    }
    public static void respawned(Object p) {
        if (deadObject!=null && p!=null && p!=deadObject) respawnPackets++;
    }
""" + anchor)
text = text.replace('String text=new GsonBuilder()', 'row.put("kill_packets",killPackets);row.put("respawn_packets_after_kill",respawnPackets);String text=new GsonBuilder()')
client.write_text(text)
shutil.copy2(Path(__file__).with_name("CenterClientPackets.java"), src / "proof/CenterClientPackets.java")
config = root / "dist/packaged-harness/src/main/resources/jail-production-observers.mixins.json"
mixins = json.loads(config.read_text())
mixins["client"].append("CenterClientPackets")
config.write_text(json.dumps(mixins, indent=2))
for name in ("PlayerOnlySmoke.java", "CenterBuildProof.java"):
    shutil.copy2(Path(__file__).with_name(name), src / name)
report = root / "dist/packaged-smoke/center-harness-sha256.json"
report.write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in (src/"PlayerOnlySmoke.java", src/"CenterBuildProof.java", src/"proof/CenterClientPackets.java",
                                       fixture, client, config)}, indent=2))
