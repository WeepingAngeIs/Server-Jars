package dev.unyielding.quests.essentials;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import java.util.function.BooleanSupplier;

/** Check live feature state at execution, including arguments and previously parsed commands. */
public final class CommandGate {
    private CommandGate() { }
    public static <S> CommandNode<S> guard(CommandNode<S> node, BooleanSupplier enabled, Command<S> disabled) {
        var builder = node.createBuilder();
        var original = node.getCommand();
        if (original != null) builder.executes(context -> enabled.getAsBoolean()
                ? original.run(context) : disabled.run(context));
        for (var child : node.getChildren()) builder.then(guard(child, enabled, disabled));
        return builder.build();
    }
}
