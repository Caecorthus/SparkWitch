package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.mixin.seeker.SeekerTaotieCooldownAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie.SeekerTaotieRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * External seam (hard dependency, direct API, pinned NoellesRoles 1.7.6 = {@code b58fa5f}): every Seeker read or write
 * of NoellesRoles Taotie state goes through here. Callers outside the Taotie package use only {@link #isSwallowed} and
 * {@link #isTaotie}. {@link #isSwallowed}, {@link #isTaotie} and {@link #swallowCooldown} are side-neutral (the swallowed
 * flag syncs to everyone, the Taotie component to its owner); the cooldown writes are server only. A NoellesRoles
 * upgrade must re-run {@code SeekerNoellesTaotieContractTest} (field names, descriptors and inlined constants).
 * 外部接缝（硬依赖、直接 API，固定 NoellesRoles 1.7.6 = {@code b58fa5f}）：搜寻者对 NoellesRoles 饕餮状态的所有读写都经过
 * 此处。饕餮包之外的调用方只使用 {@link #isSwallowed} 与 {@link #isTaotie}。{@link #isSwallowed}、{@link #isTaotie} 与
 * {@link #swallowCooldown} 两端通用（被吞标记同步给所有人，饕餮组件同步给其拥有者）；冷却写入仅限服务端。升级
 * NoellesRoles 时必须重新运行 {@code SeekerNoellesTaotieContractTest}（字段名、描述符与被内联的常量）。
 */
public final class NoellesTaotieSeekerBridge {
    private NoellesTaotieSeekerBridge() {
    }

    /**
     * Whether a Taotie currently holds this player (a living spectator following the Taotie). Safe on both sides; a
     * missing component reads as "not swallowed".
     * 该玩家当前是否被饕餮吞下（跟随饕餮的活体旁观者）。两端均可安全调用；组件缺失时视为“未被吞噬”。
     */
    public static boolean isSwallowed(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return SwallowedPlayerComponent.KEY.maybeGet(player)
                .map(SwallowedPlayerComponent::isSwallowed)
                .orElse(false);
    }

    /** Exactly NoellesRoles' Taotie role in this world's game. Side-neutral. / 恰为本世界对局中的饕餮职业。两端通用。 */
    public static boolean isTaotie(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role taotie = Noellesroles.TAOTIE;
        return taotie != null && GameWorldComponent.KEY.get(player.getWorld()).isRole(player, taotie);
    }

    /**
     * UUID form for an offline or unloaded Taotie (return poll). / 供离线或未加载饕餮使用的 UUID 形式（归还轮询）。
     */
    public static boolean isTaotie(GameWorldComponent game, @Nullable UUID player) {
        Role taotie = Noellesroles.TAOTIE;
        return player != null && taotie != null && game.isRole(player, taotie);
    }

    /**
     * Remaining swallow cooldown in ticks (client: the owner-synced value). A missing component fails closed
     * ({@link Integer#MAX_VALUE}, never ready).
     * 剩余吞噬冷却刻数（客户端读取同步给拥有者的值）。组件缺失时保守关闭（{@link Integer#MAX_VALUE}，永不就绪）。
     */
    public static int swallowCooldown(PlayerEntity taotie) {
        return TaotiePlayerComponent.KEY.maybeGet(taotie)
                .map(TaotiePlayerComponent::getSwallowCooldown)
                .orElse(Integer.MAX_VALUE);
    }

    /**
     * Server only: starts NoellesRoles' dynamic swallow cooldown ({@code calculatedSwallowCooldown}, the same cost as a
     * player swallow); {@code setSwallowCooldown} syncs the Taotie's own client. A swallow that fails afterwards is
     * rolled back with {@link #restoreSwallowCooldown}.
     * 仅服务端：启动 NoellesRoles 的动态吞噬冷却（{@code calculatedSwallowCooldown}，与吞人代价相同）；
     * {@code setSwallowCooldown} 会同步给饕餮本人的客户端。之后失败的吞噬用 {@link #restoreSwallowCooldown} 回滚。
     */
    public static void consumeSwallowCooldown(PlayerEntity taotie) {
        TaotiePlayerComponent component = TaotiePlayerComponent.KEY.getNullable(taotie);
        if (component == null) {
            return;
        }
        int calculated = ((SeekerTaotieCooldownAccessor) (Object) component).sparkwitch$getCalculatedSwallowCooldown();
        component.setSwallowCooldown(SeekerTaotieRules.swallowCostTicks(calculated,
                TaotiePlayerComponent.SWALLOW_COOLDOWN));
    }

    /** Server only: rolls a consumed cooldown back. / 仅服务端：回滚已消耗的冷却。 */
    public static void restoreSwallowCooldown(PlayerEntity taotie, int ticks) {
        TaotiePlayerComponent component = TaotiePlayerComponent.KEY.getNullable(taotie);
        if (component != null) {
            component.setSwallowCooldown(Math.max(0, ticks));
        }
    }

    /**
     * NoellesRoles' swallow reach (feet to feet, squared). A compile-time constant inlined into SparkWitch, pinned to 9
     * by the contract test so an upstream change cannot drift silently.
     * NoellesRoles 的吞噬距离（脚到脚，平方）。它是被内联进 SparkWitch 的编译期常量，由契约测试固定为 9，
     * 防止上游改动悄然失配。
     */
    public static int swallowDistanceSquared() {
        return TaotiePlayerComponent.SWALLOW_DISTANCE_SQUARED;
    }
}
