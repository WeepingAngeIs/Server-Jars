package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.essentials.DestinationPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** Read-only list; clicks invoke the server's existing home or warp command. */
public final class DestinationScreen extends FormScreen {
    private final CompoundTag data;
    private final List<Integer> iconY = new ArrayList<>();
    private final long received = System.nanoTime();
    private long lastSecond = -1;
    private DestinationScreen(CompoundTag data) { super(null, data.getString("kind").equals("home") ? "Your homes" : "Warps"); this.data = data; }
    public static void receive(CompoundTag data) { Minecraft.getInstance().setScreen(new DestinationScreen(data)); }
    @Override protected void build() {
        iconY.clear();
        String kind = data.getString("kind");
        long remain = Math.max(0L, data.getLong("remaining") - Math.max(0L, (System.nanoTime()-received)/1_000_000_000L));
        note(data.getBoolean("exempt") ? "Cooldown: admin exempt" : "Cooldown: " + data.getInt("cooldown") + " seconds; " + (remain > 0 ? "wait " + remain + " seconds" : "ready"));
        if (!data.getBoolean("allowed")) note("Teleport command unavailable for you.");
        if (data.getInt("omitted") > 0) {
            note(data.getInt("omitted") + " destinations omitted from this list.");
            note("Use /" + kind + " <name> to access them.");
        }
        gap(5);
        for (Tag tag : data.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)tag;
            String name = row.getString("name");
            String dimension = row.getString("dimension");
            iconY.add(cy + 2);
            String command = DestinationPolicy.command(kind, name);
            var b = button(left()+20, innerWidth()-20, font.plainSubstrByWidth(name, innerWidth()-36), null, Ui.Style.NORMAL, () -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null && command != null) connection.sendCommand(command);
                Minecraft.getInstance().setScreen(null);
            });
            b.active = data.getBoolean("allowed") && command != null;
            gap(22);
            note(font.plainSubstrByWidth("Dimension: " + dimension, innerWidth())); gap(7);
        }
    }
    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        super.render(g,x,y,delta);
        g.enableScissor(px,top,px+pw,bottom);
        ItemStack icon = new ItemStack(data.getString("kind").equals("home") ? Items.RED_BED : Items.ENDER_PEARL);
        for (int rowY : iconY) if (rowY + 16 >= top && rowY < bottom) g.renderItem(icon,left()+1,rowY);
        g.disableScissor();
    }
    @Override public void tick() {
        super.tick();
        long second = Math.max(0L, (System.nanoTime()-received)/1_000_000_000L);
        if (second != lastSecond && second <= data.getLong("remaining")) { lastSecond = second; relayout(); }
    }
    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }
}
