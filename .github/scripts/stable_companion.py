"""Deterministic archive metadata only; entry contents must remain identical.

Raw file hashes remain the runtime trust boundary. Never certify a build just
because it carries an Unyielding name or version, or merely normalize at runtime.
"""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

def canonicalize(path):
    path = Path(path)
    with ZipFile(path) as source:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate companion entries")
        entries = {name: source.read(name) for name in names}
    target = path.with_suffix(path.suffix + '.canonical')
    try:
        with ZipFile(target, 'w', compression=ZIP_DEFLATED, compresslevel=9) as output:
            for name, data in sorted(entries.items()):
                info = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (0o40755 if name.endswith('/') else 0o100644) << 16
                info.compress_type = ZIP_DEFLATED
                output.writestr(info, data, compress_type=ZIP_DEFLATED, compresslevel=9)
        with ZipFile(target) as check:
            if {name: check.read(name) for name in check.namelist()} != entries:
                raise ValueError("Canonicalization changed companion content")
        target.replace(path)
    finally:
        target.unlink(missing_ok=True)
