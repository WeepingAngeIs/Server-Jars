import copy
import unittest
from check_halt import validate


class PlannedHaltTests(unittest.TestCase):
    def run_case(self, phase=1, **changes):
        report={'commit':'test-sha','backend':'nbt','phase':phase,
                'nonempty_admission_enabled':False,'player_handback_verified':False,
                'production_changed':False,'unrelated_native_owner_unchanged':True,
                'hard_halt_after_return_generation_before_native_write':True}
        values=dict(report=report, marker=f'passed: native fence nbt phase {phase}\n',
                    log=f'PARTY_LIVE_PLANNED_HALT_{91+phase}\n', code=91+phase,
                    expected=91+phase,phase=phase,backend='nbt',commit='test-sha')
        values.update(changes)
        return validate(**values)

    def test_accepts_both_exact_crash_phases(self):
        for phase in (1,2):
            self.assertTrue(self.run_case(phase)['planned_halt_verified'])

    def test_graceful_or_wrong_exit_refused(self):
        for code in (0,1,91,137):
            with self.assertRaises(ValueError):self.run_case(code=code)

    def test_stale_marker_refused(self):
        with self.assertRaises(ValueError):self.run_case(2,marker='passed: native fence nbt phase 1\n')

    def test_wrong_or_missing_halt_log_refused(self):
        for log in ('', 'PARTY_LIVE_PLANNED_HALT_93\n', 'prefix PARTY_LIVE_PLANNED_HALT_92\n'):
            with self.assertRaises(ValueError):self.run_case(log=log)

    def test_wrong_report_or_unreached_point_refused(self):
        for key,value in [('commit','old'),('phase',2),('backend','json'),('unrelated_native_owner_unchanged',False)]:
            report={'commit':'test-sha','backend':'nbt','phase':1,'nonempty_admission_enabled':False,
                    'player_handback_verified':False,'production_changed':False,'unrelated_native_owner_unchanged':True}
            report[key]=value
            with self.assertRaises(ValueError):self.run_case(report=report)

    def test_ordinary_phase_not_treated_as_planned_crash(self):
        with self.assertRaises(ValueError):self.run_case(3)

    def test_unauthorized_custody_refused(self):
        report={'commit':'test-sha','backend':'nbt','phase':1,'nonempty_admission_enabled':True,
                'player_handback_verified':False,'production_changed':False,'unrelated_native_owner_unchanged':True}
        with self.assertRaises(ValueError):self.run_case(report=report)
