package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithViewerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the terminal cyan peer outline or ordinary-viewer privacy veto. / 最终裁决青色同伴描边与普通观察者隐私否决。 */
@Mixin(value = MinecraftClient.class, priority = 100)
public abstract class WraithMinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$resolveWraithOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (!(entity instanceof PlayerEntity target)) {
            return;
        }
        if (WraithViewerRules.shouldRevealToWraithPeer(viewer, target)) {
            cir.setReturnValue(true);
            return;
        }
        if (WraithViewerRules.shouldHideFromViewer(viewer, target)) {
            cir.setReturnValue(false);
        }
    }
}
