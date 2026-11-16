import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile
from companion_certificate import ROOT, CERTIFICATE, PROOF, validate_extension
from stable_companion import canonicalize

class CompanionCertificateTests(unittest.TestCase):
    def setUp(self):
        self.current = json.loads((ROOT/'minecraft-jail/src/main/resources'/CERTIFICATE).read_text())
        self.pins = {a['sha512'] for a in json.loads(PROOF.read_text())['artifacts']}
        self.before = copy.deepcopy(self.current)
        self.before['sha512'] = [x for x in self.before['sha512'] if x not in self.pins]

    def test_only_two_reviewed_additions(self):
        validate_extension(json.dumps(self.before), json.dumps(self.current))
        self.assertEqual(2, len(self.pins))

    def test_no_unknown_dependency_hash(self):
        altered=copy.deepcopy(self.current);altered['sha512'].append('a'*128)
        with self.assertRaises(AssertionError): validate_extension(json.dumps(self.before), json.dumps(altered))

    def test_no_kernel_contract_or_embedded_relaxation(self):
        for key,value in [('minecraft','*'),('neoforge','21.1.*'),('contract','any'),('embedded',[])]:
            altered=copy.deepcopy(self.current);altered[key]=value
            with self.subTest(key=key), self.assertRaises(AssertionError):
                validate_extension(json.dumps(self.before), json.dumps(altered))

    def test_no_existing_pin_removed(self):
        altered=copy.deepcopy(self.current);altered['sha512'].remove(self.before['sha512'][0])
        with self.assertRaises(AssertionError): validate_extension(json.dumps(self.before), json.dumps(altered))

    def test_filename_does_not_certify_changed_bytes(self):
        data=b'Not the reviewed companion, even when named Unyielding-1.2.0.jar'
        self.assertNotIn(hashlib.sha512(data).hexdigest(),self.current['sha512'])

    def test_archive_metadata_is_reproducible_and_content_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            a=Path(d)/'a.jar'; b=Path(d)/'b.jar'
            with ZipFile(a,'w') as z:z.writestr('z',b'last');z.writestr('a',b'first')
            with ZipFile(b,'w') as z:z.writestr('a',b'first');z.writestr('z',b'last')
            canonicalize(a);canonicalize(b)
            self.assertEqual(a.read_bytes(),b.read_bytes())
            with ZipFile(a) as z:self.assertEqual({'a':b'first','z':b'last'},{n:z.read(n) for n in z.namelist()})
