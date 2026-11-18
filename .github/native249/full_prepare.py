"""Build only a disposable test JAR for loading in two separate production game installations."""
from pathlib import Path
import json
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[2]
subprocess.run(["python3", ".github/native249/prepare.py"], cwd=root, check=True)
project = root/"dist/packaged-harness"
src = project/"src/main/java/dev/unyielding/bundlesmoke"

def replace(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Fixture drift in {path.name}: {old}")
    path.write_text(text.replace(old, new))

for name in ("FullPackSession.java", "FullPackClientWatch.java", "FullPackDiagnostics.java"):
    shutil.copy2(Path(__file__).with_name(name),src)
shutil.copy2(Path(__file__).with_name("NeutralSerializationObserver.java"),src/"proof")
observer_config=project/"src/main/resources/jail-production-observers.mixins.json"
mixins=json.loads(observer_config.read_text())
mixins["mixins"].append("NeutralSerializationObserver")
observer_config.write_text(json.dumps(mixins,indent=2))
tick = src/"PlayerOnlySmoke.java"
replace(tick, 'dev = server.getPlayerList().getPlayerByName("Dev")',
              'dev = server.getPlayerList().getPlayerByName("Visitor")')
replace(tick, 'begin(dev,visitor); else if (phase==2) resumed(dev,visitor);',
              'FullPackSession.begin(visitor); else if (phase==2) FullPackSession.resumed(visitor);')
replace(tick,"                require(server.getTickCount()<=deadline",
        "                FullPackSession.traceEscape(visitor);\n                require(server.getTickCount()<=deadline")
replace(tick,"                started=true;","""                CertifiedJailSmoke.world=server.getWorldPath(LevelResource.ROOT);
                if (Integer.getInteger("uq.playerOnlyPhase")==1 && !FullPackSession.prepared(visitor)) return;
                started=true;""")
replace(tick, '        cell(dev,1);\n        cell(visitor,6);\n        flag("escape_native',
              '        FullPackSession.snapshot(visitor,"respawned");\n        cell(visitor,6);\n        flag("escape_native')
replace(src/"CenterBuildProof.java", "        visitor.teleportTo(level,", "        if (visitor != admin) visitor.teleportTo(level,")
# The bounded harness extends the canonical origin layers and makes vanilla items
# valid as rings. Deploy observation/stimulus classes without any custom datapack.
data=project/"src/main/resources/data"
if data.exists():
    shutil.rmtree(data)
# A custom death UI can inherit Screen directly. Demand a fully unblocked gameplay view
# for 20 consecutive client ticks, including overlays and pause state, after the native replacement.
client = src/"PlayerOnlyClient.java"
replace(client, "static int killPackets, respawnPackets;", "static int killPackets, respawnPackets, clearTicks;")
replace(client, "killPackets++; dead=true; deadObject=p;", "killPackets++; dead=true; deadObject=p; clearTicks=0;")
replace(client, "if(!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)&&observed.add(p)){",
        """boolean playable = mc.screen==null && mc.getOverlay()==null && !mc.isPaused();
            clearTicks = playable ? clearTicks+1 : 0;
            if(clearTicks>=20 && observed.add(p)){""")
replace(client, '"screen_cleared",true,"alive",true', '"screen_cleared",true,"no_modal_ticks",clearTicks,"alive",true')
subprocess.run(["sh",str(root/"unyielding-quests/gradlew"),"--no-daemon","--console=plain","jar"],
               cwd=project,check=True)
jars=list((project/"build/libs").glob("*.jar"))
assert len(jars)==1, jars
with zipfile.ZipFile(jars[0]) as stimulus:
    assert not any(name.startswith("data/") for name in stimulus.namelist()), "Fixture JAR changes pack data"
shutil.copy2(jars[0],root/"dist/packaged-smoke/fullpack-stimulus.jar")
