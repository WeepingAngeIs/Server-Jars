"""Require actual phase evidence and exact, unchanged production bytes."""
import hashlib
import json
import os
from pathlib import Path
import sys

PINNED = {'companion': '61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7',
          'jail': 'd3145b1bc8f04c0370db30ff10e87158b6c3c88c0fde68cb0f226d3b73f79baf'}

def verify(reports, kind, commit):
    if kind not in ('party', 'wallet', 'both') or len(reports) != 4:
        raise ValueError('Exactly four phases of a known kind are required')
    for phase, r in enumerate(reports, 1):
        if r.get('commit') != commit or r.get('kind') != kind or r.get('phase') != phase or r.get('new_support_enabled') is not False:
            raise ValueError('Wrong commit/kind/phase or unauthorized support claim')
        if phase < 4 and r.get('real_disconnect_observed') is not True:
            raise ValueError('Disconnect not observed')
    requirements = [
        ['nonempty_admission_blocked', 'refused_admission_resource_equal', 'supported_mode_opted_in_in_disposable_world'],
        ['restart_after_refusal_resource_equal', 'nonempty_admission_blocked', 'supported_empty_resource_control_admitted', 'external_addition_exact', 'outside_death_blocked', 'escape_death_blocked', 'unsafe_death_resource_equal'],
        ['restart_in_custody_resource_equal', 'outside_death_blocked', 'escape_death_blocked', 'release_resource_equal'],
        ['post_release_restart_resource_equal', 'repeat_release_resource_equal', 'no_duplicate_transaction_or_grant']]
    for r, keys in zip(reports, requirements):
        for key in keys:
            if r.get(key) is not True:
                raise ValueError('Missing passing observation: '+key)
    if kind == 'party':
        if reports[2].get('release_result') != 1 or reports[2].get('equipment_restored_exact') is not True or reports[3].get('released_state_and_equipment_survived_restart') is not True:
            raise ValueError('Successful existing-custody release was not durable')
    elif reports[2].get('release_result') != 0 or reports[2].get('release_remains_held') is not True or reports[3].get('wallet_hold_survived_restart_and_retries') is not True:
        raise ValueError('Unsupported wallet did not remain held')

def main():
    p = Path('dist/packaged-smoke'); kind = sys.argv[1]
    reports = [json.loads((p/f'continuation-{kind}-phase{i}.json').read_text()) for i in range(1,5)]
    verify(reports, kind, os.environ['GITHUB_SHA'])
    for key, env in [('companion', 'UQ_BUNDLE'), ('jail', 'UQ_JAIL')]:
        digest = hashlib.sha256(Path(os.environ[env+'_JAR']).read_bytes()).hexdigest()
        if digest != PINNED[key] or digest != os.environ[env+'_SHA256']:
            raise ValueError('Production artifact changed')
    (p/f'continuation-{kind}-summary.json').write_text(json.dumps({'commit': os.environ['GITHUB_SHA'], 'passed_phases': 4, 'reports': reports, 'sha256': PINNED, 'new_support_enabled': False}, indent=2)+'\n')

if __name__ == '__main__':
    main()
