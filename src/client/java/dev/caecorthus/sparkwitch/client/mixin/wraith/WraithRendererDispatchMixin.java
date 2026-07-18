package dev.caecorthus.sparkwitch.client.mixin.wraith;

import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.wraith.WraithSteveProjection;
import dev.doctor4t.wathe.client.render.entity.PlayerBodyEntityRenderer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Selects wide renderers without mutating player profiles or shared entities. / 不修改玩家档案或共享实体，仅选择宽臂渲染器。 */
@Mixin(value = EntityRenderDispatcher.class, priority = 1200)
public abstract class WraithRendererDispatchMixin {
    @Shadow
    private Map<SkinTextures.Model, EntityRenderer<? extends PlayerEntity>> modelRenderers;

    @Unique
    private EntityRenderer<? extends PlayerBodyEntity> sparkwitch$wraithBodyRenderer;

    @Inject(method = "reload", at = @At("TAIL"))
    private void sparkwitch$reloadWraithBodyRenderer(
            ResourceManager manager,
            CallbackInfo ci,
            @Local EntityRendererFactory.Context context
    ) {
        this.sparkwitch$wraithBodyRenderer = new PlayerBodyEntityRenderer<>(context, false);
    }

    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void sparkwitch$useWideWraithProjection(
            T entity,
            CallbackInfoReturnable<EntityRenderer<? super T>> cir
    ) {
        if (entity instanceof AbstractClientPlayerEntity player
                && WraithSteveProjection.shouldAnonymizePlayer(player)) {
            EntityRenderer<? extends PlayerEntity> renderer = this.modelRenderers.get(SkinTextures.Model.WIDE);
            if (renderer != null) {
                cir.setReturnValue((EntityRenderer<? super T>) renderer);
            }
            return;
        }
        if (entity instanceof PlayerBodyEntity
                && WraithSteveProjection.shouldAnonymizeCorpses()
                && this.sparkwitch$wraithBodyRenderer != null) {
            cir.setReturnValue((EntityRenderer<? super T>) this.sparkwitch$wraithBodyRenderer);
        }
    }
}
