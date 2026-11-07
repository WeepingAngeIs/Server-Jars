package dev.unyielding.quests.client.tutorial;

/** Requires both a joining grace period and an uninterrupted interval with no other menu. */
public final class TutorialDelay {
    private int age;
    private int quiet;
    private int grace;
    private boolean pending;

    public void request(boolean automatic) {
        age = 0;
        quiet = 0;
        grace = automatic ? 100 : 0;
        pending = true;
    }

    public boolean tick(boolean ready) {
        if (!pending) return false;
        age++;
        quiet = ready ? quiet + 1 : 0;
        if (age < grace || quiet < 40) return false;
        pending = false;
        return true;
    }

    public void clear() {
        pending = false;
        age = 0;
        quiet = 0;
    }
}
