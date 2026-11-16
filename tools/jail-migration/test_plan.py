import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from plan import inventory, make_plan, portable_paths, certificate_from_verified_jar

class PlanTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.cert={'minecraft':'1.21.1','neoforge':'21.1.255','sha512':[]}
    def archive(self, entries):
        p=self.root/'input.zip'
        with zipfile.ZipFile(p,'w') as z:
            for name,raw in entries:z.writestr(name,raw)
        return p
    def jar(self, legacy=False, split=False):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w') as z:
            z.writestr('META-INF/neoforge.mods.toml','[[mods]]\nmodId="synthetic"\nversion="1"\n')
            if legacy:z.writestr('dev/unyielding/quests/jail/JailData.class',b'synthetic, not executable')
            if split:z.writestr('dev/unyielding/jail/MinecraftJail.class',b'synthetic, not executable')
        return stream.getvalue()
    def test_missing_inputs_stay_not_run(self):
        result=make_plan({});self.assertFalse(result['launch_authorized']);self.assertIsNone(result['input_archives_unchanged'])
        self.assertEqual(result['actual_save_migration'],'NOT_RUN');self.assertGreater(len(result['blockers']),3)
    def test_unknown_binary_is_blocker(self):
        p=self.archive([('mods/unknown.jar',self.jar())]);r=inventory(p,'pack',self.cert)
        self.assertTrue(any('Unreviewed artifact' in s for s in r['blockers']))
    def test_reviewed_hash_is_not_runtime_proof(self):
        raw=self.jar();self.cert['sha512']=[hashlib.sha512(raw).hexdigest()]
        r=inventory(self.archive([('mods/synthetic.jar',raw)]),'pack',self.cert)
        self.assertTrue(r['artifacts'][0]['reviewed_bytes'])
        self.assertFalse(make_plan({'pack':r})['launch_authorized'])
    def test_archive_bytes_unchanged(self):
        p=self.archive([('world/level.dat',b'not-parsed'),('world/playerdata/private-player.dat',b'synthetic')]);before=p.read_bytes()
        r=inventory(p,'save',self.cert);self.assertEqual(before,p.read_bytes());self.assertTrue(r['source_unchanged'])
    def test_reports_do_not_publish_private_names(self):
        r=inventory(self.archive([('my-private-world/level.dat',b'a'),('my-private-world/playerdata/secret-player.dat',b'b')]),'save',self.cert)
        text=json.dumps(r);self.assertNotIn('secret-player',text);self.assertNotIn('my-private-world',text)
    def test_multiple_worlds_blocked(self):
        r=inventory(self.archive([('a/level.dat',b'a'),('b/level.dat',b'b')]),'save',self.cert)
        self.assertTrue(any('one world' in s for s in r['blockers']))
    def test_missing_playerdata_blocked(self):
        r=inventory(self.archive([('world/level.dat',b'a')]),'save',self.cert);self.assertIn('Playerdata missing',r['blockers'])
    def test_mixed_owners_blocked(self):
        r=inventory(self.archive([('mods/a.jar',self.jar(legacy=True)),('mods/b.jar',self.jar(split=True))]),'pack',self.cert)
        self.assertTrue(any('coexist' in s for s in r['blockers']))
    def test_duplicate_owner_blocked(self):
        raw=self.jar(legacy=True)
        r=inventory(self.archive([('mods/a.jar',raw),('mods/b.jar',raw)]),'old_runtime',self.cert)
        self.assertTrue(any('Duplicate' in s for s in r['blockers']))
    def test_old_runtime_requires_old_owner(self):
        r=inventory(self.archive([('mods/a.jar',self.jar())]),'old_runtime',self.cert)
        self.assertTrue(any('integrated Jail' in s for s in r['blockers']))
    def test_wrong_old_identity_blocked(self):
        r=inventory(self.archive([('mods/a.jar',self.jar(legacy=True))]),'old_runtime',self.cert)
        self.assertTrue(any('does not match' in s for s in make_plan({'old_runtime':r},'0'*64)['blockers']))
    def test_invalid_digest_rejected(self):
        with self.assertRaises(ValueError):make_plan({},'short')
    def test_loader_mismatch_blocked(self):
        manifest={'manifestType':'minecraftModpack','minecraft':{'version':'1.21.1','modLoaders':[{'id':'neoforge-21.1.249'}]}}
        r=inventory(self.archive([('manifest.json',json.dumps(manifest))]),'pack',self.cert)
        self.assertTrue(any('Loader' in s for s in r['blockers']))
    def test_source_evidence_cannot_be_forged_by_changed_flag(self):
        with self.assertRaises(ValueError):make_plan({'save':{'role':'save','source_unchanged':False,'blockers':[]}})
    def test_certificate_from_modified_jar_rejected(self):
        p=self.root/'jail.jar';p.write_bytes(self.jar())
        with self.assertRaises(ValueError):certificate_from_verified_jar(p)
    def test_portable_path_aliases_rejected(self):
        for path in ['mods/CON.jar','world/name.','world/name ','world//file','mods/bad?.jar','world/\x01.dat']:
            with self.subTest(path=path):
                p=self.archive([(path,b'x')])
                with zipfile.ZipFile(p) as z:
                    with self.assertRaises(ValueError):portable_paths(z)
    def test_file_as_parent_rejected(self):
        p=self.archive([('world',b'x'),('world/level.dat',b'y')])
        with zipfile.ZipFile(p) as z:
            with self.assertRaises(ValueError):portable_paths(z)
    def test_unknown_role_rejected(self):
        with self.assertRaises(ValueError):inventory(self.archive([]),'live',self.cert)

if __name__=='__main__': unittest.main()
