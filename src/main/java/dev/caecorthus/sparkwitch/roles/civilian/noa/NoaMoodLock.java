package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: Noa's sanity lock (owner decision, PR #186). Only Wathe's OWN per-task passive drain and OWN +0.5
 * task-completion gain inside {@code PlayerMoodComponent} are zeroed for a Noa; every other {@code setMood} (Fear,
 * Perfumer, the gun -0.35, items, other roles, SparkTraits) still lands, and Wathe's mood &lt;= -1 breakdown still
 * kills her. The decisions are applied by {@code mixin/noa/PlayerMoodComponentNoaMoodLockMixin} on both sides; the
 * client reads the synced role map, so its local drain prediction stays locked too.
 * 稳定契约：诺亚的理智锁定（所有者决定，PR #186）。只把 {@code PlayerMoodComponent} 内 Wathe 自身的按任务被动流失与
 * 自身的完成任务 +0.5 回复对诺亚清零；其他所有 {@code setMood}（恐惧、调香师、开枪 -0.35、道具、其他职业、SparkTraits）
 * 照常生效，理智 &lt;= -1 时 Wathe 的精神崩溃照样杀死她。决策由 {@code mixin/noa/PlayerMoodComponentNoaMoodLockMixin}
 * 在双端执行；客户端读取已同步的职业表，因此本地流失预测同样被锁定。
 */
public final class NoaMoodLock {

    private NoaMoodLock() {
    }

    /** Whether the lock applies to this role. / 该职业是否适用锁定。 */
    public static boolean locks(@Nullable Role role) {
        return NoaRules.isNoa(role);
    }

    /**
     * Both sides: whether {@code player} currently holds the Noa role (Wathe's synced role map).
     * 双端：{@code player} 当前是否为诺亚（读取 Wathe 已同步的职业表）。
     */
    public static boolean locks(PlayerEntity player) {
        return locks(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Wathe's drain operand {@code tasks.size()}, possibly already raised by other handlers on the same read. A locked
     * player's own pending tasks count zero; pseudo tasks other mixins add (the Abyss Listener Deep Dark Zone +1) still
     * count, whichever handler runs first, because only the real task count is subtracted.
     * Wathe 下降操作数 {@code tasks.size()}，可能已被同一读取上的其他处理器提高。被锁定玩家自己的待办任务计为零；
     * 其他 mixin 追加的临时任务（聆渊者深暗领域 +1）仍然计入，与处理器先后顺序无关，因为只减去真实任务数。
     */
    public static int drainingTaskCount(int drainingTasks, int realTasks, boolean locked) {
        return locked ? Math.max(0, drainingTasks - realTasks) : drainingTasks;
    }

    /**
     * Wathe's {@code mood + 0.5F} on task completion: no gain for a locked player. The task still completes and
     * {@code TaskComplete} still fires (her 50 coins).
     * Wathe 完成任务时的 {@code mood + 0.5F}：被锁定玩家不回复。任务照常完成，{@code TaskComplete} 照常触发（她的 50 金币）。
     */
    public static float taskCompletionGain(float gain, boolean locked) {
        return locked ? 0.0F : gain;
    }
}
