package dev.unyielding.jail;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** The existing Essentials failure helper, without an Essentials dependency. */
public final class JailText {
    private JailText() {}
    public static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
