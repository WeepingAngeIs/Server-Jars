"""Unchanged final JARs plus independent disposable test-mod interception only."""
import hashlib,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
subprocess.run(['python3','.github/scripts/prepare-split-server.py'],cwd=R,check=True)
h=R/'dist/packaged-harness';dst=h/'src/main/java'
p=dst/'dev/unyielding/bundlesmoke/IntegratedJailSmoke.java';s=p.read_text();needle='    @SubscribeEvent public static void tick(ServerTickEvent.Post event){';assert s.count(needle)==1;p.write_text(s.replace(needle,needle+'\n        if(Boolean.getBoolean("uq.partyLiveFence"))return;',1))
for base in [R/'minecraft-jail/src/partySafety/java',R/'minecraft-jail/src/partySafetyNative/java',R/'.github/party-live/java']:
 for p in base.rglob('*.java'):
  target=dst/p.relative_to(base);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
subprocess.run(['python3','.github/party-live/generate.py'],cwd=R,check=True)
# Capture and assert the actual pinned native temporary and backup filename recipes.
jar=next(p for p in (h/'libs').glob('*.jar') if p.name.startswith('Cobblemon-'))
layout=subprocess.check_output(['javap','-p','-c','-v','-classpath',str(jar),'com.cobblemon.mod.common.api.storage.adapter.flatfile.OneToOneFileStoreAdapter'],text=True)
(R/'dist/packaged-smoke/native-file-layout.javap').write_text(layout)
assert r'\u0001.temp' in layout and r'\u0001.old' in layout
print('PINNED NATIVE FILE RECIPES\n'+layout[layout.index('BootstrapMethods:'):])
meta=h/'src/main/resources/META-INF/neoforge.mods.toml'
assert meta.exists();meta.write_text(meta.read_text()+'\n[[mixins]]\nconfig="party-live.mixins.json"\n')
for name,digest in [('Minecraft-Jail-1.2.0.jar','5c2653a9c14d3199581fdd67e05d055b334ec2746fe7047e3c80454eb732474e'),('Unyielding-1.2.0.jar','61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7')]:
 assert hashlib.sha256((h/'libs'/name).read_bytes()).hexdigest()==digest

subprocess.run(['python3','.github/party-live/prepare_driver.py'],cwd=R,check=True)
