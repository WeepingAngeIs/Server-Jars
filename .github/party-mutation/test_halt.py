import unittest
from check_halt import validate

class EvidenceTests(unittest.TestCase):
    def sample(self,phase=1):
        code=95+phase
        report={'commit':'test-commit','phase':phase,'backend':'nbt','expected_exit':code,'admission_enabled':False,'playable_handback_verified':False,'production_changed':False,
                ('hard_halt_after_intent_before_native_write' if phase==1 else 'hard_halt_after_edit_commit_before_publication'):True,
                ('intent_committed_access_closed' if phase==1 else 'edit_commit_not_exposed_before_validation'):True}
        return [report,f'passed: bounded mutation nbt phase {phase}\n',f'MUTATION_PLANNED_HALT_{code}\n',code,code,phase,'nbt','test-commit']
    def test_exact_first(self):self.assertTrue(validate(*self.sample())['planned_halt_verified'])
    def test_exact_second(self):self.assertTrue(validate(*self.sample(2))['planned_halt_verified'])
    def test_graceful_is_not_crash(self):
        a=self.sample();a[3]=0
        with self.assertRaises(ValueError):validate(*a)
    def test_stale_commit(self):
        a=self.sample();a[0]['commit']='older'
        with self.assertRaises(ValueError):validate(*a)
    def test_missing_denial(self):
        a=self.sample();del a[0]['intent_committed_access_closed']
        with self.assertRaises(ValueError):validate(*a)
    def test_missing_point(self):
        a=self.sample(2);del a[0]['hard_halt_after_edit_commit_before_publication']
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
