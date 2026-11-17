"""Accept only a completed, expected synthetic crash, never a graceful-save substitute."""
from __future__ import annotations
import json
import os
from pathlib import Path
import sys


def validate(report: dict, marker: str, log: str, code: int, expected: int,
             phase: int, backend: str, commit: str) -> dict:
    if phase not in (1, 2) or backend not in ('nbt', 'json') or expected != 91 + phase:
        raise ValueError('Not a recognized planned crash phase')
    if code != expected:
        raise ValueError(f'Expected exit {expected}, observed {code}')
    if marker != f'passed: native fence {backend} phase {phase}\n':
        raise ValueError('Missing exact current-phase completion marker')
    if f'PARTY_LIVE_PLANNED_HALT_{expected}' not in log.splitlines():
        raise ValueError('Missing native hard-halt log evidence')
    if report.get('commit') != commit or report.get('phase') != phase or report.get('backend') != backend:
        raise ValueError('Wrong phase/commit in hard-halt report')
    if any(report.get(k) is not False for k in ('nonempty_admission_enabled', 'player_handback_verified', 'production_changed')):
        raise ValueError('Unexpected custody authorization')
    final = 'unrelated_native_owner_unchanged' if phase == 1 else 'hard_halt_after_return_generation_before_native_write'
    if report.get(final) is not True:
        raise ValueError('Planned interruption point was not reached')
    return {'commit': commit, 'phase': phase, 'backend': backend,
            'expected_exit': expected, 'actual_exit': code, 'planned_halt_verified': True,
            'graceful_save_used': False}


if __name__ == '__main__':
    code, expected = map(int, sys.argv[1:3])
    phase = int(os.environ['UQ_LIVE_PHASE'])
    backend = os.environ['UQ_LIVE_BACKEND']
    root = Path(os.environ['GITHUB_WORKSPACE']) / 'dist/packaged-smoke'
    report = json.loads((root / f'party-live-{backend}-phase{phase}.json').read_text())
    result = validate(report, Path(os.environ['UQ_RESULT_FILE']).read_text(),
                      (root / 'jail-player-logs/server.log').read_text(),
                      code, expected, phase, backend, os.environ['GITHUB_SHA'])
    print(json.dumps(result, indent=2))
