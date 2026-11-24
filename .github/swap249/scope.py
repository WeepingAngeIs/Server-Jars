"""Reuse the verified free-runner memory scope with the swap observer."""
from pathlib import Path

root=Path(__file__).resolve().parents[2]
source=(root/".github/native249/game-scope.sh").read_text()
old='driver=.github/native249/full_run.py\nif [[ -n ${COMBINED_STORAGE_JAR:-} ]]; then driver=.github/native249/combined_run.py; fi'
assert source.count(old)==1
source=source.replace(old,'driver=.github/swap249/run.py')
target=root/"dist/swap-game-scope.sh"
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(source)
