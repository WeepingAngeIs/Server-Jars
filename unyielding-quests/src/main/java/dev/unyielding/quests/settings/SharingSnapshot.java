package dev.unyielding.quests.settings;

import java.util.function.Consumer;
import java.util.List;

/** Carries one observed value through reconciliation, even if configuration changes meanwhile. */
public final class SharingSnapshot {
    private SharingSnapshot() {}
    public static void apply(boolean captured, Consumer<Boolean> reconcile) { reconcile.accept(captured); }
    public static boolean needsPublish(List<Boolean> observed, List<Boolean> captured, double observedScale,
                                       double capturedScale, boolean sharingMatches) {
        return !observed.equals(captured) || Double.compare(observedScale, capturedScale) != 0 || !sharingMatches;
    }
}
