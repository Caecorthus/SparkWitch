package dev.caecorthus.sparkwitch.roles.killer.hiro.witchification;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import java.util.function.IntPredicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Identifier;

/**
 * Pure decisions of 二阶堂希罗's 魔女化 (owner rules 2026-11-11): which kills count, the once-per-round step, the shape of
 * the Speed she owns and where the derringer may land. The threshold itself is {@code HiroRules.requiredKills}.
 * 二阶堂希罗「魔女化」的纯规则（所有者 2026-11-11）：哪些击杀计数、每局仅一次的结算、她所拥有的速度效果的形态，以及德林加手枪可以
 * 放入的栏位。所需击杀数本身是 {@code HiroRules.requiredKills}。
 */
public final class HiroWitchificationRules {
    /** Speed II. / 速度 II。 */
    public static final int SPEED_AMPLIFIER = 1;
    /** Wathe replay global event recorded once when she witchifies. / 魔女化时记录一次的 Wathe 回放全局事件。 */
    public static final Identifier REPLAY_EVENT_ID = SparkWitch.id("nikaido_hiro_witchified");

    private HiroWitchificationRules() {
    }

    /** Progress after one counted kill; {@code witchifiesNow} is true exactly once. / 计入一次击杀后的进度；{@code witchifiesNow} 只会为真一次。 */
    public record Step(int kills, int required, boolean witchified, boolean witchifiesNow) {
    }

    /**
     * A kill counts when Wathe committed it ({@code KillPlayer.AFTER}) for a victim other than the responsible player,
     * SparkTraits did not intercept the death, the round is ACTIVE and the responsible player is a raw-role Hiro who
     * is not dead in Wathe. Her body does not matter: swallowed by a Taotie or inside a Rift Gate she is a spectator
     * but alive, and a poison or grenade kill committing then still counts. The victim's role or faction does not
     * matter.
     * 击杀计数的条件：Wathe 已提交该击杀（{@code KillPlayer.AFTER}）、受害者不是责任人本人、SparkTraits 未拦截这次死亡、对局处于
     * ACTIVE，且责任人是在 Wathe 中未死亡的真实身份希罗。与她的身体状态无关：被饕餮吞下或身处裂隙门内时她是旁观者但仍然存活，此时
     * 结算的毒杀或手雷击杀照样计数。受害者的职业与阵营不影响计数。
     */
    public static boolean countsKill(boolean activeRound, boolean responsibleIsHiroAliveInWathe, boolean selfKill,
                                     boolean deathIntercepted) {
        return activeRound && responsibleIsHiroAliveInWathe && !selfKill && !deathIntercepted;
    }

    /**
     * The threshold is reached but the 魔女化 moment has not happened yet: the deciding kill was counted while she was
     * out of her body (swallowed, inside a Rift Gate), where no factor, Speed or derringer can be handed over. The
     * tick performs the moment once she is a living Hiro in her own body again. A threshold below 1 is treated as 1,
     * so an unset component is never pending.
     * 已达到所需击杀数但「魔女化」尚未发生：决定性的那次击杀是在她不在自己身体里（被吞下、身处裂隙门内）时计入的，此时无法发放因子、
     * 速度与德林加手枪。待她重新成为身体内存活的希罗时，由每刻逻辑补上这一刻。小于 1 的所需数按 1 处理，因此未设置的组件永不处于待定。
     */
    public static boolean isPending(int kills, int required, boolean witchified) {
        return !witchified && Math.max(0, kills) >= Math.max(1, required);
    }

    /**
     * Adds one counted kill. Once witchified nothing changes any more, so the rewards can never repeat in a round; a
     * threshold below 1 is treated as 1.
     * 计入一次击杀。魔女化之后不再变化，因此奖励在一局内不会重复；小于 1 的所需数按 1 处理。
     */
    public static Step afterCountedKill(int kills, int required, boolean witchified) {
        int threshold = Math.max(1, required);
        if (witchified) {
            return new Step(Math.max(0, kills), threshold, true, false);
        }
        int next = Math.max(0, kills) + 1;
        boolean reached = next >= threshold;
        return new Step(next, threshold, reached, reached);
    }

    /**
     * Wind Spirit pattern: (re)grant the infinite Speed II when she has no Speed, a weaker one, or a Speed II that
     * would run out. A stronger Speed is left alone until it ends.
     * 风精灵模式：没有速度、速度较弱、或速度 II 会到期时（重新）授予无限速度 II；更强的速度在其结束前不做处理。
     */
    public static boolean shouldGrantSpeed(boolean present, int amplifier, boolean infinite) {
        return !present || amplifier < SPEED_AMPLIFIER || amplifier == SPEED_AMPLIFIER && !infinite;
    }

    /**
     * Exactly the effect this passive grants: infinite Speed II, not ambient, no particles, icon shown. Only an
     * instance of this shape is ever removed.
     * 恰为本被动授予的效果：无限速度 II、非环境、无粒子、显示图标。只有这种形态的实例才会被移除。
     */
    public static boolean isOwnSpeedShape(int amplifier, boolean infinite, boolean ambient, boolean particles,
                                          boolean icon) {
        return amplifier == SPEED_AMPLIFIER && infinite && !ambient && !particles && icon;
    }

    /**
     * First empty slot Wathe's limited inventory shows: hotbar 0-8, then 27-35 only while SparkFactionAPI's second
     * row is shown; never the hidden 9-26, where {@code PlayerInventory#insertStack} would otherwise bury the item.
     * Returns -1 when every shown slot is taken.
     * Wathe 受限物品栏显示的第一个空栏位：快捷栏 0-8，然后仅在 SparkFactionAPI 第二行显示时为 27-35；绝不使用隐藏的 9-26
     * （{@code PlayerInventory#insertStack} 否则会把物品埋进那里）。可见栏位全满时返回 -1。
     */
    public static int shownEmptySlot(boolean secondRowShown, IntPredicate emptySlot) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (emptySlot.test(slot)) {
                return slot;
            }
        }
        if (secondRowShown) {
            for (int slot = SparkFactionSecondRowCompat.SECOND_ROW_START;
                 slot < SparkFactionSecondRowCompat.SECOND_ROW_END; slot++) {
                if (emptySlot.test(slot)) {
                    return slot;
                }
            }
        }
        return -1;
    }
}
