package dev.unyielding.quests.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** An alternate input for bounded weapon durations. Only the form's Save button submits the edit. */
final class WeaponTimeSlider extends AbstractSliderButton {
    private final double minimum, maximum;
    private final int units;
    private final EditBox field;
    private final boolean wholeNumber;

    WeaponTimeSlider(int x, int y, int width, double minimum, double maximum, int units, boolean wholeNumber, EditBox field) {
        super(x, y, width, 20, Component.literal("Seconds"), 0.0);
        this.minimum = minimum;
        this.maximum = maximum;
        this.units = units;
        this.field = field;
        this.wholeNumber = wholeNumber;
    }

    void follow(String text) {
        try {
            double raw = Double.parseDouble(text) * units;
            if (!Double.isFinite(raw)) return;
            value = Math.clamp((raw - minimum) / (maximum - minimum), 0.0, 1.0);
            updateMessage();
        } catch (NumberFormatException ignored) {
            // Let the user finish typing an empty or partial number.
        }
    }

    private String seconds() {
        double raw = Math.clamp(minimum + value * (maximum - minimum), minimum, maximum);
        if (wholeNumber) return ModSettingsView.shown(Long.toString(Math.round(raw)), units);
        var rounded = java.math.BigDecimal.valueOf(raw).setScale(2, java.math.RoundingMode.HALF_UP);
        return ModSettingsView.shown(rounded.stripTrailingZeros().toPlainString(), units);
    }

    @Override protected void updateMessage() {
        setMessage(Component.literal(seconds() + " seconds"));
    }

    @Override protected void applyValue() {
        field.setValue(seconds());
    }
}
