import unittest
from verify import validate, CRASH, REQUIRED

class EvidenceValidation(unittest.TestCase):
    def report(self,scenario='lifecycle',phase=1):
        r={'commit':'exact','scenario':scenario,'phase':phase,'jail_sha256':'new-jail','companion_sha256':'unchanged','hard_stop':(scenario,phase) in CRASH,'actual_disconnect_observed':True,'actual_wallet_custody_admission':True,'both_admission_switches_default_off':True,'default_off_refused_without_mutation':True,'legacy_supported_mode_does_not_enable_wallets':True,'packed_wallet_native_preflight_passed':True,'crash_live_state_verified':True,'crash_disk_state_verified':True}
        if r['hard_stop']:r['crash_boundary']=CRASH[(scenario,phase)]
        for key in REQUIRED.get((scenario,phase),[]):r[key]=True
        return r
    def check(self,r,exitcode='0'):
        return validate(r,r['scenario'],r['phase'],'exact','new-jail','unchanged',exitcode)
    def test_normal_phase(self):self.check(self.report())
    def test_crash_requires_actual_exit86(self):
        r=self.report('remove-saved',1)
        with self.assertRaises(ValueError):self.check(r)
        self.check(r,'86')
    def test_wrong_commit(self):
        r=self.report();r['commit']='old'
        with self.assertRaises(ValueError):self.check(r)
    def test_wrong_jail(self):
        r=self.report();r['jail_sha256']='different'
        with self.assertRaises(ValueError):self.check(r)
    def test_wrong_companion(self):
        r=self.report();r['companion_sha256']='changed'
        with self.assertRaises(ValueError):self.check(r)
    def test_no_real_admission(self):
        r=self.report();r['actual_wallet_custody_admission']=False
        with self.assertRaises(ValueError):self.check(r)
    def test_no_real_disconnect(self):
        r=self.report();del r['actual_disconnect_observed']
        with self.assertRaises(ValueError):self.check(r)
    def test_missing_death_proof(self):
        r=self.report('lifecycle',2);r['actual_jailed_escape_death_with_wallet_in_escrow']=False
        with self.assertRaises(ValueError):self.check(r)
    def test_false_crash_claim(self):
        r=self.report('return-unsaved',2);r['hard_stop']=False
        with self.assertRaises(ValueError):self.check(r,'86')
    def test_missing_saved_state_proof(self):
        r=self.report('return-saved',2);r['crash_disk_state_verified']=False
        with self.assertRaises(ValueError):self.check(r,'86')
    def test_uncertain_replay_not_certified(self):
        r=self.report('return-unsaved',3)
        with self.assertRaises(ValueError):self.check(r)
        for key in ['ambiguous_save_remains_quarantined','ambiguous_save_resource_location_preserved','uncertain_save_never_replayed_or_granted']:r[key]=True
        self.check(r)
    def test_missing_conflict_proof(self):
        r=self.report('occupied',3);del r['occupied_destination_repeat_recovery_refused']
        with self.assertRaises(ValueError):self.check(r)
    def test_normal_abnormal_exit(self):
        with self.assertRaises(ValueError):self.check(self.report(),'137')
    def test_wrong_boundary(self):
        r=self.report('remove-unsaved',1);r['crash_boundary']='ITEMS_DURABLE'
        with self.assertRaises(ValueError):self.check(r,'86')
    def test_missing_final_review_is_not_a_pass(self):
        r=self.report('return-saved',3);del r['final_review_completed_without_resource_regrant']
        with self.assertRaises(ValueError):self.check(r)
    def test_false_final_review_gate_is_not_a_pass(self):
        r=self.report('return-saved',3);r['unreviewed_saved_return_cannot_finish']=False
        with self.assertRaises(ValueError):self.check(r)
if __name__=='__main__':unittest.main()
