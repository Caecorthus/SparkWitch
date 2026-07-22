package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects the local jump key before prediction when Wathe's map disables jumping. */
@Mixin(KeyBinding.class)
public abstract class WraithJumpKeyBindingMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockWraithJumpKey(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        GameOptions options = client.options;
        if ((Object) this != options.jumpKey || client.player == null) {
            return;
        }
        boolean mapAllowsJump = MapEnhancementsWorldComponent.KEY.get(client.player.getWorld())
                .getJumpConfig().allowed();
        if (!WraithParticipationRules.mayJump(WraithStateService.isActive(client.player), mapAllowsJump)) {
            cir.setReturnValue(false);
        }
    }
}
