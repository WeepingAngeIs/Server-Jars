package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailBackpacks;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.p3pp3rf1y.sophisticatedbackpacks.command.GiveCommand",remap=false)
public abstract class BackpackGiveMixin {
    @Inject(method="giveBackpack",at=@At("HEAD"),cancellable=true,require=0)
    private static void jail$held(CommandSourceStack source,UUID bag,Collection<ServerPlayer> targets,CallbackInfoReturnable<Integer> result) {
        if(JailBackpacks.locked(bag)){
            source.sendFailure(Component.literal("This backpack is held in jail custody"));
            result.setReturnValue(0);
        }
    }
}
