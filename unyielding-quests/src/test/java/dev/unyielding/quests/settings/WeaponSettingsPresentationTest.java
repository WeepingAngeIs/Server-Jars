package dev.unyielding.quests.settings;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSettingsPresentationTest {
    @Test void labelsRegisteredWishSettingsWithoutChangingTheirOwnerValuesOrComments() {
        var row = new CompoundTag();
        row.putString("current", "8");
        row.putString("note", "Owner help");
        WeaponSettingsPresentation.decorate("uwudimension", List.of("cupidWishes", "pairRadius"), row);
        assertEquals("Cupid", row.getString("group"));
        assertEquals("Maximum distance between wish targets", row.getString("title"));
        assertEquals("blocks", row.getString("unit"));
        assertEquals("8", row.getString("current"));
        assertEquals("Owner help", row.getString("note"));
        assertFalse(row.contains("unitsPerSecond"));
    }

    @Test void unrelatedModsAndUnknownPathsRemainGeneric() {
        var row = new CompoundTag();
        WeaponSettingsPresentation.decorate("othermod", List.of("cupidWishes", "pairRadius"), row);
        WeaponSettingsPresentation.decorate("uwudimension", List.of("guardianFamily", "pairRadius"), row);
        WeaponSettingsPresentation.decorate("uwudimension", List.of("cupidWishes", "unrecognized"), row);
        assertTrue(row.isEmpty());
    }

    @Test void cupidCharmIsInWholeSecondsAndKeepsItsRegisteredRange() {
        var row = new CompoundTag();
        row.putDouble("max", 107374182);
        row.putString("current", "90");
        WeaponSettingsPresentation.decorate("uwudimension", List.of("cupidBowCharmSeconds"), row);
        assertEquals("Cupid", row.getString("group"));
        assertEquals("Enemy charm duration", row.getString("title"));
        assertEquals("seconds", row.getString("unit"));
        assertEquals(1, row.getInt("unitsPerSecond"));
        assertEquals(107374182D, row.getDouble("max"));
        assertEquals("90", row.getString("current"));
    }

    @Test void dirkDistinguishesMeleeThrownAndRearHits() {
        var melee = weapon("returnbloom_dirk", "baseHitDamage");
        var thrown = weapon("returnbloom_dirk", "throwDamage");
        var backstab = weapon("returnbloom_dirk", "backstabMultiplier");
        assertEquals("Returnbloom Dirk", melee.getString("group"));
        assertEquals("Base melee hit damage", melee.getString("title"));
        assertEquals("Thrown weapon damage", thrown.getString("title"));
        assertEquals("health points", thrown.getString("unit"));
        assertEquals("×", backstab.getString("unit"));
        assertFalse(melee.contains("unitsPerSecond"));
    }

    @Test void secondsKeepProviderPrecisionAndRevivalIsShared() {
        var row = new CompoundTag();
        row.putString("current", "0.05");
        WeaponSettingsPresentation.decorate("uwudimension", List.of("weapons", "returnbloom_dirk", "turnBackCooldownSeconds"), row);
        assertEquals("Turn Back cooldown", row.getString("title"));
        assertEquals(1, row.getInt("unitsPerSecond"));
        assertEquals("0.05", row.getString("current"));
        var revival = weapon("blossom_revival", "rechargeSeconds");
        assertEquals("Returnbloom Dirk and Dawnpetal Axe", revival.getString("group"));
        assertEquals("Shared revival cooldown", revival.getString("title"));
    }

    @Test void preservesProviderWeaponNameWhenItDiffersFromTheItemId() {
        var row = new CompoundTag();
        String note = "Twinfin Whip direct ability hit damage in health points.";
        row.putString("note", note);
        WeaponSettingsPresentation.decorate("uwudimension", List.of("weapons", "axolotl_whip", "abilityDamage"), row);
        assertEquals("Twinfin Whip", row.getString("group"));
        assertEquals(note, row.getString("note"));
        assertTrue(weapon("returnbloom_dirk", "unsupported").isEmpty());
    }

    private static CompoundTag weapon(String weapon, String key) {
        var row = new CompoundTag();
        WeaponSettingsPresentation.decorate("uwudimension", List.of("weapons", weapon, key), row);
        return row;
    }
}
