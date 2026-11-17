import copy,unittest
from check_halt import validate

class EvidenceTests(unittest.TestCase):
    def sample(self,phase=1):
        code=93+phase
        report={'commit':'test-commit','phase':phase,'backend':'nbt','expected_exit':code,'admission_enabled':False,'playable_handback_verified':False,'production_changed':False,
                ('hard_halt_after_swap_before_retirement' if phase==1 else 'hard_halt_after_retirement_before_access'):True,
                ('swapped_cache_without_receipt_not_accessible' if phase==1 else 'committed_epoch_not_exposed_early'):True}
        return [report,f'passed: read-only handback nbt phase {phase}\n',f'HANDBACK_PLANNED_HALT_{code}\n',code,code,phase,'nbt','test-commit']
    def test_exact_first(self):self.assertTrue(validate(*self.sample())['planned_halt_verified'])
    def test_exact_second(self):self.assertTrue(validate(*self.sample(2))['planned_halt_verified'])
    def test_graceful_is_not_crash(self):
        a=self.sample();a[3]=0
        with self.assertRaises(ValueError):validate(*a)
    def test_stale_commit(self):
        a=self.sample();a[0]['commit']='older'
        with self.assertRaises(ValueError):validate(*a)
    def test_missing_denial(self):
        a=self.sample();del a[0]['swapped_cache_without_receipt_not_accessible']
        with self.assertRaises(ValueError):validate(*a)
    def test_missing_point(self):
        a=self.sample(2);del a[0]['hard_halt_after_retirement_before_access']
        with self.assertRaises(ValueError):validate(*a)
    def test_gameplay_authorization_rejected(self):
        a=self.sample();a[0]['playable_handback_verified']=True
        with self.assertRaises(ValueError):validate(*a)
    def test_wrong_marker(self):
        a=self.sample();a[1]='passed: some other test\n'
        with self.assertRaises(ValueError):validate(*a)
    def test_missing_halt_log(self):
        a=self.sample();a[2]=''
        with self.assertRaises(ValueError):validate(*a)
    def test_wrong_phase(self):
        a=self.sample();a[5]=3
        with self.assertRaises(ValueError):validate(*a)

if __name__=='__main__':unittest.main()
