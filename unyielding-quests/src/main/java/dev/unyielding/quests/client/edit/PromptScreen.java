package dev.unyielding.quests.client.edit;

import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/** One text field with Save/Cancel (headings, quick renames). */
public class PromptScreen extends FormScreen {
    private final String label, hint;
    private String value;
    private final Consumer<String> onSave;

    public PromptScreen(Screen parent, String title, String label, String hint, String initial, Consumer<String> onSave) {
        super(parent, title);
        this.label = label;
        this.hint = hint;
        this.value = initial == null ? "" : initial;
        this.onSave = onSave;
    }

    @Override
    protected int panelWidth() {
        return Math.min(340, width - 16);
    }

    @Override
    protected int panelHeight() {
        return 104;
    }

    @Override
    protected void build() {
        int x = labelled(label);
        field("v", x, right() - x, value, hint, s -> value = s);
        gap(24);
    }

    @Override
    protected void save() {
        minecraft.setScreen(parent);
        onSave.accept(value);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
