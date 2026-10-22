package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowVision;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code WorldRenderer#hasBlindnessOrDarkness} (sky and weather) reads the camera entity's effects, like the fog: the
 * body answers while Perception is open and the camera is the crow (see {@code BlackRavenCrowFogMixin}). Client-only.
 * WorldRenderer#hasBlindnessOrDarkness（天空与天气）与雾一样读取镜头实体的效果：感知开启且镜头为乌鸦时改为本体回答。纯客户端。
 */
@Mixin(WorldRenderer.class)
public abstract class BlackRavenCrowSkyMixin {
    @ModifyExpressionValue(method = "hasBlindnessOrDarkness", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;"))
    private Entity sparkwitch$crowSkySubject(Entity focused) {
        return BlackRavenCrowVision.fogSubject(focused);
    }
}
