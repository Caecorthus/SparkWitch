package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * USEC terminal death (plan Q2/D9): the rifle and its parts are deleted, never dropped, and exactly one Wathe revolver
 * drops at the victim, as Wathe's own death loop drops ({@code dropItem(stack, true, false)}), so normal Wathe gun
 * pickup rules apply. Server only. Wathe's drop loop runs before {@code KillPlayer.AFTER}, so the pre-death fact "a
 * revolver already left this USEC as an item entity" is captured at the drop itself
 * ({@code PlayerEntityUsecItemMixin} RETURN, through {@link #noteDrop}) and read back in AFTER of the same server
 * tick: a carried revolver dropped by Wathe's loop, or a revolver Wathe's misfire punishment threw right before
 * killing the shooter, already is "the revolver that drops", so no second one spawns.
 * USEC 最终死亡（计划 Q2/D9）：步枪及其零件被删除、绝不掉落，并在死者处恰好掉落一把 Wathe 左轮，掉落方式与 Wathe 自身的
 * 死亡循环相同（{@code dropItem(stack, true, false)}），因此适用 Wathe 常规拾枪规则。仅服务端。Wathe 的掉落循环先于
 * {@code KillPlayer.AFTER} 执行，所以“已有一把左轮作为物品实体离开该 USEC”这一死前事实在掉落发生时捕获
 * （{@code PlayerEntityUsecItemMixin} 的 RETURN，经 {@link #noteDrop}），并在同一服务器刻的 AFTER 中读回：被 Wathe 循环掉落的
 * 随身左轮，或 Wathe 误杀惩罚在处死射手前抛出的左轮，已经就是“掉落的那把左轮”，因此不会再生成第二把。
 */
public final class UsecDeathDrops {
    /**
     * World tick at which a revolver item entity last left each USEC. Server thread only; consumed by
     * {@link #afterKill}, stale after its tick, cleared on reset, disconnect, finalize and server stop.
     * 每名 USEC 最近一次有左轮物品实体离身的世界刻。仅服务端线程；由 {@link #afterKill} 消费，过了该刻即失效，
     * 并在重置、断线、收尾与服务器停止时清除。
     */
    private static final Map<UUID, Long> REVOLVER_DROPPED_AT = new HashMap<>();

    private UsecDeathDrops() {
    }

    /**
     * Mixin seam ({@code PlayerEntityUsecItemMixin}, RETURN of {@code PlayerEntity#dropItem(ItemStack, boolean,
     * boolean)}): records a revolver item entity created for a USEC on the server. A drop cancelled at HEAD (a
     * Last Stand revolver kept by SparkTraits, a Coroner temporary grant) never reaches RETURN and is not recorded.
     * mixin 接缝（{@code PlayerEntityUsecItemMixin}，{@code PlayerEntity#dropItem(ItemStack, boolean, boolean)} 的 RETURN）：
     * 记录服务端为 USEC 生成的左轮物品实体。在 HEAD 被取消的丢弃（SparkTraits 背水一战保留的左轮、验尸官的临时装备）不会到达
     * RETURN，因此不被记录。
     */
    public static void noteDrop(PlayerEntity player, @Nullable ItemEntity dropped) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || dropped == null
                || !dropped.getStack().isOf(WatheItems.REVOLVER)
                || !UsecRules.isUsec(GameWorldComponent.KEY.get(serverPlayer.getWorld()).getRole(serverPlayer))) {
            return;
        }
        REVOLVER_DROPPED_AT.put(serverPlayer.getUuid(), serverPlayer.getWorld().getTime());
    }

    /**
     * {@code KillPlayer.AFTER} for every victim. A SparkTraits-intercepted death (Last Stand; the same fail-closed
     * check as the Seeker) keeps everything. Otherwise every bound item is stripped from any victim (a non-USEC holder
     * included) and the component is cleared; a USEC victim also drops one revolver unless one already dropped this
     * tick.
     * 对每名受害者执行的 {@code KillPlayer.AFTER}。被 SparkTraits 拦截的死亡（背水一战；与搜寻者相同的保守失败判定）保留一切。
     * 否则从任何受害者身上（含非 USEC 持有者）清除所有绑定物品并清空组件；USEC 受害者还会掉落一把左轮，除非本刻已经掉落过一把。
     */
    static void afterKill(ServerPlayerEntity victim) {
        Long droppedAt = REVOLVER_DROPPED_AT.remove(victim.getUuid());
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        boolean usec = UsecRules.isUsec(GameWorldComponent.KEY.get(victim.getWorld()).getRole(victim));
        UsecLoadoutService.cleanUp(victim);
        if (dropsRevolver(usec, droppedThisTick(droppedAt, victim.getWorld().getTime()))) {
            victim.dropItem(new ItemStack(WatheItems.REVOLVER), true, false);
        }
    }

    /** Pure: one revolver per USEC death, never a second. / 纯函数：每次 USEC 死亡一把左轮，绝不第二把。 */
    static boolean dropsRevolver(boolean usec, boolean revolverAlreadyDropped) {
        return usec && !revolverAlreadyDropped;
    }

    /** Pure: a recorded drop counts only within the same world tick. / 纯函数：记录的掉落只在同一世界刻内有效。 */
    static boolean droppedThisTick(@Nullable Long droppedAt, long now) {
        return droppedAt != null && droppedAt == now;
    }

    static void forget(PlayerEntity player) {
        REVOLVER_DROPPED_AT.remove(player.getUuid());
    }

    static void forgetAll() {
        REVOLVER_DROPPED_AT.clear();
    }
}
