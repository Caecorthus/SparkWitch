package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowVision;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla fog colour ({@code render}) and fog distance ({@code applyFog}) read the status effects of the camera
 * entity. While the local Raven looks through its crow with Perception open, the single {@code getFocusedEntity()} read
 * of each method answers the body instead, so Perception's blindness fog looks the same as on foot; otherwise the crow
 * (no effects) is kept, so the body's blackout blindness never shows through it. {@code @ModifyExpressionValue} passes
 * the value through outside that case and chains with other hooks. Client-only.
 * 原版雾颜色（render）与雾距离（applyFog）读取镜头实体的状态效果。本地黑羽鸦通过乌鸦观看且感知开启时，两个方法中唯一一次
 * getFocusedEntity() 读取改为返回本体，使感知的致盲雾与步行时一致；其他情况保留乌鸦（没有任何效果），本体的熄灯致盲因此不会透过乌鸦
 * 显示。@ModifyExpressionValue 在其他情况下原样传递，可与其他钩子串联。纯客户端。
 */
@Mixin(BackgroundRenderer.class)
public abstract class BlackRavenCrowFogMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;"))
    private static Entity sparkwitch$crowFogColorSubject(Entity focused) {
        return BlackRavenCrowVision.fogSubject(focused);
    }

    @ModifyExpressionValue(method = "applyFog", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;"))
    private static Entity sparkwitch$crowFogSubject(Entity focused) {
        return BlackRavenCrowVision.fogSubject(focused);
    }
}
