import unittest
from jail_split import MIXINS, RESOURCES, is_jail_entry, relocated_source

class JailSplitTests(unittest.TestCase):
    def test_exact_jail_members(self):
        for member in RESOURCES:
            self.assertTrue(is_jail_entry(member))
        for mixin in MIXINS:
            self.assertTrue(is_jail_entry("dev/unyielding/quests/mixin/" + mixin + ".class"))
            self.assertTrue(is_jail_entry("dev/unyielding/quests/mixin/" + mixin + "$Inner.class"))
        self.assertTrue(is_jail_entry("dev/unyielding/quests/jail/JailData.class"))
        self.assertTrue(is_jail_entry("dev/unyielding/jail/MinecraftJail.class"))

    def test_unrelated_features_are_not_removed(self):
        for member in ("dev/unyielding/quests/quest/QuestManager.class",
                       "dev/unyielding/quests/spin/RewardJournal.class",
                       "dev/unyielding/quests/mixin/PokemonProgressRequirementMixin.class",
                       "dev/unyielding/quests/essentials/Essentials.class",
                       "assets/unyieldingquests/pokemon.mixins.json",
                       "data/unyieldingquests/advancement/other.json"):
            self.assertFalse(is_jail_entry(member), member)

    def test_persisted_ids_untouched(self):
        text = '"unyieldingquests" "unyielding_jail" "custody-interface-v1" "integrated-jail-v1" "uq_integrated_native_origin"'
        self.assertEqual(text, relocated_source(text))

    def test_only_ownership_references_change(self):
        text = 'package dev.unyielding.quests.jail; @EventBusSubscriber(modid="unyieldingquests") getModFileById("unyieldingquests")'
        self.assertEqual('package dev.unyielding.jail; @EventBusSubscriber(modid=MinecraftJail.MODID) getModFileById(MinecraftJail.MODID)', relocated_source(text))

if __name__ == '__main__':
    unittest.main()
