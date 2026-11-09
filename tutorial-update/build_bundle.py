"""Update the quest component and embed pinned custom content on GitHub only."""
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import tomllib
import zipfile


if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Assembly and validation run only on GitHub-hosted runners.")

ROOT = Path(__file__).resolve().parents[1]
release = json.loads((ROOT / "tutorial-update/release.json").read_text())
MODS = "META-INF/neoforge.mods.toml"
MANIFEST = "META-INF/MANIFEST.MF"
METADATA = {MODS, MANIFEST}
QUEST_PREFIXES = (
    "dev/unyielding/quests/", "assets/unyieldingquests/",
    "data/unyieldingquests/", "defaultquests/",
)


def owned_by_quests(name):
    return name.startswith(QUEST_PREFIXES)


def read_input(info):
    path = ROOT / info["path"]
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if "verification_report" in info:
        proof = json.loads((ROOT / info["verification_report"]).read_text())
        assert proof["status"] == "passed" and proof["version"] == info["version"]
        assert proof["jar"] == path.name and proof["development_helpers_in_release"] is False
        assert digest == proof["sha256"], "Verified quest artifact changed before assembly"
        info["sha256"] = digest
    assert digest == info["sha256"], f"Input checksum mismatch: {path.name}"
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        assert len(names) == len(set(names)), "Duplicate archive entries"
        assert not any(re.search(r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$", n, re.I) for n in names), "Signed input requires source rebuild"
        assert not any(n.startswith("/") or "\\" in n or ".." in PurePosixPath(n).parts for n in names), "Unsafe archive entry"
        return {name: archive.read(name) for name in names}


base = read_input(release["base"])
quests = read_input(release["quests"])
pokemon_pack = read_input(release["pokemon_pack"])
assert not [name for name in quests if name not in METADATA and not owned_by_quests(name)], "Unexpected quest payload namespace"
base_text = base[MODS].decode()
base_meta = tomllib.loads(base_text)
quest_meta = tomllib.loads(quests[MODS].decode().replace("${file.jarVersion}", release["quests"]["version"]))
assert set(quest_meta) == {"modLoader", "loaderVersion", "license", "mods", "dependencies", "mixins"}
assert quest_meta["mixins"], "Missing quest mixin configuration"
for mixin in quest_meta["mixins"]:
    assert set(mixin) == {"config"}, "Unexpected mixin metadata"
    assert mixin["config"].startswith("assets/unyieldingquests/") and mixin["config"] in quests, "Unexpected quest mixin namespace"
assert len({mixin["config"] for mixin in quest_meta["mixins"]}) == len(quest_meta["mixins"]), "Duplicate quest mixin"
for field in ("modLoader", "loaderVersion", "license"):
    assert base_meta[field] == quest_meta[field], f"Incompatible metadata: {field}"
assert len(base_meta["mods"]) == 9 and len(quest_meta["mods"]) == 1
old_mod = next(mod for mod in base_meta["mods"] if mod["modId"] == "unyieldingquests")
assert old_mod["version"] == release["quests"]["previous_version"]
new_mod = quest_meta["mods"][0]
assert new_mod == old_mod | {"version": release["quests"]["version"]}, "Quest metadata changed beyond the version"
assert set(quest_meta["dependencies"]) == {"unyieldingquests"}
assert quest_meta["dependencies"]["unyieldingquests"] == base_meta["dependencies"]["unyieldingquests"], "Quest dependencies changed; review required"
blocks = list(re.finditer(r"\[\[mods\]\].*?(?=\n\[\[|\Z)", base_text, re.S))
quest_blocks = [block for block in blocks if re.search(r'modId\s*=\s*"unyieldingquests"', block.group())]
assert len(quest_blocks) == 1
block = quest_blocks[0]
replacement, count = re.subn(r'(\bversion\s*=\s*")' + re.escape(release["quests"]["previous_version"]) + r'(")',
                             lambda match: match[1] + release["quests"]["version"] + match[2], block.group())
assert count == 1
metadata = base_text[:block.start()] + replacement + base_text[block.end():]
existing_mixins = {mixin["config"]: mixin for mixin in base_meta.get("mixins", [])}
new_mixins = []
for mixin in quest_meta["mixins"]:
    if mixin["config"] in existing_mixins:
        assert existing_mixins[mixin["config"]] == mixin, "Conflicting existing mixin metadata"
    else:
        metadata += '\n[[mixins]]\nconfig=' + json.dumps(mixin["config"]) + '\n'
        new_mixins.append(mixin)
expected_meta = copy.deepcopy(base_meta)
next(mod for mod in expected_meta["mods"] if mod["modId"] == "unyieldingquests")["version"] = release["quests"]["version"]
expected_meta["mixins"] = base_meta["mixins"] + new_mixins
assert tomllib.loads(metadata) == expected_meta, "Nonquest metadata changed"
assert "${" not in metadata

preserved = {name: payload for name, payload in base.items() if name not in METADATA and not owned_by_quests(name)}
updated = {name: payload for name, payload in quests.items() if name not in METADATA}
assert not set(preserved).intersection(updated)
entries = preserved | updated
# Mod assets and data are always part of the JAR's built-in resource/data packs.
# Do not include standalone installation notes, previews or build reports.
embedded = {}
for name, payload in pokemon_pack.items():
    if name.startswith(("assets/", "data/")):
        embedded[name] = payload
    elif name.startswith("LICENSES/"):
        embedded["LICENSES/unyielding-custom-content/" + name.removeprefix("LICENSES/")] = payload
assert embedded, "Custom content input has no embeddable resources"
species_names = {name for name in embedded if name.startswith("data/cobblemon/species/custom/") and name.endswith(".json")}
assert len(species_names) == 9, "Pinned custom species set changed"
assert not any("/spawn_pool_world/" in name for name in embedded), "Bundled input must preserve command-only spawn defaults"
adjusted_resources = []
# The original progress caps doubled as fixed evolution thresholds. Keep saved
# feature IDs while allowing administrators to raise the corresponding gates.
for feature, previous_maximum in {
    "mawonna_verified_dark_defeats": 666,
    "mawonna_verified_dark_defeats_1000": 1000,
    "mawonna_distinct_meals": 49,
}.items():
    name = f"data/cobblemon/species_features/{feature}.json"
    definition = json.loads(embedded[name])
    assert definition["max"] == previous_maximum and definition["min"] == 0 and definition["default"] == 0
    definition["max"] = 2_000_000_000
    embedded[name] = (json.dumps(definition, indent=2) + "\n").encode()
    adjusted_resources.append(name)
merged_languages = []
for name, payload in embedded.items():
    if name in entries and entries[name] != payload:
        assert name == "assets/mawonna/lang/en_us.json", "Unexpected custom content collision: " + name
        original_language = json.loads(entries[name])
        pack_language = json.loads(payload)
        assert isinstance(original_language, dict) and isinstance(pack_language, dict)
        for key in original_language.keys() & pack_language.keys():
            assert original_language[key] == pack_language[key], "Conflicting translation: " + key
        entries[name] = (json.dumps(original_language | pack_language, ensure_ascii=False, indent=2) + "\n").encode()
        merged_languages.append(name)
    else:
        entries[name] = payload
language_name = "assets/mawonna/lang/en_us.json"
language = json.loads(entries[language_name])
for key in (
    "cobblemon.species.covislight.desc",
    "cobblemon.species.corvislight.desc",
    "cobblemon.species.corvisquire.mawonna_evolution.desc",
    "cobblemon.species.mawile.mawonna_evolution.desc",
    "cobblemon.species.mawonna.desc",
    "cobblemon.species.mawonna_flower.desc",
    "cobblemon.species.mawonnaflower.desc",
    "cobblemon.species.fairysliggoo.desc",
    "cobblemon.species.sliggoo.fairy_evolution.desc",
    "cobblemon.species.inkyeon.desc",
    "cobblemon.species.sylveon.inkyeon_evolution.desc",
):
    assert key in language
    language[key] = "Evolution conditions and species settings are controlled by this server. Administrators can inspect current values in /quests settings."
language.update({
    "mawonna.message.repeat": "This dish is already remembered. Different qualifying meals: %s.",
    "mawonna.message.meal": "A meal to remember! Different qualifying meals: %s. Verified Dark-type defeats: %s.",
    "mawonna.message.requirements": "The current server evolution conditions are not met. Progress: %s different qualifying meals, %s verified Dark-type defeats.",
    "mawonna.message.married": "Your vow is sealed. Check your Pokemon's evolution option for the current server conditions.",
})
entries[language_name] = (json.dumps(language, ensure_ascii=False, indent=2) + "\n").encode()
adjusted_resources.append(language_name)
assert "dev/mawonna/MawonnaCompanion.class" in entries, "Existing evolution module was lost"
for name, payload in preserved.items():
    if name not in merged_languages and name not in adjusted_resources:
        assert entries[name] == payload, "Existing nonquest entry changed: " + name
for name, payload in embedded.items():
    if name not in merged_languages:
        assert entries[name] == payload, "Embedded resource changed: " + name
entries[MODS] = metadata.encode()
entries[MANIFEST] = (
    "Manifest-Version: 1.0\r\n"
    "FMLModType: MOD\r\n"
    "Implementation-Title: Unyielding\r\n"
    f"Implementation-Version: {release['version']}\r\n"
    f"Unyielding-Quests-Version: {release['quests']['version']}\r\n"
    f"Unyielding-Content-Revision: {release['pokemon_pack']['commit']}\r\n"
    "Unyielding-Currency-Bridge-Version: 1.0.0\r\n\r\n"
).encode()
assert not any("/quests/devtest/" in name or "/bundlesmoke/" in name or "/currencysmoke/" in name for name in entries), "Test helper leaked into release"
assert len([name for name in entries if name.startswith("assets/unyieldingquests/textures/quest_art/") and name.endswith(".png")]) == 923
bridge_names = [name for name in entries if name.startswith("com/uwustudio/cobblecurrency/") and name.endswith(".class")]
assert len(bridge_names) == 12
assert all(entries[name] == base[name] for name in bridge_names), "Currency implementation changed"
parser = "com/uwustudio/cobblecurrency/CobbleDollarsValueParser.class"
assert hashlib.sha256(entries[parser]).hexdigest() == "33c5a513e690ccfa47f8f6ad65be46aa49de0466f6722a6a2477f66934efe5f4", "Currency parser compatibility fix was lost"

output = ROOT / release["output"]
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, payload in sorted(entries.items()):
        entry = zipfile.ZipInfo(name, date_time=(2026, 11, 8, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o644 << 16
        archive.writestr(entry, payload)
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(entries)
    for name, payload in entries.items():
        assert archive.read(name) == payload, f"Packaged entry changed: {name}"

digest = hashlib.sha256(output.read_bytes()).hexdigest()
report = {
    "status": "passed", "version": release["version"], "quest_version": release["quests"]["version"],
    "sha256": digest, "source_commit": os.environ.get("GITHUB_SHA"),
    "cloud_run": f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
    "inputs": {key: release[key] for key in ("base", "quests", "pokemon_pack")},
    "production_module_count": 9, "preserved_nonquest_entries": len(preserved) - len(merged_languages),
    "rebuilt_quest_entries": len(updated), "preserved_currency_classes": len(bridge_names),
    "embedded_custom_entries": len(embedded), "embedded_species_count": len(species_names),
    "merged_language_resources": merged_languages, "standalone_pack_required": False,
    "companion_module_preserved": True,
    "audited_custom_resource_adjustments": adjusted_resources,
    "custom_progress_maximum": 2_000_000_000,
    "currency_parser_sha256": hashlib.sha256(entries[parser]).hexdigest(),
    "quest_pictures": 923, "external_dependencies_bundled": False,
    "metadata_changes": ["Outer release manifest", "Quest component version", "New quest mixins if declared"],
}
(output.parent / "assembly-report.json").write_text(json.dumps(report, indent=2) + "\n")
(output.parent / "SHA256SUMS.txt").write_text(f"{digest}  {output.name}\n")
print(json.dumps(report, indent=2))
