package dev.unyielding.quests.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpinOddsTest {
    @Test
    void rareTiersCanBeDisabledWithoutDisablingItemPrizes() {
        int[] weights = SpinOdds.parse("[600,400,0,0,0]");
        assertArrayEquals(new int[]{600,400,0,0,0}, weights);
        assertDoesNotThrow(() -> SpinOdds.validate(weights));
    }

    @Test
    void supportsPreciseSmallChancesAndMaximumWeights() {
        assertArrayEquals(new int[]{600000,399997,1,1,1}, SpinOdds.parse("[600000,399997,1,1,1]"));
        assertDoesNotThrow(() -> SpinOdds.validate(new int[]{1000000,1000000,1000000,1000000,1000000}));
    }

    @Test
    void rejectsMalformedIncompleteOrNonIntegerGroups() {
        for (String json : List.of("{}", "null", "[]", "[1,2,3,4]", "[1,2,3,4,5,6]",
                "[1,2,3,4,\"5\"]", "[1,2,3,4,true]", "[1,2,3,4,null]", "[1,2,3,4,[]]",
                "[1,2,3,4,0.1]", "[1,2,3,4,2147483648]")) {
            assertThrows(RuntimeException.class, () -> SpinOdds.parse(json), json);
        }
    }

    @Test
    void rejectsZeroNegativeAndOutOfRangeGroups() {
        for (int[] weights : new int[][]{new int[5], {-1,1,1,1,1}, {1000001,1,1,1,1}, {1,2,3,4}})
            assertThrows(IllegalArgumentException.class, () -> SpinOdds.validate(weights));
        assertThrows(IllegalArgumentException.class, () -> SpinOdds.validate(null));
    }

    @Test
    void rejectsStaleSnapshotsEvenWhenTheNormalizedOddsAreEqual() {
        int[] current = {600,397,1,1,1};
        assertDoesNotThrow(() -> SpinOdds.requireUnchanged(current, "[ 600, 397, 1, 1, 1 ]"));
        assertThrows(IllegalArgumentException.class,
                () -> SpinOdds.requireUnchanged(current, "[1200,794,2,2,2]"));
        assertThrows(IllegalArgumentException.class,
                () -> SpinOdds.requireUnchanged(current, "[600,397,1,1,2]"));
    }

    @Test
    void existingAllZeroConfigCanBeRepaired() {
        assertDoesNotThrow(() -> SpinOdds.requireUnchanged(new int[5], "[0,0,0,0,0]"));
        assertThrows(IllegalArgumentException.class, () -> SpinOdds.parse("[0,0,0,0,0]"));
        assertArrayEquals(new int[]{1,0,0,0,0}, SpinOdds.parse("[1,0,0,0,0]"));
    }
}
