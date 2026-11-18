"""Build only a disposable test JAR for loading in two separate production game installations."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
subprocess.run(["python3", ".github/native249/prepare.py"], cwd=root, check=True)
project = root/"dist/packaged-harness"
src = project/"src/main/java/dev/unyielding/bundlesmoke"

def replace(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Fixture drift in {path.name}: {old}")
    path.write_text(text.replace(old, new))

for name in ("FullPackSession.java", "FullPackClientWatch.java"):
    shutil.copy2(Path(__file__).with_name(name),src)
tick = src/"PlayerOnlySmoke.java"
replace(tick, 'dev = server.getPlayerList().getPlayerByName("Dev")',
              'dev = server.getPlayerList().getPlayerByName("Visitor")')
replace(tick, 'begin(dev,visitor); else if (phase==2) resumed(dev,visitor);',
              'FullPackSession.begin(visitor); else if (phase==2) FullPackSession.resumed(visitor);')
replace(tick, '        cell(dev,1);\n        cell(visitor,6);\n        flag("escape_native',
              '        FullPackSession.snapshot(visitor,"respawned");\n        cell(visitor,6);\n        flag("escape_native')
replace(src/"CenterBuildProof.java", "        visitor.teleportTo(level,", "        if (visitor != admin) visitor.teleportTo(level,")
# Record seeded native state even when its actual production provider preflight refuses admission.
replace(src/"CertifiedJailSmoke.java", "UUID probe=UUID.randomUUID();items(probe,null).validateRemoval();",
        'FullPackSession.snapshot(target,"seeded-before-preflight");UUID probe=UUID.randomUUID();items(probe,null).validateRemoval();')
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
shutil.copy2(jars[0],root/"dist/packaged-smoke/fullpack-stimulus.jar")
