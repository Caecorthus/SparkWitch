package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Per-variant callbacks run by the shared Grand Witch recruitment transaction.
 * 共享的大魔女招募事务为每个特殊共犯调用的回调。
 */
public interface AccompliceVariantHooks {
    AccompliceVariantHooks NONE = new AccompliceVariantHooks() {
    };

    /**
     * Runs once after a recruitment into this variant committed: the role map is written, {@code RoleAssigned}
     * fired, the retained inventory restored, the balance overwritten and the shop re-initialized. Grant bound
     * starting items here; items granted from a {@code RoleAssigned} listener are wiped by the inventory restore.
     * Server thread only; a thrown exception is logged and never undoes the committed recruitment.
     * 招募为该特殊共犯并提交后调用一次：此时身份已写入、{@code RoleAssigned} 已触发、保留背包已恢复、余额已覆盖、商店已重置。
     * 请在这里发放绑定的初始物品；在 {@code RoleAssigned} 监听器中发放的物品会被背包恢复抹掉。仅服务端线程；
     * 抛出的异常只会被记录，不会撤销已提交的招募。
     */
    default void afterRecruitCommitted(ServerPlayerEntity recruit, ServerPlayerEntity recruiter) {
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
