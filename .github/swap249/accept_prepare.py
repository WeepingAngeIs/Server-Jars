"""Add swap-specific controls to the existing hosted combined fixture."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
source=(root/".github/native249/combined_prepare.py").read_text()
marker='source = source.replace(marker, insert + marker)'
assert source.count(marker)==1
extra=r'''
shutil.copy2(root/".github/swap249/SwapPreflight.java",src)
shutil.copy2(root/".github/swap249/SwapGuardControls.java",src)
replace(full, '        Custody249Proof.refusals(player);', '        SwapGuardControls.preflight(player);\n        Custody249Proof.refusals(player);')
replace(full, '        CombinedStorageProof.attemptRecovery(player,true);', '        SwapGuardControls.held(player);\n        CombinedStorageProof.attemptRecovery(player,true);')
# Full client initialization and software rendering can lag behind server ticks on
# standard runners. Keep the original bounded observation window, and still
# require the real client to observe the death and replacement without a click.
replace(full, '        deadline=player.server.getTickCount()+400;', '        deadline=player.server.getTickCount()+2400;')
'''
source=source.replace(marker,'insert += '+repr(extra)+'\n'+marker)
exec(compile(source,str(root/".github/native249/combined_prepare.py"),"exec"),
     {"__file__":str(root/".github/native249/combined_prepare.py")})
