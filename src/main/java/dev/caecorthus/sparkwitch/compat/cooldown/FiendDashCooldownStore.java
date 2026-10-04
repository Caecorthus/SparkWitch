package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The Fiend's Dash cooldown (owner decision 2026-10-04), kept as an absolute ready tick in the
 * {@code sparkwitch:fiend_moment} world component, whose sync sends it only to the moment Fiend. It exists only while
 * the moment runs and dies with it. The nominal is the 30 s cooldown after each use. Every write only moves the ready
 * tick later through {@code setDashReadyTick}, which resyncs, so nothing is shortened and a running Speed IV is never
 * touched.
 * 魔人疾驰冷却（所有者 2026-10-04 决定），以绝对就绪刻保存在 {@code sparkwitch:fiend_moment} 世界组件中，其同步只发给
 * 时刻中的魔人。它只在时刻进行中存在，并随时刻一同结束。标准冷却为每次使用后的 30 秒。每次写入都只经
 * {@code setDashReadyTick} 把就绪刻推后（并重新同步），因此绝不缩短，也不改动进行中的速度 IV。
 */
final class FiendDashCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("fiend_dash");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // FiendDashService's role and round gates: an ACTIVE round and the role-gated moment Fiend.
        // 与 FiendDashService 的职业与回合门控一致：ACTIVE 回合且按职业判断的时刻中魔人。
        return GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && FiendParticipation.isMomentFiend(player);
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return moment(player).dashCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(FiendRules.DASH_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        FiendMomentWorldComponent moment = moment(player);
        ForcedCooldownMath.raiseReadyTick(moment.dashReadyTick(), now(player), ticks)
                .ifPresent(moment::setDashReadyTick);
    }

    private static FiendMomentWorldComponent moment(ServerPlayerEntity player) {
        return FiendMomentWorldComponent.get(player.getServerWorld());
    }

    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getTime();
    }
}
