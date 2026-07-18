package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import org.agmas.noellesroles.scavenger.HiddenBodiesWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Reveals hidden bodies only inside the Wraith's anonymous corpse view. / 仅在冤魂匿名尸体视角内显示被隐藏的尸体。 */
@Mixin(value = HiddenBodiesWorldComponent.class, remap = false)
public abstract class WraithHiddenBodiesMixin {
    @Inject(method = "isHidden", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$revealHiddenBody(UUID playerUuid, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && WraithSteveProjection.shouldAnonymizeCorpses()) {
            cir.setReturnValue(false);
        }
    }
}
