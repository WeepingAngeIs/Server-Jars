"""Keep the existing hosted memory limits; choose this isolated evidence driver."""
import os
from pathlib import Path

assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
root = Path(__file__).resolve().parents[2]
source = (root / ".github/native249/game-scope.sh").read_text()
old = 'driver=.github/native249/full_run.py\nif [[ -n ${COMBINED_STORAGE_JAR:-} ]]; then driver=.github/native249/combined_run.py; fi'
assert source.count(old) == 1
source = source.replace(old, 'driver=.github/release249/run.py')
target = root / "dist/release-game-scope.sh"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(source)
