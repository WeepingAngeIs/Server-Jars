package dev.unyielding.quests.client.ui;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player look settings (config/unyieldingquests-client.toml). */
public final class UiConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MENU_OPACITY;
    public static final ModConfigSpec.BooleanValue BLUR;
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    public static final ModConfigSpec.BooleanValue SHOW_HINTS;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.EnumValue<dev.unyielding.quests.client.ArtAnim.Motion> PICTURE_MOTION;
    public static final ModConfigSpec.EnumValue<InvButtons> INV_BUTTONS;
    public static final ModConfigSpec.IntValue INV_X;
    public static final ModConfigSpec.IntValue INV_Y;
    public static final ModConfigSpec.BooleanValue TEAMS_BUTTON;

    /** Where the Quest Book (Premier Ball) button sits on the inventory screen. */
    public enum InvButtons implements net.neoforged.neoforge.common.TranslatableEnum {
        TOP_LEFT, BESIDE_INVENTORY, OFF;

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable("unyieldingquests.inv_buttons." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("look");
        MENU_OPACITY = b.comment("How solid the quest book and party windows are. 1.0 = solid, 0.4 = very see-through.")
                .defineInRange("menu_opacity", 0.9, 0.3, 1.0);
        BLUR = b.comment("Blur the world behind the quest book and party screen.")
                .define("blur_background", true);
        ANIMATIONS = b.comment("Small animations (glowing quests, sliding panels).")
                .define("animations", true);
        SHOW_HINTS = b.comment("Show 'how to get it' hints under quest tasks.")
                .define("show_hints", true);
        SOUNDS = b.comment("Play small sounds when opening pages and claiming rewards.")
                .define("sounds", true);
        PICTURE_MOTION = b.comment("Moving quest and chapter pictures.",
                        "HOVER = a picture moves while you point at it (and on the open quest and the quest-complete popup).",
                        "ALWAYS = every picture moves all the time. OFF = still pictures.")
                .defineEnum("picture_motion", dev.unyielding.quests.client.ArtAnim.Motion.HOVER);
        b.pop();
        b.push("inventory");
        INV_BUTTONS = b.comment("The Premier Ball button that opens the quest book from your inventory.",
                        "TOP_LEFT = top-left corner of the screen. BESIDE_INVENTORY = just left of the inventory window. OFF = hidden (press J instead).")
                .defineEnum("quest_book_button", InvButtons.TOP_LEFT);
        INV_X = b.comment("Left edge of the button when it is in the top-left corner (pixels).").defineInRange("button_x", 2, 0, 4000);
        INV_Y = b.comment("Top edge of the button when it is in the top-left corner (pixels).").defineInRange("button_y", 2, 0, 4000);
        TEAMS_BUTTON = b.comment("Also show the Teams button next to it.").define("teams_button", true);
        b.pop();
        SPEC = b.build();
    }

    private UiConfig() {
    }

    public static float opacity() {
        try {
            return (float) (double) MENU_OPACITY.get();
        } catch (IllegalStateException e) {
            return 0.9f;
        }
    }

    public static boolean blur() {
        try {
            return BLUR.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean animations() {
        try {
            return ANIMATIONS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static dev.unyielding.quests.client.ArtAnim.Motion pictureMotion() {
        try {
            return PICTURE_MOTION.get();
        } catch (IllegalStateException e) {
            return dev.unyielding.quests.client.ArtAnim.Motion.HOVER;
        }
    }

    public static InvButtons invButtons() {
        try {
            return INV_BUTTONS.get();
        } catch (IllegalStateException e) {
            return InvButtons.TOP_LEFT;
        }
    }

    public static int invX() {
        try {
            return INV_X.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static int invY() {
        try {
            return INV_Y.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    public static boolean teamsButton() {
        try {
            return TEAMS_BUTTON.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean hints() {
        try {
            return SHOW_HINTS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean sounds() {
        try {
            return SOUNDS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
