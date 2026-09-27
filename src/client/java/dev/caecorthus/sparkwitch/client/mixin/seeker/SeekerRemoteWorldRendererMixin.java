package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Client-only: renders the owner's own body when it is seen from the car or camera. Vanilla skips the local player
 * through {@code !(entity instanceof ClientPlayerEntity) || camera.getFocusedEntity() == entity} in
 * {@code WorldRenderer#render}. The right-hand {@code getFocusedEntity()} is only evaluated for a
 * {@code ClientPlayerEntity}, and in a client world only the local player is one (others are
 * {@code OtherClientPlayerEntity}), so returning the local player from that single call while viewing makes the
 * check pass for the body alone. It is the first {@code getFocusedEntity()} after the only
 * {@code LivingEntity#isSleeping()} of the same condition (pinned by {@code SeekerRemoteRenderMixinTargetsTest}).
 * No {@code @Local} and no new locals: it coexists with Wathe's {@code @Local Entity} and NoellesRoles'
 * {@code CAPTURE_FAILHARD} in the same method. The focused car itself is still skipped by vanilla in first person.
 * <p>Remap / runtime: plain {@code INVOKE} selectors are remapped by Loom. The {@code INSTANCEOF} itself is NOT
 * targeted, because {@code @At("CONSTANT", args = "classValue=...")} strings are not remapped, and a MixinExtras
 * {@code @Expression} needs {@code "mixinextras": {"minVersion": "0.5.0"}} in the mixin config (it crashes the client
 * at class load without it).
 * 纯客户端：从小车或摄像头看时渲染拥有者自己的本体。原版在 {@code WorldRenderer#render} 中用
 * {@code !(entity instanceof ClientPlayerEntity) || camera.getFocusedEntity() == entity} 跳过本地玩家。右侧的
 * {@code getFocusedEntity()} 只会对 {@code ClientPlayerEntity} 求值，而客户端世界里只有本地玩家是
 * {@code ClientPlayerEntity}（其他玩家为 {@code OtherClientPlayerEntity}），因此观看期间让这一处调用返回本地玩家，
 * 即可只让本体通过判断。它是同一条件中唯一一次 {@code LivingEntity#isSleeping()} 之后的第一处
 * {@code getFocusedEntity()}（由 {@code SeekerRemoteRenderMixinTargetsTest} 固定）。不使用 {@code @Local}、
 * 不新增局部变量：可与同一方法中 Wathe 的 {@code @Local Entity} 及 NoellesRoles 的 {@code CAPTURE_FAILHARD} 共存。
 * 第一人称下原版仍会跳过被聚焦的小车本身。
 * 重映射/运行时：普通 {@code INVOKE} 选择器会被 Loom 重映射。不直接定位 {@code INSTANCEOF}：
 * {@code @At("CONSTANT", args = "classValue=...")} 字符串不会被重映射，而 MixinExtras {@code @Expression}
 * 需要 mixin 配置声明 {@code "mixinextras": {"minVersion": "0.5.0"}}，否则类加载时客户端直接崩溃。
 */
@Mixin(WorldRenderer.class)
public abstract class SeekerRemoteWorldRendererMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @ModifyExpressionValue(method = "render",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;isSleeping()Z")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;",
                    ordinal = 0))
    private Entity sparkwitch$renderOwnBodyWhileViewing(Entity focused) {
        if (!SeekerRemoteViewClient.isActive()) {
            return focused;
        }
        ClientPlayerEntity player = this.client.player;
        return player != null ? player : focused;
    }
}
