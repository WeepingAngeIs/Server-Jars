"""Adapt only independent fixtures. Preserve the verified production pair byte-for-byte."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Hosted disposable runner required')
subprocess.run(['python3', '.github/scripts/prepare-split-server.py'], cwd=ROOT, check=True)
source = ROOT/'dist/packaged-harness/src/main/java/dev/unyielding/bundlesmoke'
p = source/'IntegratedJailSmoke.java'
s = p.read_text()
needle = '    @SubscribeEvent public static void tick(ServerTickEvent.Post event){'
assert s.count(needle) == 1
p.write_text(s.replace(needle, needle+'\n        if(Boolean.getBoolean("uq.partyWalletProbe"))return;', 1))
# The destructive origin seed helper has its own world allowlist. Extend only
# that TEST fixture for this named synthetic experiment; retain hosted guards.
p=source/'OriginCustodyPort.java';s=p.read_text()
needle='||!(Boolean.getBoolean("uq.originCustody")'
replacement='||!(Boolean.getBoolean("uq.partyWalletProbe")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_integrated_smoke")||Boolean.getBoolean("uq.originCustody")'
assert s.count(needle)==1
p.write_text(s.replace(needle,replacement,1))
shutil.copy2(ROOT/'.github/party-wallet/PartyWalletProbe.java', source)
evidence=ROOT/'dist/packaged-smoke/api-audit'
evidence.mkdir()
# Inspect exact runtime bytecode, not a moving upstream branch.
classes = {
    'cobblemon': ['com.cobblemon.mod.common.api.storage.PokemonStoreManager', 'com.cobblemon.mod.common.api.storage.party.PlayerPartyStore', 'com.cobblemon.mod.common.api.storage.party.PartyStore', 'com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory'],
    'lightmanscurrency': ['io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler', 'io.github.lightman314.lightmanscurrency.common.items.WalletItem', 'io.github.lightman314.lightmanscurrency.common.items.data.WalletDataWrapper'],
}
for label, names in classes.items():
    jars=[]
    for jar in sorted((ROOT/'dist/packaged-harness/libs').glob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            if names[0].replace('.', '/')+'.class' in z.namelist():jars.append(jar)
    assert len(jars)==1, (label,jars)
    jar=jars[0]
    output = subprocess.run(['javap','-p','-c','-classpath',str(jar),*names], text=True, capture_output=True, check=True).stdout
    (evidence/(label+'.txt')).write_text('Artifact SHA512: '+hashlib.sha512(jar.read_bytes()).hexdigest()+'\n'+output)
