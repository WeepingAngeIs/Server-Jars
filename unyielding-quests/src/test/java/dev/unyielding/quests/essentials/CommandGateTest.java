package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CommandGateTest {
    @Test void blocksEveryExecutableAndAlreadyParsedCommandsWithoutSideEffects() throws Exception {
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicInteger sideEffects = new AtomicInteger(), blocked = new AtomicInteger();
        var root = LiteralArgumentBuilder.<Integer>literal("feature")
                .executes(c -> sideEffects.incrementAndGet())
                .then(LiteralArgumentBuilder.<Integer>literal("child").executes(c -> sideEffects.incrementAndGet()))
                .then(RequiredArgumentBuilder.<Integer, String>argument("name", StringArgumentType.word())
                        .executes(c -> sideEffects.incrementAndGet()));
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(CommandGate.guard(root.build(), enabled::get, c -> { blocked.incrementAndGet(); return 0; }));
        assertEquals(1, dispatcher.execute("feature", 2));
        var parsedBeforeSwitch = dispatcher.parse("feature child", 2);
        enabled.set(false);
        assertEquals(0, dispatcher.execute(parsedBeforeSwitch));
        assertEquals(0, dispatcher.execute("feature value", 2));
        assertEquals(0, dispatcher.execute("feature", 2));
        assertEquals(1, sideEffects.get());
        assertEquals(3, blocked.get());
        enabled.set(true);
        assertEquals(2, dispatcher.execute("feature child", 2));
    }

    @Test void preservesRootAndChildPermissionsArgumentsAndSuggestions() throws Exception {
        var root = LiteralArgumentBuilder.<Integer>literal("feature").requires(level -> level >= 1)
                .then(RequiredArgumentBuilder.<Integer, String>argument("name", StringArgumentType.word())
                        .requires(level -> level >= 2)
                        .suggests((context, builder) -> builder.suggest("saved-home").buildFuture())
                        .executes(context -> StringArgumentType.getString(context, "name").length()));
        var guarded = CommandGate.guard(root.build(), () -> true, c -> 0);
        assertFalse(guarded.canUse(0));
        assertTrue(guarded.canUse(1));
        assertFalse(guarded.getChild("name").canUse(1));
        assertTrue(guarded.getChild("name").canUse(2));
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(guarded);
        assertEquals(10, dispatcher.execute("feature saved-home", 2));
        assertTrue(dispatcher.getCompletionSuggestions(dispatcher.parse("feature ", 2)).get()
                .getList().stream().anyMatch(s -> s.getText().equals("saved-home")));
    }
}
