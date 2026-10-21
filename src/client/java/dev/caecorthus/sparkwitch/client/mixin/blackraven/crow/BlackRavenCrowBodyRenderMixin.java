package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowBodyVisibility;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: the body a Black Raven leaves behind in Raven Form is not drawn at all for non-spectator viewers
 * (vanilla invisibility would still draw its held items, armour, cape and shadow). Reads the synced active bit through
 * {@code BlackRavenCrowBodyVisibility}; any other entity passes untouched.
 * 纯客户端：黑羽鸦化鸦时留下的本体对非旁观观察者完全不绘制（原版隐身仍会绘制手持物、护甲、披风与影子）。经
 * BlackRavenCrowBodyVisibility 读取同步的在场标记；其他实体原样通过。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class BlackRavenCrowBodyRenderMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void sparkwitch$hideRavenFormBody(E entity, Frustum frustum, double x, double y,
                                                                 double z, CallbackInfoReturnable<Boolean> cir) {
        if (BlackRavenCrowBodyVisibility.hidesFromLocalViewer(entity)) {
            cir.setReturnValue(false);
        }
    }
}
