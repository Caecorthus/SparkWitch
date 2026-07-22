package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reveals an invisible Wraith only to actual spectators. / 仅向真正的旁观者显示隐身冤魂。 */
@Mixin(value = Entity.class, priority = 100)
public abstract class WraithEntityInvisibilityMixin {
    @ModifyReturnValue(method = "isInvisibleTo", at = @At("RETURN"))
    private boolean sparkwitch$revealWraithToSpectator(boolean original, PlayerEntity viewer) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity target
                && (WraithViewerRules.shouldRevealToSpectator(viewer, target)
                || VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target)
                || CurserFeatureService.isActivePromotedCurser(target)
                && viewer != null
                && WitchFactionRules.isWitchFactionMember(
                        GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer)))) {
            return false;
        }
        return original;
    }
}
