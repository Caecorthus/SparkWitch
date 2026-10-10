package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.render.WraithViewerGates;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reveals an invisible Wraith only to actual spectators, and its body to viewers an add-on gate names
 * ({@code api.client.WraithViewerApi}).
 * 仅向真正的旁观者显示隐身冤魂；附属模组闸门（{@code api.client.WraithViewerApi}）指定的观察者可看到其身体。
 */
@Mixin(value = Entity.class, priority = 100)
public abstract class WraithEntityInvisibilityMixin {
    @ModifyReturnValue(method = "isInvisibleTo", at = @At("RETURN"))
    private boolean sparkwitch$revealWraithToSpectator(boolean original, PlayerEntity viewer) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity target
                && (WraithViewerRules.shouldRevealToSpectator(viewer, target)
                || WraithViewerGates.revealsBody(viewer, target)
                || VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target)
                || WraithViewerRules.shouldRevealCurserToWitch(viewer, target))) {
            return false;
        }
        return original;
    }
}
