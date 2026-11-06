package dev.caecorthus.sparkwitch.client.bear.hearing;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * Frozen contract (client thread only): the local player's BEAR hearing state that other BEAR packages call. The
 * headset/hearing package (WP4) implements it; until then every call is a no-op and {@link #factor()} is 1.
 * <ul>
 *   <li>{@link #factor()}: the volume multiplier applied to every scaled in-game sound and to voice chat
 *   ({@code BearHearingRules.factorFor} of the local player, times any active {@link #muffle}).</li>
 *   <li>{@link #muffle}: a ricochet daze muffles world sounds for {@code ticks} (WP3 calls it).</li>
 *   <li>{@link #EXEMPT_SOUND_IDS}: the wearer's own helmet ricochet / visor hit / tinnitus sounds ignore the factor.</li>
 * </ul>
 * 冻结契约（仅客户端线程）：本地玩家的 BEAR 听力状态，供其他 BEAR 工作包调用。由耳机/听力工作包（WP4）实现；在此之前所有调用
 * 都不做任何事，{@link #factor()} 恒为 1。{@link #factor()}：作用于所有受影响的游戏内声音与语音的音量倍数（本地玩家的
 * {@code BearHearingRules.factorFor}，再乘以生效中的 {@link #muffle}）；{@link #muffle}：跳弹眩晕让世界声音在 {@code ticks} 内发闷
 * （由 WP3 调用）；{@link #EXEMPT_SOUND_IDS}：佩戴者自己的头盔跳弹、面罩命中与耳鸣声不受该倍数影响。
 */
public final class BearHearingClient {
    /** Sound event ids the factor never scales. / 不受倍数影响的声音事件 id。 */
    public static final Set<Identifier> EXEMPT_SOUND_IDS = Set.of(
            SparkWitch.id("bear.rys_t.ricochet"),
            SparkWitch.id("bear.rys_t.visor_hit"),
            SparkWitch.id("bear.rys_t.tinnitus"));

    private BearHearingClient() {
    }

    /** Current volume multiplier for the local player (1 when unaffected). / 本地玩家当前的音量倍数（不受影响时为 1）。 */
    public static float factor() {
        return 1.0F;
    }

    /**
     * Muffles world sounds to {@code volume} (0..1) for {@code ticks}, easing back afterwards; a longer or deeper call
     * wins over a running one. / 让世界声音在 {@code ticks} 内降到 {@code volume}（0..1），之后渐渐恢复；更长或更深的调用
     * 覆盖正在进行的。
     */
    public static void muffle(int ticks, float volume) {
        // WP4 implements. / 由 WP4 实现。
    }
}
