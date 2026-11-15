package dev.unyielding.quests.settings;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Human-friendly input only for settings explicitly known to be in seconds. */
public final class DurationInput {
    private static final Set<String> SECONDS = Set.of("essentials.home.cooldown", "essentials.back.cooldown", "essentials.rtp.cooldown", "essentials.cooldowns.spawn", "essentials.cooldowns.playerspawn", "essentials.cooldowns.warp", "essentials.cooldowns.tpa");
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([hms])", Pattern.CASE_INSENSITIVE);
    private DurationInput() {}
    public static boolean applies(String key) { return SECONDS.contains(key); }
    public static int seconds(String text) {
        String input = text.trim();
        if (input.matches("\\d+")) return Integer.parseInt(input);
        Matcher matcher = PART.matcher(input);
        int offset = 0;
        long total = 0;
        while (matcher.find()) {
            if (!input.substring(offset, matcher.start()).isBlank()) throw new IllegalArgumentException("Invalid duration");
            long part = Long.parseLong(matcher.group(1));
            long multiplier = switch (matcher.group(2).toLowerCase(java.util.Locale.ROOT)) { case "h" -> 3600; case "m" -> 60; default -> 1; };
            total = Math.addExact(total, Math.multiplyExact(part, multiplier));
            offset = matcher.end();
        }
        if (offset == 0 || !input.substring(offset).isBlank() || total > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid duration");
        return (int)total;
    }
    public static String format(int seconds) {
        int hours = seconds / 3600, minutes = (seconds % 3600) / 60, remaining = seconds % 60;
        return (hours > 0 ? hours + "h " : "") + (minutes > 0 ? minutes + "m " : "") + remaining + "s";
    }
}
