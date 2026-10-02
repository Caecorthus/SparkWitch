package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.riftwalker.swapper.RiftSwapperClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.agmas.noellesroles.client.SwapperPlayerWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * C7: NoellesRoles' Swapper head click ({@code lambda$new$0}) silently ignores a player missing from the client world,
 * and spectators are never tracked by non-spectators, so a gate occupant could never be picked and D13 could not fire.
 * When that lookup misses, a listed, Wathe-alive SPECTATOR is let through so the server decides (gate occupant → crush,
 * any other spectator → silent cancel). Wraps only that one call; the cooldown check, pick order and payload stay NR's.
 * Pinned to the NR jar by a local test; {@code require = 1} fails loudly if the lambda or call moves.
 * C7：NoellesRoles 交换头像点击（{@code lambda$new$0}）会静默忽略客户端世界中不存在的玩家，而旁观者从不会被非旁观者追踪，
 * 因此门内玩家永远点不到，D13 无法触发。该查询落空时，放行列表中仍被 Wathe 记为存活的旁观者，交给服务端裁决（门内 → 夹死，
 * 其他旁观者 → 静默取消）。只包装这一处调用；冷却检查、点选顺序与数据包仍是 NR 原样。由本地测试锁定到 NR jar；
 * 若 lambda 或调用位置变化，{@code require = 1} 会直接报错。
 */
@Mixin(SwapperPlayerWidget.class)
public abstract class RiftSwapperWidgetMixin {
    @WrapOperation(
            method = "lambda$new$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPlayerByUuid(Ljava/util/UUID;)Lnet/minecraft/entity/player/PlayerEntity;"),
            require = 1
    )
    private static PlayerEntity sparkwitch$allowListedAliveSpectator(World world, UUID clicked,
                                                                     Operation<PlayerEntity> original) {
        PlayerEntity tracked = original.call(world, clicked);
        return tracked != null ? tracked : RiftSwapperClient.standInForUntrackedPick(clicked);
    }
}
