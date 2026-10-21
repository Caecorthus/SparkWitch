package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wathe knife-stab receiver on a Raven Form crow (plan v2 §2.5), the gun receiver's twin: a crow id the attacker validly
 * stabbed (hittable crow, 3 + 0.5 lag-compensated reach, clear line) resolves to its owner
 * ({@link BlackRavenCrowHits#onKnifeTargetLookup}), so Wathe's stab path (record, kill, knife cooldown) and every
 * mod's stab hook (Fiend stab scope, NoellesRoles Bodyguard) run on the Black Raven. Wathe's 3-block cap measures the
 * hidden body, so a substituted owner passes it; the stab sound plays where the crow was hit. The HEAD hooks
 * (SparkTraits 2200, Magician/Seeker 2300) see the crow id and leave it alone; SparkStrength's Veteran, Coroner and
 * Silencer HEAD handlers cancel a non-player id before this lookup, so those knives do not reach the crow (follow-up).
 * Class-level {@code remap = false}; the vanilla targets say {@code remap = true}.
 * 化鸦乌鸦上的 Wathe 刀刺接收器（计划 v2 §2.5），与枪械接收器成对：攻击者有效刺中的乌鸦 id（可命中的乌鸦、3 + 0.5 的延迟补偿距离、
 * 视线畅通）解析为其主人（{@link BlackRavenCrowHits#onKnifeTargetLookup}），因此 Wathe 的刺击路径（记录、击杀、刀冷却）以及
 * 各模组的刺击钩子（魔人刺击作用域、NoellesRoles 保镖）都作用于黑羽鸦。Wathe 的 3 格上限量的是隐藏的本体，被替身的主人直接通过；
 * 刺击声在乌鸦被命中处播放。HEAD 钩子（SparkTraits 2200、魔术师/搜寻者 2300）看到的是乌鸦 id，不做处理；SparkStrength 的老兵、
 * 验尸官与沉默者 HEAD 处理器会在本查找之前取消非玩家 id，因此这些刀打不到乌鸦（后续事项）。类级 {@code remap = false}；原版目标
 * 显式声明 {@code remap = true}。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class BlackRavenCrowKnifeReceiverMixin {
    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getEntityById(I)Lnet/minecraft/entity/Entity;",
                    remap = true),
            require = 1, allow = 1)
    private @Nullable Entity sparkwitch$crowResolvesToOwner(ServerWorld world, int entityId,
                                                           Operation<Entity> original, KnifeStabPayload payload,
                                                           ServerPlayNetworking.Context context) {
        return BlackRavenCrowHits.onKnifeTargetLookup(context.player(), original.call(world, entityId));
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;distanceTo(Lnet/minecraft/entity/Entity;)F",
                    remap = true),
            require = 1, allow = 1)
    private float sparkwitch$substitutedOwnerPassesReach(ServerPlayerEntity target, Entity attacker,
                                                        Operation<Float> original) {
        return BlackRavenCrowHits.receiverDistance(target, original.call(target, attacker));
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;playSound(Lnet/minecraft/sound/SoundEvent;FF)V",
                    remap = true),
            require = 1, allow = 1)
    private void sparkwitch$stabSoundAtCrow(ServerPlayerEntity target, SoundEvent sound, float volume, float pitch,
                                            Operation<Void> original) {
        if (!BlackRavenCrowHits.playAtSubstitutedCrow(target, sound, volume, pitch)) {
            original.call(target, sound, volume, pitch);
        }
    }
}
