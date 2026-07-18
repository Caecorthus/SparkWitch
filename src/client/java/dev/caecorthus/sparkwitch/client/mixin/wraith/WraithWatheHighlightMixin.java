package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithRole;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolves Wraith peer color and living-viewer privacy before role-specific highlights. / 在职业高亮前裁决冤魂同伴颜色与存活观察者隐私。 */
@Mixin(value = WatheClient.class, remap = false, priority = 2000)
public abstract class WraithWatheHighlightMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$resolveWraithHighlight(
            Entity target,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerEntity playerTarget)) {
            return;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (WraithViewerRules.shouldRevealToWraithPeer(viewer, playerTarget)) {
            cir.setReturnValue(WraithRole.COLOR);
        } else if (WraithViewerRules.shouldHideFromViewer(viewer, playerTarget)) {
            cir.setReturnValue(-1);
        }
    }
}
