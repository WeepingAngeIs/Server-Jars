"""Strict planned-publication interruption evidence; no graceful exit substitution."""
import json,os,sys
from pathlib import Path

def validate(report,marker,log,code,expected,phase,backend,commit):
    if phase not in (1,2) or backend not in ('nbt','json') or expected!=93+phase or code!=expected:
        raise ValueError('Wrong planned phase/exit')
    if marker!=f'passed: read-only handback {backend} phase {phase}\n' or f'HANDBACK_PLANNED_HALT_{expected}' not in log.splitlines():
        raise ValueError('Missing exact completion or JVM halt marker')
    if report.get('commit')!=commit or report.get('phase')!=phase or report.get('backend')!=backend or report.get('expected_exit')!=expected:
        raise ValueError('Wrong report identity')
    if any(report.get(k) is not False for k in ('admission_enabled','playable_handback_verified','production_changed')):
        raise ValueError('Unexpected production/gameplay authorization')
    point='hard_halt_after_swap_before_retirement' if phase==1 else 'hard_halt_after_retirement_before_access'
    denial='swapped_cache_without_receipt_not_accessible' if phase==1 else 'committed_epoch_not_exposed_early'
    if report.get(point) is not True or report.get(denial) is not True:raise ValueError('Missing closed-gate interruption evidence')
    return {'commit':commit,'phase':phase,'backend':backend,'actual_exit':code,'expected_exit':expected,'planned_halt_verified':True,'graceful_save_used':False}

if __name__=='__main__':
    code,expected=map(int,sys.argv[1:3]);phase=int(os.environ['UQ_LIVE_PHASE']);backend=os.environ['UQ_LIVE_BACKEND'];root=Path(os.environ['GITHUB_WORKSPACE'])/'dist/packaged-smoke'
    result=validate(json.loads((root/f'handback-{backend}-phase{phase}.json').read_text()),Path(os.environ['UQ_RESULT_FILE']).read_text(),(root/'jail-player-logs/server.log').read_text(),code,expected,phase,backend,os.environ['GITHUB_SHA'])
    print(json.dumps(result,indent=2))
