import importlib.util
import io
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('preflight',Path(__file__).with_name('preflight.py'))
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)

class PreflightTests(unittest.TestCase):
    def archive(self,rows):
        b=io.BytesIO()
        with zipfile.ZipFile(b,'w') as z:
            for key,value in rows:z.writestr(key,value)
        b.seek(0);return zipfile.ZipFile(b)
    def test_traversal_rejected(self):
        for name in ('../level.dat','world/../level.dat','/etc/passwd','C:/save/level.dat','world\\level.dat'):
            with self.subTest(name=name),self.archive([(name,b'x')]) as z,self.assertRaises(p.UnsafeArchive):p.checked_entries(z)
    def test_duplicate_rejected(self):
        with self.archive([('world/level.dat',b'x'),('WORLD/level.dat',b'y')]) as z,self.assertRaises(p.UnsafeArchive):p.checked_entries(z)
    def test_symlink_rejected(self):
        i=zipfile.ZipInfo('world/link');i.create_system=3;i.external_attr=(stat.S_IFLNK|0o777)<<16
        with self.archive([(i,b'../../save')]) as z,self.assertRaises(p.UnsafeArchive):p.checked_entries(z)
    def test_regular_zip_accepted(self):
        with self.archive([('world/level.dat',b'x')]) as z:self.assertEqual(len(p.checked_entries(z)),1)
    def test_bounded_read(self):
        with self.archive([('manifest.json',b'12345')]) as z,self.assertRaises(p.UnsafeArchive):p.bounded_read(z,z.infolist()[0],4)
    def test_manifest_not_runtime(self):
        with tempfile.TemporaryDirectory() as d:
            archive=Path(d)/'pack.zip'
            manifest={'manifestType':'minecraftModpack','version':'1.0.8','minecraft':{'version':'1.21.1','modLoaders':[{'id':'neoforge-21.1.249'}]},'files':[{'projectID':1,'fileID':2}]}
            with zipfile.ZipFile(archive,'w') as z:z.writestr('manifest.json',json.dumps(manifest))
            before=archive.read_bytes();r=p.inspect(archive,'pack',{'minecraft':'1.21.1','neoforge':'21.1.255','sha512':[]})
            self.assertEqual(before,archive.read_bytes());self.assertEqual(r['jar_count'],0);self.assertEqual(len(r['blockers']),2);self.assertFalse(r['launch_authorized'])
    def test_missing_save_blocked(self):
        r=p.plan(None,None);self.assertFalse(r['launch_authorized']);self.assertEqual(r['actual_save_migration'],'NOT_RUN');self.assertGreater(len(r['blockers']),0)
    def test_world_without_players_incomplete(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'world.zip'
            with zipfile.ZipFile(path,'w') as z:z.writestr('world/level.dat',b'placeholder')
            r=p.inspect(path,'save');self.assertTrue(r['save_present']);self.assertFalse(r['player_data_present']);self.assertTrue(r['blockers'])
    def test_complete_shape_is_not_migration_proof(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'world.zip'
            with zipfile.ZipFile(path,'w') as z:z.writestr('world/level.dat',b'placeholder');z.writestr('world/playerdata/test.dat',b'placeholder')
            r=p.inspect(path,'save');self.assertEqual(r['migration_test_status'],'NOT_RUN');self.assertFalse(r['launch_authorized'])
    def test_unknown_jar_not_trusted(self):
        with self.archive([('META-INF/neoforge.mods.toml',b'[[mods]]\nmodId="unknown"\nversion="1"\n')]) as z:
            z.fp.seek(0);r=p.jar_info(z.fp.read(),{'sha512':[]})
            self.assertFalse(r['listed_by_runtime_certificate']);self.assertEqual(r['mods'][0]['id'],'unknown')

if __name__=='__main__':unittest.main()
