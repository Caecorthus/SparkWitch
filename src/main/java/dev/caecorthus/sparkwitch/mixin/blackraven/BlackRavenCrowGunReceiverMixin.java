package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wathe revolver/derringer receiver on a Raven Form crow (plan v2 §2.5). Wathe resolves the client's target id once and
 * kills only a {@code ServerPlayerEntity}; a crow id the shooter validly hit resolves to the crow's owner instead
 * ({@link BlackRavenCrowHits#onGunTargetLookup}: hittable crow, 65 cap, lag-compensated look ray), so Wathe's whole
 * player path runs on the Black Raven: the record, {@code ShouldPunishGunShooter}, the innocent-shot punishment and
 * backfire (小脑 for a police or passenger shooter, since the Raven is innocent), the kill (whose HEAD kill link ends the
 * session) and the passenger mood loss. Wathe's 65-block cap measures the hidden body, so a substituted owner passes it
 * (it was measured on the crow). Both are chaining {@code @WrapOperation}s (SparkTraits' {@code @ModifyConstant} on the
 * cap and the Seeker/Magician hooks at the record anchor are untouched; they see a player target). Class-level
 * {@code remap = false}; the vanilla targets say {@code remap = true}.
 * 化鸦乌鸦上的 Wathe 左轮/德林加接收器（计划 v2 §2.5）。Wathe 只解析一次客户端目标 id，且只击杀 {@code ServerPlayerEntity}；
 * 射手有效命中的乌鸦 id 改为解析为乌鸦的主人（{@link BlackRavenCrowHits#onGunTargetLookup}：可命中的乌鸦、65 格上限、延迟补偿的
 * 视线射线），因此 Wathe 的整条玩家路径都作用于黑羽鸦：记录、{@code ShouldPunishGunShooter}、误杀惩罚与走火（黑羽鸦是好人，
 * 警察或乘客射手会小脑）、击杀（其 HEAD 击杀链接会结束会话）以及乘客理智损失。Wathe 的 65 格上限量的是隐藏的本体，被替身的主人
 * 已按乌鸦量过，因此直接通过。两者都是可串联的 {@code @WrapOperation}（SparkTraits 对上限的 {@code @ModifyConstant} 与记录锚点处的
 * 搜寻者/魔术师钩子不受影响，它们看到的是玩家目标）。类级 {@code remap = false}；原版目标显式声明 {@code remap = true}。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class BlackRavenCrowGunReceiverMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getEntityById(I)Lnet/minecraft/entity/Entity;",
                    remap = true),
            require = 1, allow = 1)
    private @Nullable Entity sparkwitch$crowResolvesToOwner(ServerWorld world, int entityId,
                                                           Operation<Entity> original, GunShootPayload payload,
                                                           ServerPlayNetworking.Context context) {
        return BlackRavenCrowHits.onGunTargetLookup(context.player(), original.call(world, entityId));
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;distanceTo(Lnet/minecraft/entity/Entity;)F",
                    remap = true),
            require = 1, allow = 1)
    private float sparkwitch$substitutedOwnerPassesCap(ServerPlayerEntity candidate, Entity shooter,
                                                      Operation<Float> original) {
        return BlackRavenCrowHits.receiverDistance(candidate, original.call(candidate, shooter));
    }
}
