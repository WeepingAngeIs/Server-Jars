"""Static application-class references from JVM class-file constant pools.

This is a packaging check, not proof that optional mods or reflective names exist.
It intentionally scans only our application's internal names, including names in
type descriptors and signatures, without treating arbitrary binary as text.
"""
import re
import struct

_APP_CLASS = re.compile(rb'dev/unyielding/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)+')


def application_references(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('not a JVM class file')
    count = struct.unpack_from('>H', data, 8)[0]
    pos, index, references = 10, 1, set()
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:  # CONSTANT_Utf8, also includes descriptors and signatures
            size = struct.unpack_from('>H', data, pos)[0]
            pos += 2
            value = data[pos:pos + size]
            if len(value) != size:
                raise ValueError('truncated JVM constant')
            pos += size
            references.update(match.decode('ascii') + '.class' for match in _APP_CLASS.findall(value))
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1  # long and double occupy two pool entries
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'unsupported JVM constant tag {tag}')
        if pos > len(data):
            raise ValueError('truncated JVM class file')
        index += 1
    return references


def missing_references(classes):
    """Map missing app-class paths to referring class-file paths."""
    missing = {}
    for name, data in classes.items():
        for dependency in application_references(data) - classes.keys():
            missing.setdefault(dependency, set()).add(name)
    return missing


def complete_missing_classes(classes, available, read, forbidden_prefix):
    """Add referenced missing classes without replacing existing entries."""
    additions = {}
    while missing := missing_references(classes):
        for name, referrers in sorted(missing.items()):
            if name not in available or name.startswith(forbidden_prefix):
                raise AssertionError(f'Unresolved application class {name}, referenced by {sorted(referrers)}')
            root = name[:-6].split('$', 1)[0]
            family = [n for n in available if n == root + '.class' or n.startswith(root + '$') and n.endswith('.class')]
            for member in family:
                if member not in classes:
                    additions[member] = classes[member] = read(member)
    return additions
