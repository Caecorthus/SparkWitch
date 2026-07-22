package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.CreativeWraithInstinctRules;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the final Wraith outline veto. / 在所有描边来源返回后应用最终的冤魂否决。 */
@Mixin(value = MinecraftClient.class, priority = 100)
public abstract class WraithMinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$vetoWraithOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (entity instanceof PlayerEntity target
                && WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)
                && !(CreativeWraithInstinctRules.shouldReveal(viewer, target)
                        && WatheClient.getInstinctHighlight(target) != -1)) {
            cir.setReturnValue(false);
        }
    }
}
