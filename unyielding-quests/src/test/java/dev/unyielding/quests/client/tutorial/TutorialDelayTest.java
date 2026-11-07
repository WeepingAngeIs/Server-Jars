package dev.unyielding.quests.client.tutorial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialDelayTest {
    @Test
    void firstJoinWaitsForGraceAndContinuousClearScreen() {
        TutorialDelay delay = new TutorialDelay();
        delay.request(true);
        for (int tick = 0; tick < 99; tick++) assertFalse(delay.tick(true));
        assertTrue(delay.tick(true));
        assertFalse(delay.tick(true), "A guide opens only once per request");
    }

    @Test
    void originOrOtherMenuResetsTheQuietIntervalEvenAfterLongJoin() {
        TutorialDelay delay = new TutorialDelay();
        delay.request(true);
        for (int tick = 0; tick < 200; tick++) assertFalse(delay.tick(false));
        for (int tick = 0; tick < 39; tick++) assertFalse(delay.tick(true));
        assertFalse(delay.tick(false));
        for (int tick = 0; tick < 39; tick++) assertFalse(delay.tick(true));
        assertTrue(delay.tick(true));
    }

    @Test
    void disconnectCancelsAndManualReopenStillWaitsForOtherMenus() {
        TutorialDelay delay = new TutorialDelay();
        delay.request(true);
        for (int tick = 0; tick < 20; tick++) delay.tick(true);
        delay.clear();
        for (int tick = 0; tick < 200; tick++) assertFalse(delay.tick(true));
        delay.request(false);
        for (int tick = 0; tick < 39; tick++) assertFalse(delay.tick(true));
        assertTrue(delay.tick(true));
    }
}
