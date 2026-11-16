"""The exact pinned Cobblemon binary reports the full Minecraft-suffixed version."""
from pathlib import Path
import tomllib
import unittest

class JailLoaderMetadataTests(unittest.TestCase):
    def test_matches_exact_certified_runtime_version(self):
        root=Path(__file__).resolve().parents[2]
        meta=tomllib.loads((root/'minecraft-jail/src/main/resources/META-INF/neoforge.mods.toml').read_text())
        deps={row['modId']:row for row in meta['dependencies']['minecraftjail']}
        self.assertEqual('[1.8.4+1.21.1]',deps['cobblemon']['versionRange'])
        self.assertEqual('required',deps['cobblemon']['type'])
        self.assertEqual('[1.21.1]',deps['minecraft']['versionRange'])
        self.assertEqual('[21.1.255,21.2)',deps['neoforge']['versionRange'])
        self.assertEqual('optional',deps['unyieldingquests']['type'])
