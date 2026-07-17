package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/** Resolves Wraith instinct privacy before role highlights. / 在职业高亮前裁决冤魂的本能隐私。 */
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
        if (viewer == null) {
            return;
        }

        if (WraithViewerRules.shouldRevealToSpectator(viewer, playerTarget)) {
            GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
            int highlight = WatheClient.isInstinctEnabled()
                    ? Objects.requireNonNullElse(game.getRole(playerTarget), WatheRoles.CIVILIAN).color()
                    : -1;
            cir.setReturnValue(highlight);
            return;
        }
        if (WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, playerTarget)) {
            cir.setReturnValue(-1);
        }
    }
}
