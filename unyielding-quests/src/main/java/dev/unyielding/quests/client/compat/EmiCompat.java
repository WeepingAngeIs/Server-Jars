package dev.unyielding.quests.client.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.unyielding.quests.client.QuestScreen;
import net.minecraft.world.item.ItemStack;

/** Lets EMI's recipe (R) and uses (U) keys work on items shown in the quest book. Only loaded when EMI is installed. */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    // EMI's automatic input hooks only cover container screens. QuestScreen is a plain Screen.
    public static boolean keyPressed(QuestScreen screen, ItemStack stack, int key, int scan) {
        if (!ready(stack)) return false;
        if (EmiConfig.viewRecipes.matchesKey(key, scan)) return open(screen, stack, false);
        if (EmiConfig.viewUses.matchesKey(key, scan)) return open(screen, stack, true);
        return false;
    }

    public static boolean mouseClicked(QuestScreen screen, ItemStack stack, int button) {
        if (!ready(stack)) return false;
        if (EmiConfig.viewRecipes.matchesMouse(button)) return open(screen, stack, false);
        if (EmiConfig.viewUses.matchesMouse(button)) return open(screen, stack, true);
        return false;
    }

    private static boolean ready(ItemStack stack) {
        return !stack.isEmpty() && EmiConfig.enabled && EmiReloadManager.isLoaded();
    }

    private static boolean open(QuestScreen screen, ItemStack stack, boolean uses) {
        if (uses) EmiApi.displayUses(EmiStack.of(stack));
        else EmiApi.displayRecipes(EmiStack.of(stack));
        // EMI creates an inventory parent for non-container screens. Restore the quest book
        // as the history entry so Escape/Back returns to the same quest and scroll position.
        if (Minecraft.getInstance().screen instanceof RecipeScreen) {
            EmiHistory.clear();
            EmiHistory.push(screen);
        }
        return true;
    }

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
