"""Extraction identity rules. Legacy save, resource and permission IDs are retained."""
import re

BASE_COMMIT = "dc3699ab04e775bd772c38b9ff24646e0791f81a"
OLD_PACKAGE = "dev/unyielding/quests/jail/"
NEW_PACKAGE = "dev/unyielding/jail/"
MIXINS = ("JailMovementMixin", "CustodyPacketMixin", "JailEquipmentAccess", "JailXpDropMixin")
RESOURCES = (
    "data/unyieldingquests/advancement/jail_escape.json",
    "assets/unyieldingquests/blockstates/custody_vault.json",
    "assets/unyieldingquests/models/block/custody_vault.json",
    "assets/unyieldingquests/jail-certified-runtime.json",
)


def is_jail_entry(name):
    if name.startswith((OLD_PACKAGE, NEW_PACKAGE)) or name in RESOURCES or name == "minecraftjail.mixins.json":
        return True
    for mixin in MIXINS:
        root = "dev/unyielding/quests/mixin/" + mixin
        if name == root + ".class" or name.startswith(root + "$") and name.endswith(".class"):
            return True
    return False


def relocated_source(text):
    # Deliberately not a blanket replacement of unyieldingquests: changing persisted
    # identities would reinterpret custody, vault blocks or advancement progress.
    for old, new in (
        ("dev.unyielding.quests.jail", "dev.unyielding.jail"),
        ("dev.unyielding.quests.mixin", "dev.unyielding.jail.mixin"),
        ("dev.unyielding.quests.essentials.Loc", "dev.unyielding.jail.Loc"),
        ("dev.unyielding.quests.essentials.Essentials", "dev.unyielding.jail.JailText"),
        ("dev.unyielding.quests.UnyieldingQuests", "dev.unyielding.jail.MinecraftJail"),
        ("dev.unyielding.quests.Registration", "dev.unyielding.jail.JailRegistration"),
        ("dev.unyielding.quests.Config", "dev.unyielding.jail.JailConfig"),
        ("dev.unyielding.quests.devtest", "dev.unyielding.jail.devtest"),
    ):
        text = text.replace(old, new)
    for old, new in (("UnyieldingQuests", "MinecraftJail"), ("Essentials", "JailText")):
        text = re.sub(r"\b" + old + r"\b", new, text)
    text = re.sub(r"\bConfig\.JAIL_CERTIFIED_ADMISSIONS", "JailConfig.JAIL_CERTIFIED_ADMISSIONS", text)
    text = text.replace('@EventBusSubscriber(modid="unyieldingquests")', '@EventBusSubscriber(modid=MinecraftJail.MODID)')
    return text.replace('getModFileById("unyieldingquests")', 'getModFileById(MinecraftJail.MODID)')
