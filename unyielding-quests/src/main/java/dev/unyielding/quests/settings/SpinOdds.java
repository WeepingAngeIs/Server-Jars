package dev.unyielding.quests.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.Arrays;

/** Validation shared by the five-tier odds editor and the server. */
public final class SpinOdds {
    public static final int TIER_COUNT = 5;
    public static final int MAX_WEIGHT = 1_000_000;

    private SpinOdds() { }

    public static void validate(int[] weights) {
        validate(weights, true);
    }

    private static void validate(int[] weights, boolean requirePositive) {
        if (weights == null || weights.length != TIER_COUNT)
            throw new IllegalArgumentException("Enter all five prize chances.");
        int total = 0;
        for (int weight : weights) {
            if (weight < 0 || weight > MAX_WEIGHT)
                throw new IllegalArgumentException("Each prize weight must be between 0 and " + MAX_WEIGHT + ".");
            total += weight;
        }
        if (requirePositive && total == 0)
            throw new IllegalArgumentException("At least one prize chance must be greater than zero.");
    }

    public static int[] parse(String json) {
        return parse(json, true);
    }

    private static int[] parse(String text, boolean requirePositive) {
        JsonElement json = JsonParser.parseString(text);
        if (!json.isJsonArray() || json.getAsJsonArray().size() != TIER_COUNT)
            throw new IllegalArgumentException("Enter all five prize chances.");
        int[] weights = new int[TIER_COUNT];
        for (int i = 0; i < TIER_COUNT; i++) {
            JsonElement value = json.getAsJsonArray().get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Prize weights must be whole numbers.");
            try {
                weights[i] = value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("Prize weights must be whole numbers within the allowed range.", invalid);
            }
        }
        validate(weights, requirePositive);
        return weights;
    }

    public static void requireUnchanged(int[] current, String expected) {
        // Old files can contain all-zero weights; admins must still be able to repair them.
        if (!Arrays.equals(current, parse(expected, false)))
            throw new IllegalArgumentException("These chances changed. Reload them before saving.");
    }
}
