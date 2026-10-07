package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.demonhunter.DemonHunterShootC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Server Demon Hunter pistol receiver (NoellesRoles): breaks a validated device target; NoellesRoles' kill path is
 * unchanged. The seam is NoellesRoles' single target lookup, which runs only after its spectator, held-pistol,
 * cooldown and bullet checks, so a forged packet cannot break a device without a real, loaded, ready pistol. When
 * {@link SeekerDeviceHits#onDemonHunterPayload} reports that a device absorbed the shot, the lookup resolves to
 * {@code null}: NoellesRoles then runs its own miss path (null target recorded, bullet spent, sound, muzzle flash,
 * cooldown) and never hits a player behind the device. Any other result is returned untouched. The same seam ends a
 * validated Magician puppet ({@code MagicianPuppetHits.onDemonHunterPayload}; a puppet is never a player target, so
 * NoellesRoles also runs its miss path; a Jester puppet's refund is paid by {@code MagicianDemonHunterRefundMixin}).
 * Class-level {@code remap = false} (Fabric/NoellesRoles members); the vanilla {@code ServerWorld#getEntityById}
 * target says {@code remap = true} explicitly.
 * 服务端猎魔枪接收器（NoellesRoles）：打坏经校验的设备目标；NoellesRoles 的击杀逻辑不变。接缝是 NoellesRoles
 * 唯一一次目标查找，它只在旁观、手持猎魔枪、冷却与子弹检查之后执行，因此伪造的数据包无法在没有真实、已装弹、
 * 可射击的猎魔枪时打坏设备。当 {@link SeekerDeviceHits#onDemonHunterPayload} 表示设备吸收了这一枪时，查找结果变为
 * {@code null}：NoellesRoles 随后走自己的未命中流程（记录空目标、消耗子弹、音效、枪口火光、冷却），
 * 也绝不会命中设备后方的玩家。其他结果原样返回。同一接缝也会结束经校验的魔术师皮套
 * （{@code MagicianPuppetHits.onDemonHunterPayload}；皮套从不是玩家目标，因此 NoellesRoles 同样走未命中流程；小丑皮套的返还
 * 子弹由 {@code MagicianDemonHunterRefundMixin} 支付）。类级 {@code remap = false}（Fabric/NoellesRoles 成员）；
 * 原版 {@code ServerWorld#getEntityById} 目标显式声明 {@code remap = true}。
 */
@Mixin(value = DemonHunterShootC2SPacket.Receiver.class, remap = false)
public abstract class SeekerDemonHunterHitMixin {
    @WrapOperation(method = "receive(Lorg/agmas/noellesroles/demonhunter/DemonHunterShootC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getEntityById(I)Lnet/minecraft/entity/Entity;",
                    remap = true))
    private @Nullable Entity sparkwitch$absorbShotAtSeekerDevice(ServerWorld world, int entityId,
                                                               Operation<Entity> original,
                                                               DemonHunterShootC2SPacket payload,
                                                               ServerPlayNetworking.Context context) {
        Entity resolved = original.call(world, entityId);
        // Magician seam: a puppet never resolves to NoellesRoles' player target, so the shot then runs its miss path.
        // 魔术师接缝：皮套永远不会解析为 NoellesRoles 的玩家目标，因此这一枪随后走未命中流程。
        MagicianPuppetHits.onDemonHunterPayload(context.player(), resolved);
        return SeekerDeviceHits.onDemonHunterPayload(context.player(), resolved) ? null : resolved;
    }
}
