import struct
import unittest

from class_dependencies import application_references, complete_missing_classes, missing_references


def fixture(*constants):
    """Minimal class-file header and UTF-8 constant pool for parser tests."""
    entries = [b'\x01' + struct.pack('>H', len(value)) + value for value in constants]
    return b'\xca\xfe\xba\xbe\x00\x00\x00\x41' + struct.pack('>H', len(entries) + 1) + b''.join(entries)


class ClassDependencyTests(unittest.TestCase):
    def test_descriptors_and_nested_classes(self):
        data = fixture(b'(Ldev/unyielding/quests/quest/QuestRewardEpochs;)Ldev/unyielding/quests/spin/RewardCredit$Part;')
        self.assertEqual(application_references(data), {
            'dev/unyielding/quests/quest/QuestRewardEpochs.class',
            'dev/unyielding/quests/spin/RewardCredit$Part.class',
        })

    def test_adds_recursive_and_nested_classes_without_replacing_existing(self):
        entry = 'dev/unyielding/quests/Main.class'
        outer = 'dev/unyielding/quests/Missing.class'
        inner = 'dev/unyielding/quests/Missing$Part.class'
        tail = 'dev/unyielding/quests/Another.class'
        classes = {entry: fixture(b'dev/unyielding/quests/Missing'), tail: fixture(b'old')}
        component = {outer: fixture(b'dev/unyielding/quests/Another'), inner: fixture(b'inner'), tail: fixture(b'new')}
        added = complete_missing_classes(classes, set(component), component.__getitem__, 'dev/unyielding/quests/devtest/')
        self.assertEqual(set(added), {outer, inner})
        self.assertEqual(classes[tail], fixture(b'old'))
        self.assertFalse(missing_references(classes))

    def test_rejects_unavailable_or_test_only_dependencies(self):
        name = 'dev/unyielding/quests/devtest/Test.class'
        classes = {'dev/unyielding/quests/Main.class': fixture(name[:-6].encode())}
        with self.assertRaisesRegex(AssertionError, 'Unresolved application class'):
            complete_missing_classes(dict(classes), set(), lambda _: b'', 'dev/unyielding/quests/devtest/')
        with self.assertRaisesRegex(AssertionError, 'Unresolved application class'):
            complete_missing_classes(dict(classes), {name}, lambda _: fixture(), 'dev/unyielding/quests/devtest/')

    def test_rejects_invalid_class(self):
        with self.assertRaises(ValueError):
            application_references(b'not a class')


if __name__ == '__main__':
    unittest.main()
