package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies wide Steve metadata only to the local renderer projection. / 仅向本地渲染投影提供宽臂 Steve 元数据。 */
@Mixin(value = AbstractClientPlayerEntity.class, priority = 2000)
public abstract class WraithPlayerSkinTexturesMixin {
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$projectSteveSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (WraithSteveProjection.shouldAnonymizePlayer(player)) {
            cir.setReturnValue(WraithSteveProjection.steveSkinTextures());
        }
    }
}
