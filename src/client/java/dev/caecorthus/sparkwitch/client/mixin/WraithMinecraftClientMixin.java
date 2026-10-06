package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.grandwitch.RecruitmentHoldClient;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
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
                && WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)) {
            cir.setReturnValue(false);
            return;
        }
        if (RecruitmentHoldClient.isOutlineHiddenFrom(viewer, entity)) {
            // A held recruit gets no outline from any source (vanilla glowing included) for a non-spectator viewer
            // outside the witch faction (owner 2026-10-06).
            // 被定身的新共犯对魔女阵营以外的非旁观观察者不显示任何来源的描边（包括原版发光，所有者 2026-10-06）。
            cir.setReturnValue(false);
        }
    }
}
