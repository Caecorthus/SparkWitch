package dev.caecorthus.sparkwitch.mixin.fiend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendTargetExclusion;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.serialkiller.SerialKillerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.UUID;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}, commit b58fa5f, members {@code remap = false}), C15:
 * the Serial Killer must never lock onto an unkillable dormant Fiend. Its target is chosen only from
 * {@code getEligibleTargets} (used by both {@code assignTarget} and {@code reassignTarget}) and kept while
 * {@code isTargetValid} holds (checked every 20 ticks), so both return values are filtered: a dormant Fiend is dropped
 * from the candidates and a held one is re-picked at the next check. A moment or spent Fiend and every other player
 * keep the host answer. Additive return-value filters only, no redirect. Server only (the component
 * ticks on the server). Pinned by {@code FiendDormantMixinContractTest}.
 * 外部接缝（锁定 NoellesRoles {@code 1.7.6-h1.5.6-spark}，提交 b58fa5f，成员 {@code remap = false}），C15：连环杀手绝不能
 * 锁定无法击杀的休眠魔人。其目标只从 {@code getEligibleTargets}（{@code assignTarget} 与 {@code reassignTarget} 共用）中
 * 选出，并在 {@code isTargetValid} 成立时保留（每 20 刻检查一次），因此两处返回值都被过滤：休眠魔人从候选中移除，已持有的
 * 休眠魔人目标在下一次检查时重选。时刻中或已耗尽的魔人与其他所有玩家保持宿主结果。仅叠加式返回值过滤，
 * 不做重定向。仅服务端（该组件在服务端刻更新）。由 {@code FiendDormantMixinContractTest} 固定。
 */
@Mixin(value = SerialKillerPlayerComponent.class, remap = false)
public abstract class SerialKillerPlayerComponentFiendTargetMixin {
    @ModifyReturnValue(
            method = "isTargetValid(Ljava/util/UUID;Ldev/doctor4t/wathe/cca/GameWorldComponent;Lnet/minecraft/server/world/ServerWorld;)Z",
            at = @At("RETURN")
    )
    private boolean sparkwitch$dormantFiendIsNeverAValidTarget(boolean valid, UUID target, GameWorldComponent game,
                                                               ServerWorld world) {
        return FiendTargetExclusion.isValidTarget(valid, world, target);
    }

    @ModifyReturnValue(
            method = "getEligibleTargets(Ldev/doctor4t/wathe/cca/GameWorldComponent;Lnet/minecraft/server/world/ServerWorld;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private List<UUID> sparkwitch$dormantFiendIsNeverACandidate(List<UUID> candidates, GameWorldComponent game,
                                                                ServerWorld world) {
        return FiendTargetExclusion.eligibleTargets(candidates, world);
    }
}
