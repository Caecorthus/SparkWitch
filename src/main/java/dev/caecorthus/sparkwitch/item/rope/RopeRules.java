package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * Every number of the Rope ({@code sparkwitch:rope}, 二阶堂希罗's shop item). Owner spec: use it on a player within
 * {@link #RANGE} blocks whose back is toward the user (the user stands inside the {@link #BACK_HALF_ARC_DEGREES}
 * half-arc behind the target); both are frozen for {@link #CHANNEL_TICKS}, the target takes a cosmetic vanilla hit
 * every {@link #HIT_INTERVAL_TICKS}, then dies of {@code sparkwitch:strangled}, bypassing shields without consuming
 * them. An attacked or controlled user aborts and the rope cools down for {@link #INTERRUPT_COOLDOWN_TICKS}; the rope is
 * consumed only by a successful kill.
 * 绳索（{@code sparkwitch:rope}，二阶堂希罗的商店道具）的全部数值。所有者规格：对 {@link #RANGE} 格内背对使用者的玩家使用
 * （使用者位于目标身后 {@link #BACK_HALF_ARC_DEGREES} 的半弧内）；双方定身 {@link #CHANNEL_TICKS}，目标每
 * {@link #HIT_INTERVAL_TICKS} 受到一次仅表现的原版受击，随后以 {@code sparkwitch:strangled} 死亡，无视护盾且不消耗护盾。
 * 使用者被攻击或被控制时中断，绳索进入 {@link #INTERRUPT_COOLDOWN_TICKS} 冷却；只有击杀成功才消耗绳索。
 */
public final class RopeRules {
    public static final Identifier ITEM_ID = SparkWitch.id("rope");
    public static final double RANGE = 2.0D;
    // Half-width of the arc behind the target, measured from straight behind. / 目标身后弧的半角，从正后方算起。
    public static final double BACK_HALF_ARC_DEGREES = 60.0D;
    public static final int CHANNEL_TICKS = 60;
    public static final int HIT_INTERVAL_TICKS = 20;
    public static final int INTERRUPT_COOLDOWN_TICKS = 200;
    public static final int PRICE = 50;

    private RopeRules() {
    }
}
