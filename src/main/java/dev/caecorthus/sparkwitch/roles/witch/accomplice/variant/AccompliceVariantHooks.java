package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Per-variant callbacks run by the shared Bewitched promotion transaction.
 * 共享的魔化使晋升事务为每个特殊共犯调用的回调。
 */
public interface AccompliceVariantHooks {
    AccompliceVariantHooks NONE = new AccompliceVariantHooks() {
    };

    /**
     * Runs once after a Bewitched promotion into this variant committed: the role map is written, {@code RoleAssigned}
     * fired, the earned balance restored and the shop re-initialized; the inventory is kept as it was. Grant bound
     * starting items here, idempotently (a {@code RoleAssigned} grant may already be present). Server thread only; a
     * thrown exception is logged and never undoes the committed promotion.
     * 魔化使晋升为该特殊共犯并提交后调用一次：此时身份已写入、{@code RoleAssigned} 已触发、已赚取的余额已恢复、商店已重置；
     * 背包保持原样。请在这里幂等地发放绑定的初始物品（{@code RoleAssigned} 可能已经发放过）。仅服务端线程；抛出的异常只会被
     * 记录，不会撤销已提交的晋升。
     */
    default void afterPromotionCommitted(ServerPlayerEntity player) {
    }

    /**
     * The witch-skill ids this variant owns and may show in the top-left {@code gui.sparkwitch.skills} inventory panel
     * (owner decision D13); empty means the variant never uses the panel. Only these ids are shown, never another
     * registered skill. Read every client frame, so return a constant immutable set.
     * 该特殊共犯拥有、可在背包左上角 {@code gui.sparkwitch.skills} 技能面板展示的魔女技能 ID（所有者决定 D13）；为空表示
     * 不使用该面板。只展示这些 ID，绝不展示其他已注册技能。客户端每帧读取，请返回不可变常量集合。
     */
    default Set<Identifier> ownSkillIds() {
        return Set.of();
    }
}
