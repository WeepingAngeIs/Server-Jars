import copy
import unittest
from verify import verify

class EvidenceTest(unittest.TestCase):
    def reports(self, kind='party'):
        rows=[dict(commit='test', kind=kind, phase=i, new_support_enabled=False, real_disconnect_observed=True) for i in range(1,5)]
        keys=[['nonempty_admission_blocked','refused_admission_resource_equal','supported_mode_opted_in_in_disposable_world'],
              ['restart_after_refusal_resource_equal','nonempty_admission_blocked','supported_empty_resource_control_admitted','external_addition_exact','outside_death_blocked','escape_death_blocked','unsafe_death_resource_equal'],
              ['restart_in_custody_resource_equal','outside_death_blocked','escape_death_blocked','release_resource_equal'],
              ['post_release_restart_resource_equal','repeat_release_resource_equal','no_duplicate_transaction_or_grant']]
        for r, ks in zip(rows, keys):
            r.update({k:True for k in ks})
        rows[2].update(release_result=1 if kind=='party' else 0,equipment_restored_exact=True,release_remains_held=True)
        rows[3].update(released_state_and_equipment_survived_restart=True,wallet_hold_survived_restart_and_retries=True)
        return rows
    def test_valid_expected_outcomes(self):
        for kind in ('party','wallet','both'):verify(self.reports(kind),kind,'test')
    def test_missing_observation_rejected(self):
        rows=self.reports();rows[3].pop('post_release_restart_resource_equal')
        with self.assertRaises(ValueError):verify(rows,'party','test')
    def test_false_observation_rejected(self):
        rows=self.reports();rows[1]['outside_death_blocked']=False
        with self.assertRaises(ValueError):verify(rows,'party','test')
    def test_wrong_commit_rejected(self):
        with self.assertRaises(ValueError):verify(self.reports(),'party','different')
    def test_missing_phase_rejected(self):
        with self.assertRaises(ValueError):verify(self.reports()[:3],'party','test')
    def test_swapped_phases_rejected(self):
        rows=self.reports();rows[1],rows[2]=rows[2],rows[1]
        with self.assertRaises(ValueError):verify(rows,'party','test')
    def test_no_new_support_claim_allowed(self):
        rows=self.reports();rows[0]['new_support_enabled']=True
        with self.assertRaises(ValueError):verify(rows,'party','test')
    def test_wallet_release_not_authorized(self):
        rows=self.reports('wallet');rows[2]['release_result']=1
        with self.assertRaises(ValueError):verify(rows,'wallet','test')
    def test_no_disconnect_no_pass(self):
        rows=self.reports();rows[2]['real_disconnect_observed']=False
        with self.assertRaises(ValueError):verify(rows,'party','test')

if __name__=='__main__':unittest.main()
