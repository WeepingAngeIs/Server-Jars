"""Use only the pinned, completed wallet fixtures in a detached disposable tree."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from certify import WALLET_SOURCE, OLD_COMPANION, NEW_COMPANION, CERT

ROOT = Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted'
mode = sys.argv[1]
assert mode in ('core', 'faults', 'integrated', 'vein')
pinned = ROOT / 'dist/wallet-runtime-source'
subprocess.run(['git', 'fetch', '--no-tags', '--depth=1', 'origin', WALLET_SOURCE], cwd=ROOT, check=True)
subprocess.run(['git', 'worktree', 'add', '--detach', str(pinned), WALLET_SOURCE], cwd=ROOT, check=True)
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=pinned, text=True).strip() == WALLET_SOURCE
pair = ROOT / 'dist/joint-pair'
manifest = json.loads((pair / 'pair-source.json').read_text())
for name, digest in manifest['jars'].items():
    file = pair / name
    assert hashlib.sha256(file.read_bytes()).hexdigest() == digest
    dest = pinned / ('dist/120-release' if name.startswith('Unyielding') else 'minecraft-jail/build/libs') / name
    dest.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(file, dest)
# The existing preparation verifies the packaged certificate against its source
# copy. This disposable detached copy receives the identical certificate only.
(pinned / 'minecraft-jail/src/main/resources' / CERT).write_bytes((pair / 'joint-runtime-certificate.json').read_bytes())
verify = pinned / '.github/packed-wallet/verify.py'
text = verify.read_text(); assert text.count(OLD_COMPANION) == 1
verify.write_text(text.replace(OLD_COMPANION, NEW_COMPANION))
env = dict(os.environ); env['GITHUB_WORKSPACE'] = str(pinned)
subprocess.run(['python3', '.github/scripts/fetch-build-dependencies.py'], cwd=pinned, env=env, check=True)
script = '.github/packed-wallet/prepare.py' if mode in ('core', 'faults') else '.github/scripts/prepare-split-server.py'
subprocess.run(['python3', script], cwd=pinned, env=env, check=True)
if mode == 'vein':
    dest = ROOT / 'dist/120-release'; dest.mkdir(parents=True, exist_ok=True)
    shutil.copy2(pair / 'Unyielding-1.2.0.jar', dest)
    # Compile-only Kotlin was fetched for the pinned fixture; reuse identical bytes.
    dest = ROOT / 'unyielding-quests/libs'; dest.mkdir(parents=True, exist_ok=True)
    shutil.copy2(pinned / 'unyielding-quests/libs/kotlin-stdlib.jar', dest)
    subprocess.run(['python3', '.github/scripts/prepare-vein-protection.py'], cwd=ROOT, check=True)
    libs = ROOT / 'dist/vein-harness/libs'
    for file in (pinned / 'dist/packaged-harness/libs').glob('*.jar'):
        target = libs / file.name
        if target.exists():
            assert target.read_bytes() == file.read_bytes(), f'Conflicting runtime input: {file.name}'
        else:
            shutil.copy2(file, target)
    source = ROOT / 'dist/vein-harness/src/main/java/dev/unyielding/bundlesmoke/BundleSmoke.java'
    text = source.read_text()
    old = 'require(server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"integrated Jail returned");'
    assert text.count(old) == 1
    new = '''require(server.getCommands().getDispatcher().getRoot().getChild("jail")!=null,"standalone Jail missing");
                require(Path.of(System.getenv("UQ_JAIL_JAR")).toRealPath().equals(ModList.get().getModFileById("minecraftjail").getFile().getFilePath().toRealPath()),"wrong standalone Jail JAR");
                require(!dev.unyielding.jail.JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()&&!dev.unyielding.jail.JailConfig.JAIL_PACKED_WALLETS.get(),"admissions must default off");
                try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("integrated Jail returned");}catch(ClassNotFoundException expected){}
                var pairProof=new java.util.TreeMap<String,Object>();pairProof.put("commit",System.getenv("GITHUB_SHA"));pairProof.put("admissions_default_off",true);
                for(String jarName:List.of("Minecraft-Jail-1.2.0.jar","Unyielding-1.2.0.jar")){var jarPath=Path.of(System.getenv(jarName.startsWith("Minecraft")?"UQ_JAIL_JAR":"UQ_BUNDLE_JAR"));pairProof.put(jarName,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jarPath))));}
                Files.writeString(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).resolve("paired-identity.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(pairProof));'''
    source.write_text(text.replace(old, new))
    # A dry, uncropped farmland block may revert between the screenshot and
    # server assertion. Hydrate the disposable plot without changing the action
    # or expected transformations, including protected negative controls.
    text = source.read_text()
    old = 'static void ground(boolean hoe){seed(false);'
    assert text.count(old) == 1
    source.write_text(text.replace(old, old + 'if(hoe)server.overworld().setBlock(origin.south(),Blocks.WATER.defaultBlockState(),3);'))
    with Path(os.environ['GITHUB_ENV']).open('a') as output:
        output.write(f'UQ_JAIL_JAR={libs / "Minecraft-Jail-1.2.0.jar"}\n')
(ROOT / 'dist/joint-preparation.json').write_text(json.dumps({'mode': mode, 'pinned_wallet_source': WALLET_SOURCE, 'jars': manifest['jars'], 'ongoing_custody_work_used': False}, indent=2)+'\n')
