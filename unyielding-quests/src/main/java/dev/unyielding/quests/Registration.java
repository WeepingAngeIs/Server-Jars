package dev.unyielding.quests;

import dev.unyielding.quests.quest.QuestBookItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Registration {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UnyieldingQuests.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UnyieldingQuests.MODID);

    public static final DeferredHolder<Item, QuestBookItem> QUEST_BOOK =
            ITEMS.register("quest_book", () -> new QuestBookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, dev.unyielding.quests.loot.LootCrateItem> LOOT_CRATE =
            ITEMS.register("loot_crate", () -> new dev.unyielding.quests.loot.LootCrateItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.unyieldingquests"))
                    .icon(() -> new ItemStack(QUEST_BOOK.get()))
                    .displayItems((params, output) -> output.accept(QUEST_BOOK.get()))
                    .build());

    private Registration() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
