package dev.unyielding.quests.settings;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;

/** Labels for registered UwU settings. The owner supplies values, bounds and validation. */
final class WeaponSettingsPresentation {
    // UwU prefixes registered weapon descriptions with the weapon's in-game name.
    private static final Pattern WEAPON_DESCRIPTION = Pattern.compile(
            "^(.+?) (?:fully charged main-hand|full-draw arrow|direct ability hit|direct or delayed echo"
                    + "|cast cooldown|cast or charged throw|crescent attack cooldown|physical throw cooldown)\\b");

    private WeaponSettingsPresentation() {}

    static void decorate(String modId, List<String> path, CompoundTag row) {
        if (!"uwudimension".equals(modId)) return;
        if (path.size() == 3 && "weapons".equals(path.getFirst())) {
            weaponSetting(path.get(1), path.get(2), row);
            return;
        }
        switch (String.join(".", path)) {
            case "cupidBowCharmSeconds" -> {
                label(row, "Cupid", "Enemy charm duration");
                seconds(row);
                if (!row.contains("note")) row.putString("note", "Applies when a Cupid arrow hits. Active charms keep their original end time.");
            }
            case "cupidWishes.enabled" -> label(row, "Cupid", "Enable Cupid wishes");
            case "cupidWishes.playerWitnesses" -> label(row, "Cupid", "Allow player witnesses");
            case "cupidWishes.pairRadius" -> {
                label(row, "Cupid", "Maximum distance between wish targets");
                row.putString("unit", "blocks");
            }
            case "cupidWishes.permissionLevel" -> label(row, "Cupid", "Wish command permission level");
            case "bellHammerAreaMiningEnabled" -> label(row, "Bell Hammer", "Enable area mining");
            default -> { }
        }
    }

    private static void weaponSetting(String weapon, String key, CompoundTag row) {
        String title = switch (key) {
            case "baseHitDamage" -> "Base melee hit damage";
            case "abilityDamage" -> "Ability hit damage";
            case "throwDamage" -> "Thrown weapon damage";
            case "backstabMultiplier" -> "Melee backstab multiplier";
            case "castCooldownSeconds" -> "Cast cooldown";
            case "turnBackCooldownSeconds" -> "Turn Back cooldown";
            case "throwCooldownSeconds" -> "Throw cooldown";
            case "waveCooldownSeconds" -> "Crescent attack cooldown";
            case "spinCooldownSeconds" -> "Spin cooldown";
            case "heartSpinCooldownSeconds" -> "Heart Spin cooldown";
            case "spinDamage" -> "Spin damage per target";
            case "heartSpinDamage" -> "Heart Spin damage per target";
            case "blockCharmSeconds" -> "Charm duration after blocking";
            case "rechargeSeconds" -> "Shared revival cooldown";
            default -> null;
        };
        if (title == null) return;
        if ("cupid_bow".equals(weapon) && "castCooldownSeconds".equals(key)) title = "Shot cooldown";
        String group = switch (weapon) {
            case "cupid_bow" -> "Cupid";
            case "returnbloom_dirk" -> "Returnbloom Dirk";
            case "dawnpetal_axe" -> "Dawnpetal Axe";
            case "blossom_revival" -> "Returnbloom Dirk and Dawnpetal Axe";
            case "petalfold_shield" -> "Petalfold Shield";
            case "heart_launcher" -> "Heart Launcher";
            default -> weaponName(weapon, row.getString("note"));
        };
        label(row, group, title);
        if (key.endsWith("Seconds")) seconds(row);
        else if (key.endsWith("Damage")) row.putString("unit", "health points");
        else if ("backstabMultiplier".equals(key)) row.putString("unit", "×");
    }

    private static String weaponName(String id, String description) {
        var match = WEAPON_DESCRIPTION.matcher(description);
        if (match.find()) return match.group(1);
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return name.toString();
    }

    private static void seconds(CompoundTag row) {
        row.putString("unit", "seconds");
        row.putInt("unitsPerSecond", 1);
    }

    private static void label(CompoundTag row, String group, String title) {
        row.putString("group", group);
        row.putString("title", title);
    }
}
