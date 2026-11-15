package dev.unyielding.quests.spin;

/** Calendar-day claims with a missed-day grace; all times come from the server. */
public final class DailyRewardPolicy {
    static final long MIN_CLAIM_MILLIS = 20L * 60 * 60 * 1000;
    private DailyRewardPolicy() {}

    public static boolean eligible(long lastDay, long lastMillis, long day, long now) {
        return day > lastDay && (lastMillis <= 0 || (now >= lastMillis && now - lastMillis >= MIN_CLAIM_MILLIS));
    }

    public static long nextStreak(long lastDay, long streak, long day, int graceDays) {
        return streak > 0 && day > lastDay && day - lastDay <= 1L + graceDays ? Math.addExact(streak, 1) : 1;
    }
}
