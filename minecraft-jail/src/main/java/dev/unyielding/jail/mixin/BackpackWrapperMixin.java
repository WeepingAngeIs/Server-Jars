package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailBackpacks;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper",remap=false)
public abstract class BackpackWrapperMixin {
    @Inject(method="fromStack",at=@At("HEAD"),cancellable=true,require=0)
    private static void jail$held(ItemStack stack,CallbackInfoReturnable<Object> result) {
        if(JailBackpacks.locked(JailBackpacks.identity(stack)))result.setReturnValue(JailBackpacks.unavailableWrapper());
    }
    @Inject(method={"fromExistingData","getCapabilityFluidHandler"},at=@At("HEAD"),cancellable=true,require=0)
    private static void jail$heldCapability(ItemStack stack,CallbackInfoReturnable<Optional<?>> result) {
        if(JailBackpacks.locked(JailBackpacks.identity(stack)))result.setReturnValue(Optional.empty());
    }
}
