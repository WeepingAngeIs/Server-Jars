package dev.unyielding.quests.spin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DailyDebitHistoryTest {
    @Test void legacyMissingHistoryIsValid() {
        assertTrue(DailyLogin.Data.load(new CompoundTag(), null).hasValidDebitHistory());
    }
    @Test void malformedHistoryIsPreservedForRecovery() {
        CompoundTag input = new CompoundTag(); input.putString("appliedDebits", "corrupt");
        DailyLogin.Data data = DailyLogin.Data.load(input, null);
        assertFalse(data.hasValidDebitHistory());
        assertEquals(input.get("appliedDebits"), data.save(new CompoundTag(), null).get("appliedDebits"));
    }
    @Test void wrongElementTypeAndDuplicateIdsFailClosed() {
        CompoundTag input = new CompoundTag(); ListTag wrong = new ListTag(); wrong.add(StringTag.valueOf("wrong"));
        input.put("appliedDebits", wrong);
        assertFalse(DailyLogin.Data.load(input, null).hasValidDebitHistory());
        ListTag duplicate = new ListTag(); CompoundTag entry = new CompoundTag(); entry.putUUID("id", UUID.randomUUID());
        duplicate.add(entry); duplicate.add(entry.copy()); input.put("appliedDebits", duplicate);
        assertFalse(DailyLogin.Data.load(input, null).hasValidDebitHistory());
    }
}
