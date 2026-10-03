package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.riftwalker.gate.RiftGateCrosshairClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Melee pass-through, attack half (R1): a gate user's crosshair keeps gates (for the right-click entry), so a
 * left-click that resolved to a gate is re-picked with gates ignored and hits the entity behind it, or sends nothing.
 * Wraps only the entity-attack call of {@code doAttack}: every HEAD cancel on {@code doAttack} (in-gate lock, Seeker,
 * Death Ray, NR Spirit, SparkTraits Last Escape) still runs first, {@code attackEntity}'s own hooks still run through
 * {@code original}, and vanilla still swings. Non-gate targets pass through unchanged.
 * 近战穿门的攻击部分（R1）：门使用者的准星保留门（用于右键进门），因此落在门上的左键会忽略门重新选取并击中门后的实体，没有
 * 实体则不发送任何东西。只包装 {@code doAttack} 中攻击实体的那一处调用：{@code doAttack} 上所有 HEAD 取消（门内锁、搜寻者、
 * 死光、NR 灵魂、SparkTraits 最后逃亡）仍先执行，{@code attackEntity} 自身的钩子经 {@code original} 照常执行，原版照常挥手。
 * 非门目标原样通过。
 */
@Mixin(MinecraftClient.class)
public abstract class RiftGateAttackMixin {
    @WrapOperation(
            method = "doAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V"
            )
    )
    private void sparkwitch$attackThroughRiftGate(ClientPlayerInteractionManager manager, PlayerEntity player,
                                                  Entity target, Operation<Void> original) {
        Entity resolved = RiftGateCrosshairClient.attackTarget((MinecraftClient) (Object) this, target);
        if (resolved != null) {
            original.call(manager, player, resolved);
        }
    }
}
