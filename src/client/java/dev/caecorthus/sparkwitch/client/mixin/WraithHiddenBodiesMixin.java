package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithSteveProjection;
import org.agmas.noellesroles.scavenger.HiddenBodiesWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Reveals Scavenger-hidden corpses only to the local Wraith. / 仅向本地冤魂显示被食腐者隐藏的尸体。 */
@Mixin(value = HiddenBodiesWorldComponent.class, remap = false)
public abstract class WraithHiddenBodiesMixin {
    @Inject(method = "isHidden", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$revealHiddenBody(UUID playerUuid, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && WraithSteveProjection.shouldAnonymizeCorpses()) {
            cir.setReturnValue(false);
        }
    }
}
