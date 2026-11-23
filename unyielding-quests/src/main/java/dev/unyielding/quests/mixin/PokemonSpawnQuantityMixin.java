package dev.unyielding.quests.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.command.SpawnPokemon", remap = false)
public abstract class PokemonSpawnQuantityMixin {
    @Unique private static final Pattern unyielding$numericToken =
        Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    @Unique private static final Pattern unyielding$wholeNumber = Pattern.compile("[0-9]+");
    @Unique private static final SimpleCommandExceptionType unyielding$badCount =
        new SimpleCommandExceptionType(Component.literal("Pokémon spawn count must be a whole number from 1 to 99."));

    // Cobblemon has already enforced the same permission for the normal command and alias.
    // Only the positionless callback is affected; spawnpokemonat retains upstream semantics.
    @Inject(method = "register$lambda$0", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void unyielding$spawnQuantity(CommandContext<CommandSourceStack> context,
                                                CallbackInfoReturnable<Integer> result) throws CommandSyntaxException {
        var propertiesNode = context.getNodes().stream()
            .filter(node -> node.getNode().getName().equals("properties"))
            .findFirst().orElse(null);
        if (propertiesNode == null) return;

        String properties = propertiesNode.getRange().get(context.getInput()).strip();
        int suffix = properties.length();
        while (suffix > 0 && !Character.isWhitespace(properties.charAt(suffix - 1))) suffix--;
        if (suffix == 0) return;
        String token = properties.substring(suffix);
        if (!unyielding$numericToken.matcher(token).matches()) return;
        if (!unyielding$wholeNumber.matcher(token).matches()) throw unyielding$badCount.create();

        final int count;
        try {
            count = Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            throw unyielding$badCount.create();
        }
        if (count < 1 || count > 99) throw unyielding$badCount.create();

        var source = context.getSource();
        var dispatcher = source.getServer().getCommands().getDispatcher();
        String singleProperties = properties.substring(0, suffix).stripTrailing();
        int preceding = singleProperties.length();
        while (preceding > 0 && !Character.isWhitespace(singleProperties.charAt(preceding - 1))) preceding--;
        // Reject repeated counts before any spawn; otherwise the same callback would multiply them.
        if (unyielding$numericToken.matcher(singleProperties.substring(preceding)).matches()) {
            throw unyielding$badCount.create();
        }
        String singleInput = "spawnpokemon " + singleProperties;
        var parsed = dispatcher.parse(singleInput, source);
        var single = parsed.getContext().build(singleInput);
        if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty() || single.getCommand() == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parsed.getReader());
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            try {
                // Run the same command with the same source and no count. Recursive callbacks
                // pass through to native species, form and properties handling.
                spawned += single.getCommand().run(single);
            } catch (CommandSyntaxException e) {
                if (spawned == 0) throw e;
                source.sendFailure(Component.literal("Spawned " + spawned + " of " + count + " Pokémon before stopping."));
                result.setReturnValue(spawned);
                return;
            }
        }
        int total = spawned;
        source.sendSuccess(() -> Component.literal("Spawned " + total + " Pokémon."), false);
        result.setReturnValue(total);
    }
}
