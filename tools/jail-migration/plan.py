#!/usr/bin/env python3
"""Read-only private-copy migration planning. No extraction, launch or migration.

Uses the certificate from an exactly pinned Jail binary, not a user-edited allowlist.
Missing inputs, unknown artifacts and ownership conflicts are reported as blockers.
Even a complete inventory NEVER authorizes runtime execution or custody recovery.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('jail_preflight', ROOT/'tools/jail-sandbox/preflight.py')
preflight = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(preflight)
BASELINE = '4e3667a149d4e530bddb5326ee739ad386fe6a47'
JAIL_SHA256 = 'd3145b1bc8f04c0370db30ff10e87158b6c3c88c0fde68cb0f226d3b73f79baf'
COMPANION_SHA256 = '61f6add6c68364b1c9dda4486948faf8d104c45ca2db2c40199de64a04ea38f7'
CERTIFICATE_PATH = 'assets/unyieldingquests/jail-certified-runtime.json'


def certificate_from_verified_jar(path: Path) -> dict:
    if preflight.digest_file(path) != JAIL_SHA256:
        raise ValueError('Certificate source is not the exact reviewed Jail JAR')
    with zipfile.ZipFile(path) as jar:
        entries = {i.filename: i for i in preflight.checked_entries(jar)}
        raw = preflight.bounded_read(jar, entries[CERTIFICATE_PATH], 1024*1024)
    if preflight.digest_file(path) != JAIL_SHA256:
        raise ValueError('Certificate source changed while reading')
    result = json.loads(raw)
    if result.get('minecraft') != '1.21.1' or result.get('neoforge') != '21.1.255':
        raise ValueError('Unexpected pinned certificate runtime')
    if not isinstance(result.get('sha512'), list) or not all(re.fullmatch('[0-9a-f]{128}', s) for s in result['sha512']):
        raise ValueError('Malformed certificate digest list')
    return result


def portable_paths(archive: zipfile.ZipFile) -> None:
    """Reject cross-platform aliases, including Windows reserved/trailing names."""
    reserved = {'con', 'prn', 'aux', 'nul'} | {f'{p}{i}' for p in ('com', 'lpt') for i in range(1,10)}
    entries = preflight.checked_entries(archive)
    file_paths = set()
    all_paths = []
    for item in entries:
        parts = item.filename.rstrip('/').split('/')
        for part in parts:
            if not part or part[-1] in '. ' or any(ord(c)<32 or c in '<>"|?*' for c in part) or part.split('.')[0].casefold() in reserved:
                raise preflight.UnsafeArchive('Nonportable or aliased archive path')
        key = '/'.join(parts).casefold(); all_paths.append(parts)
        if not item.is_dir(): file_paths.add(key)
    for parts in all_paths:
        if any('/'.join(parts[:i]).casefold() in file_paths for i in range(1,len(parts))):
            raise preflight.UnsafeArchive('A file is also used as a parent directory')


def inventory(path: Path, role: str, certificate: dict) -> dict:
    if role not in ('pack', 'old_runtime', 'save'):
        raise ValueError('Unknown input role')
    path = path.resolve(strict=True)
    before = preflight.digest_file(path)
    with zipfile.ZipFile(path) as archive:
        portable_paths(archive)
    original = preflight.inspect(path, 'save' if role=='save' else 'pack', certificate)
    # Keep reports free of local paths, individual save filenames and arbitrary pack names.
    report = {k: original[k] for k in ('archive_sha256','entry_count','jar_count','save_present','world_roots','player_data_present','custody_files_present','source_unchanged')}
    report.update(role=role, blockers=[], artifacts=[])
    if role == 'save':
        if original['world_roots'] != 1: report['blockers'].append('Exactly one world root with level.dat is required')
        if not original['player_data_present']: report['blockers'].append('Playerdata missing')
    else:
        if not original['jar_count']: report['blockers'].append('Exact mod binaries missing; a manifest is insufficient')
        if original.get('minecraft') and original['minecraft'] != certificate['minecraft']: report['blockers'].append('Minecraft metadata differs from the reviewed runtime')
        if original.get('loaders') and 'neoforge-'+certificate['neoforge'] not in original['loaders']:
            report['blockers'].append('Loader metadata differs from the reviewed split runtime')
    legacy = split = 0
    for row in original['jars']:
        legacy += bool(row['contains_legacy_jail']); split += bool(row['contains_split_jail'])
        approved = row['sha512'] in certificate['sha512'] or row['sha256'] == JAIL_SHA256
        report['artifacts'].append({'sha256':row['sha256'], 'sha512':row['sha512'], 'legacy_owner':row['contains_legacy_jail'], 'split_owner':row['contains_split_jail'], 'reviewed_bytes':approved})
        if role == 'pack' and not approved:
            report['blockers'].append('Unreviewed artifact '+row['sha256']+'; do not remove it or expand the certificate automatically')
    report['legacy_owner_count'] = legacy; report['split_owner_count'] = split
    if legacy and split: report['blockers'].append('Legacy and split Jail owners coexist')
    if legacy > 1 or split > 1: report['blockers'].append('Duplicate Jail owner binaries')
    if role == 'old_runtime' and (legacy != 1 or split != 0): report['blockers'].append('Old runtime must contain exactly one integrated Jail owner and no split Jail')
    if preflight.digest_file(path) != before or original['archive_sha256'] != before:
        raise preflight.UnsafeArchive('Input changed during planning')
    report['source_unchanged'] = True
    return report


def make_plan(reports: dict[str,dict], old_integrated_sha256: str | None = None) -> dict:
    blockers = []
    for role in ('pack','old_runtime','save'):
        if role not in reports: blockers.append('Missing private input: '+role)
        else:
            if reports[role].get('role') != role or reports[role].get('source_unchanged') is not True:
                raise ValueError('Mismatched or changed input evidence')
            blockers.extend(role+': '+s for s in reports[role]['blockers'])
    if old_integrated_sha256 is None:
        blockers.append('Exact old integrated JAR identity not independently supplied')
    elif not re.fullmatch('[0-9a-f]{64}', old_integrated_sha256):
        raise ValueError('Old JAR identity must be a complete SHA-256')
    elif 'old_runtime' in reports:
        matches = [r for r in reports['old_runtime']['artifacts'] if r['legacy_owner'] and r['sha256']==old_integrated_sha256]
        if len(matches)!=1: blockers.append('Old integrated JAR does not match the supplied identity')
    blockers.extend([
        'Consistent backup boundary and complete external Pokemon/economy stores still require verification',
        'Private isolated execution and network isolation must be established before any launch',
        'Full native resource inventory and custody receipt validation have not run'])
    cases = [
        ('old_control', 'Start a working copy with the exact old runtime; capture complete native state and stop/restart'),
        ('empty_custody', 'Separate split copy with no occupied legacy custody; compare unrelated data and restart'),
        ('occupied_legacy', 'Fingerprint mismatch and unresolved receipts must retain quarantine without rewritten evidence'),
        ('resource_lifecycle', 'Compare Pokemon UUIDs/order/full state and unique wallet ownership through release/death/disconnect/restart'),
        ('ambiguous_recovery', 'Changed destinations, absent providers, duplicate ownership or missing receipts must refuse replay'),
        ('interrupted_writes', 'Test abrupt process failure only after a supported transaction adapter exists')]
    return {'schema':1, 'production_baseline':BASELINE, 'input_reports':reports, 'blockers':blockers,
            'input_archives_unchanged':all(r.get('source_unchanged') is True for r in reports.values()) if reports else None,
            'launch_authorized':False, 'actual_pack_compatibility':'NOT_RUN', 'actual_save_migration':'NOT_RUN',
            'test_cases':[{'id':name,'status':'NOT_RUN','required_assertion':text} for name,text in cases],
            'prohibited_actions':['writing installed paths','connecting to live services','publishing save or player data','rewriting legacy fingerprints','auto-expanding certificates','deleting resources to satisfy tests']}


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    for flag in ('pack','old-runtime','save','jail-jar','report'): parser.add_argument('--'+flag, type=Path, required=flag in ('jail-jar','report'))
    parser.add_argument('--old-integrated-sha256')
    args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') == 'true':
        parser.error('Private input CLI is disabled on GitHub Actions; use only synthetic unit tests there')
    inputs=[p.resolve() for p in (args.pack,args.old_runtime,args.save,args.jail_jar) if p]
    if args.report.resolve() in inputs or args.report.exists() or args.report.is_symlink():
        parser.error('Report must be a new file, never an input or existing report')
    cert=certificate_from_verified_jar(args.jail_jar)
    reports={r:inventory(p,r,cert) for r,p in [('pack',args.pack),('old_runtime',args.old_runtime),('save',args.save)] if p}
    result=make_plan(reports,args.old_integrated_sha256)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    with args.report.open('x',encoding='utf-8') as stream: json.dump(result,stream,indent=2);stream.write('\n')
    print('Read-only plan written. No extraction, launch, compatibility pass or migration performed.')

if __name__=='__main__': main()
