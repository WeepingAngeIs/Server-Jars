package dev.unyielding.quests.client.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.unyielding.quests.client.QuestScreen;
import net.minecraft.world.item.ItemStack;

/** Lets EMI's recipe (R) and uses (U) keys work on items shown in the quest book. Only loaded when EMI is installed. */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // keep EMI's side panels off the Premier Ball / Teams buttons on the inventory screens
        registry.addGenericExclusionArea((screen, consumer) -> {
            int[] a = dev.unyielding.quests.client.InvButtons.area(screen);
            if (a != null) consumer.accept(new dev.emi.emi.api.widget.Bounds(a[0] - 1, a[1] - 1, a[2] + 2, a[3] + 2));
        });
        registry.addStackProvider(QuestScreen.class, (screen, x, y) -> {
            ItemStack s = screen.stackAt(x, y);
            return s.isEmpty() ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(s));
        });
    }
}
