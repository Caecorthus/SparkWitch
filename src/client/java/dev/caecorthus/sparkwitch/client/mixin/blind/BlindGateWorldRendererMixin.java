package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * D2 entity gate: while the Blind view is active, another player who is not currently perceived is never drawn into
 * the world frame (no body, shadow, fire, label or outline). {@code renderEntity} is the world loop's single per-entity
 * call and sits above every renderer swap (Wathe corpses, NoellesRoles/SparkStrength morphs, the SparkTraits Pig body,
 * Wraith and Black Raven skins), so none of them can re-add a hidden player; the outermost whole-method wrap also
 * encloses any later injection. {@code EntityRenderDispatcher.render} stays free for GUI and for the Blind's own
 * silhouette pass. Non-player entities (corpses, items, Seeker devices) are environment and pass (C6).
 * D2 实体闸门：盲人视图生效期间，当前未被感知的其他玩家不会进入世界画面（不画身体、阴影、火焰、名字或描边）。
 * {@code renderEntity} 是世界循环中每个实体唯一的一次调用，位于所有渲染器替换（Wathe 尸体、NoellesRoles/SparkStrength
 * 变形、SparkTraits 猪身体、冤魂与黑羽鸦皮肤）之上，因此它们都无法把被隐藏的玩家重新画出来；最外层的整方法包裹也覆盖
 * 之后加入的注入。{@code EntityRenderDispatcher.render} 保持开放，供 GUI 与盲人自己的轮廓渲染使用。
 * 非玩家实体（尸体、掉落物、搜寻者装置）属于环境，照常通过（C6）。
 */
@Mixin(value = WorldRenderer.class, priority = 2000)
public abstract class BlindGateWorldRendererMixin {
    @WrapMethod(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V")
    private void sparkwitch$blindHidesUnperceivedPlayers(Entity entity, double cameraX, double cameraY, double cameraZ,
                                                         float tickDelta, MatrixStack matrices,
                                                         VertexConsumerProvider vertexConsumers,
                                                         Operation<Void> original) {
        if (BlindClientGates.hidesEntity(entity)) {
            return;
        }
        original.call(entity, cameraX, cameraY, cameraZ, tickDelta, matrices, vertexConsumers);
    }
}
