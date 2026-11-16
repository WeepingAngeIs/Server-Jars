#!/usr/bin/env python3
"""Read-only archive inventory. It NEVER launches Minecraft, downloads mods or edits saves.

Input archives remain unopened for writing. Reports deliberately exclude player names,
addresses, tokens, server.properties content and individual save filenames.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import stat
import tomllib
import zipfile

MAX_TOTAL = 32 * 1024**3
MAX_FILE = 512 * 1024**2
MAX_ENTRIES = 200_000
BASELINE = '4e3667a149d4e530bddb5326ee739ad386fe6a47'

class UnsafeArchive(ValueError):
    pass

def digest_file(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def checked_entries(archive: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    entries = archive.infolist()
    if len(entries) > MAX_ENTRIES:
        raise UnsafeArchive('Too many entries; no archive was extracted')
    seen: set[str] = set()
    total = 0
    for item in entries:
        name = item.filename
        parts = PurePosixPath(name).parts
        if not name or '\\' in name or name.startswith('/') or any(p in ('..', '.') for p in name.split('/')) or any(':' in p for p in parts):
            raise UnsafeArchive('Unsafe archive path; no archive was extracted')
        normalized = name.rstrip('/').casefold()
        if normalized in seen:
            raise UnsafeArchive('Duplicate or case-colliding archive path')
        seen.add(normalized)
        mode = item.external_attr >> 16
        if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0,stat.S_IFREG,stat.S_IFDIR)):
            raise UnsafeArchive('Archive links or special files are not accepted')
        if item.flag_bits & 1:
            raise UnsafeArchive('Encrypted archive requires a separate safe export')
        total += item.file_size
        if total > MAX_TOTAL:
            raise UnsafeArchive('Archive exceeds the read-only inspection size limit')
    return entries

def bounded_read(archive: zipfile.ZipFile, item: zipfile.ZipInfo, limit: int) -> bytes:
    if item.file_size > limit:
        raise UnsafeArchive('Metadata or binary exceeds its inspection limit')
    with archive.open(item) as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit or len(raw) != item.file_size:
        raise UnsafeArchive('Archive size mismatch')
    return raw

def jar_info(raw: bytes, certificate: dict | None) -> dict:
    info = {'sha256':hashlib.sha256(raw).hexdigest(), 'sha512':hashlib.sha512(raw).hexdigest(), 'mods':[]}
    with zipfile.ZipFile(io.BytesIO(raw)) as jar:
        names = {x.filename:x for x in checked_entries(jar)}
        key = next((n for n in ('META-INF/neoforge.mods.toml','META-INF/mods.toml') if n in names), None)
        if key:
            metadata = tomllib.loads(bounded_read(jar,names[key],1024**2).decode('utf-8'))
            info['mods'] = [{'id':r.get('modId'), 'version':r.get('version')} for r in metadata.get('mods',[])]
        info['contains_legacy_jail'] = any(n.startswith('dev/unyielding/quests/jail/') and n.endswith('.class') for n in names)
        info['contains_split_jail'] = 'dev/unyielding/jail/MinecraftJail.class' in names
    if certificate is not None:
        info['listed_by_runtime_certificate'] = info['sha512'] in certificate.get('sha512',[])
        # Self-artifact handling in the runtime is NOT a reason to auto-trust an input JAR here.
    return info

def inspect(path: Path, role: str, certificate: dict | None = None) -> dict:
    path = path.resolve(strict=True)
    if not path.is_file():
        raise UnsafeArchive('Input must be an archive file, not an installed directory')
    before = digest_file(path)
    report: dict = {'role':role,'archive_sha256':before,'entry_count':0,'jar_count':0,'save_present':False,'jars':[],'blockers':[]}
    with zipfile.ZipFile(path) as archive:
        entries = checked_entries(archive)
        report['entry_count'] = len(entries)
        files = {i.filename:i for i in entries if not i.is_dir()}
        levels = [n for n in files if PurePosixPath(n).name == 'level.dat']
        report['save_present'] = bool(levels)
        report['world_roots'] = len(levels)
        report['player_data_present'] = any('playerdata' in PurePosixPath(n).parts for n in files)
        report['custody_files_present'] = any('integrated-jail-v1' in n or 'jail_custody' in n or 'jail-custody' in n for n in files)
        report['database_like_files_present'] = any(n.lower().endswith(('.sql','.bson','.sqlite','.db')) for n in files)
        report['external_database_completeness'] = 'UNVERIFIED'
        manifests = [n for n in files if PurePosixPath(n).name == 'manifest.json']
        if 'manifest.json' in manifests:
            manifest=json.loads(bounded_read(archive,files['manifest.json'],8*1024**2))
            if manifest.get('manifestType') == 'minecraftModpack':
                report['curseforge_export'] = True
                report['pack_version'] = manifest.get('version')
                report['minecraft'] = manifest.get('minecraft',{}).get('version')
                report['loaders'] = [m.get('id') for m in manifest.get('minecraft',{}).get('modLoaders',[])]
                report['manifest_file_count'] = len(manifest.get('files',[]))
                if certificate and report['minecraft'] != certificate.get('minecraft'):
                    report['blockers'].append('Minecraft version differs from reviewed runtime')
                expected = 'neoforge-' + str(certificate.get('neoforge')) if certificate else None
                if expected and expected not in report['loaders']:
                    report['blockers'].append('NeoForge version differs from reviewed runtime: expected '+expected)
        for name,item in files.items():
            if name.lower().endswith('.jar'):
                row=jar_info(bounded_read(archive,item,MAX_FILE),certificate)
                row['file']=PurePosixPath(name).name
                report['jars'].append(row)
        report['jar_count']=len(report['jars'])
        if role == 'pack' and not report['jars']:
            report['blockers'].append('No exact installed mod binaries; manifest references are not a complete server runtime')
        if role == 'save' and len(levels) != 1:
            report['blockers'].append('Need exactly one complete old world copy with level.dat')
        if role == 'save' and not report['player_data_present']:
            report['blockers'].append('Old playerdata absent; cannot verify player custody migration')
        if any(r['contains_legacy_jail'] for r in report['jars']) and any(r['contains_split_jail'] for r in report['jars']):
            report['blockers'].append('Both legacy and split jail owners present; never launch this mixture')
    report['source_unchanged'] = digest_file(path) == before
    if not report['source_unchanged']:
        raise UnsafeArchive('Input changed during inspection')
    report['launch_authorized'] = False
    report['runtime_test_status'] = 'NOT_RUN'
    report['migration_test_status'] = 'NOT_RUN'
    return report

def plan(pack: dict | None, save: dict | None) -> dict:
    blockers=[]
    if pack is None: blockers.append('Exact current server pack not supplied')
    else: blockers.extend(pack['blockers'])
    if save is None: blockers.append('Complete pre-split save copy not supplied')
    else: blockers.extend(save['blockers'])
    blockers.extend(['Exact old integrated JAR and matching old runtime must be identified','External Pokemon/economy stores must be included or proven absent','Private isolated execution environment must be approved; never publish a player save or secrets to the public repository'])
    return {'baseline':BASELINE,'input_reports':[r for r in (pack,save) if r is not None],'blockers':blockers,
            'launch_authorized':False,'actual_pack_compatibility':'NOT_RUN','actual_save_migration':'NOT_RUN',
            'migration_cases':['old-runtime baseline from copy','empty-jail split copy startup/restart','occupied-jail fingerprint mismatch remains quarantined','inventory Pokemon UUID and wallet conservation','disconnect/death/restart at each proved handoff','restore-collision refusal with no overwrite or duplicate grant']}

def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack',type=Path);parser.add_argument('--save',type=Path)
    parser.add_argument('--certificate',type=Path);parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    inputs=[p.resolve() for p in (args.pack,args.save,args.certificate) if p]
    if args.report.resolve() in inputs or args.report.exists():
        parser.error('Report must be a new file, never overwrite an input or prior report')
    certificate=json.loads(args.certificate.read_text()) if args.certificate else None
    result=plan(inspect(args.pack,'pack',certificate) if args.pack else None,inspect(args.save,'save',certificate) if args.save else None)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    with args.report.open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print('Read-only inspection complete. Compatibility and migration have NOT run. No launch authorized.')

if __name__=='__main__':main()
