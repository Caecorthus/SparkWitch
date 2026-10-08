package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecPlayerComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.IntSupplier;

/**
 * Client only. The USEC scoped instinct cloak (owner, 2026-10-08), driven by
 * {@code client/mixin/usec/UsecInstinctCloakMixin}. Wathe's whole {@code getInstinctHighlight} for a cloaked target
 * (see {@link UsecInstinctCloakRules}) runs as a "cloaked evaluation" in which Wathe's two keyed instinct gates,
 * {@code isInstinctEnabled()} and {@code isInstinctEnabledAndIsKiller()}, read false. That is exactly "this viewer is
 * not holding the instinct key, for this target only": a keybind event result and Wathe's null-result default (which
 * is gated by {@code isInstinctEnabledAndIsKiller()}) both fall to -1, and so does every HEAD answer, RETURN fallback
 * and listener that keys its x-ray on those gates, even one that answers {@code always} only while the key is held
 * (the Insider, the SparkStrength Corrupt Cop). Highlights that need no key ({@code always} event results, Feather
 * marks, Fiend Moment, Judge sentence, Vendetta, Guardian Shield) are untouched. Presentation only: nothing is sent.
 * The evaluation depth is per thread, so a nested evaluation (the Magician puppet asks again for its copied player)
 * restores correctly and no other thread ever reads the gates closed.
 * 仅客户端。USEC 开镜本能隐蔽（所有者 2026-10-08），由 {@code client/mixin/usec/UsecInstinctCloakMixin} 驱动。对被隐蔽的
 * 目标（见 {@link UsecInstinctCloakRules}），Wathe 的整个 {@code getInstinctHighlight} 以“隐蔽求值”运行，其间 Wathe 的两个
 * 按键本能入口 {@code isInstinctEnabled()} 与 {@code isInstinctEnabledAndIsKiller()} 读为 false。这正是“仅对该目标而言，
 * 观察者没有按住本能键”：需按键的事件结果与 Wathe 无事件结果时的默认逻辑（由 {@code isInstinctEnabledAndIsKiller()}
 * 门控）都落为 -1，所有依这两个入口决定透视的 HEAD 结果、RETURN 兜底与监听器也一样，包括只在按住按键时才回答
 * {@code always} 的监听器（内应、SparkStrength 黑警）。无需按键的高亮（{@code always} 事件结果、羽刃标记、魔人时刻、
 * 大法官判决、仇杀客、守护护盾）不受影响。仅为显示，不发送任何数据。求值深度按线程记录，因此嵌套求值（魔术师皮套
 * 为其复制的玩家再次询问）能正确恢复，其他线程也永远不会读到关闭的入口。
 */
public final class UsecInstinctCloak {
    private static final ThreadLocal<int[]> CLOAKED_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private UsecInstinctCloak() {
    }

    /**
     * Runs {@code evaluation}, inside a cloaked evaluation when {@code cloaked}; nests and always restores.
     * 执行 {@code evaluation}；{@code cloaked} 时处于隐蔽求值内；可嵌套且总会恢复。
     */
    public static int evaluate(boolean cloaked, IntSupplier evaluation) {
        if (!cloaked) {
            return evaluation.getAsInt();
        }
        int[] depth = CLOAKED_DEPTH.get();
        depth[0]++;
        try {
            return evaluation.getAsInt();
        } finally {
            depth[0]--;
        }
    }

    /** Whether this thread is inside a cloaked evaluation. / 当前线程是否处于隐蔽求值内。 */
    public static boolean inCloakedEvaluation() {
        return CLOAKED_DEPTH.get()[0] > 0;
    }

    /**
     * Whether the local viewer must not see {@code target} through keyed instinct right now. Cheapest test first:
     * nearly every entity is not a scoped player.
     * 本地观察者此刻是否不得通过按键本能看到 {@code target}。最便宜的判断在前：几乎所有实体都不是开镜玩家。
     */
    public static boolean cloaks(Entity target) {
        if (!(target instanceof PlayerEntity player)) {
            return false;
        }
        UsecPlayerComponent component = UsecPlayerComponent.KEY.getNullable(player);
        if (component == null || !component.isScoped()) {
            return false;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }
        return UsecInstinctCloakRules.cloaks(
                SparkWitchServerConnection.isConfirmedServer(),
                WatheClient.canSeeSpectatorInformation(),
                viewer == player || viewer.getUuid().equals(player.getUuid()),
                true,
                viewer.squaredDistanceTo(player)
        );
    }
}
