package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the M2A1 range dial ({@link M32RangeDial}): wheel stepping on the 25 m grid of
 * {@link M32Ballistics#dialSettings()}, the holding-session test and the launch pitch of an aimed shot. No Minecraft
 * state, so all are unit-tested.
 * M2A1 射程刻度（{@link M32RangeDial}）的纯规则：在 {@link M32Ballistics#dialSettings()} 的 25 米网格上按滚轮步进、
 * 持握会话判定，以及开镜射击的发射俯仰角。不涉及 Minecraft 状态，均有单元测试。
 */
public final class M32DialRules {
    /** Where every holding session starts. / 每个持握会话的起始刻度。 */
    public static final int DEFAULT_METRES = M32Ballistics.DIAL_MIN_METRES;
    /** Dial ticks pitch: range up / range down. / 刻度音调：射程增大 / 射程减小。 */
    public static final float PITCH_UP = 1.0F;
    public static final float PITCH_DOWN = 1.06F;
    /** Most settings one wheel event can cross; larger deltas are clamped first. / 单次滚轮最多跨越的刻度数。 */
    private static final int MAX_STEPS = (M32Ballistics.DIAL_MAX_METRES - M32Ballistics.DIAL_MIN_METRES)
            / M32Ballistics.DIAL_STEP_METRES;

    private M32DialRules() {
    }

    /**
     * One wheel delta. Like vanilla's {@code Scroller}, a delta against the carried fraction's sign drops the
     * fraction first; the sum's whole part (toward zero) moves that many settings (positive = range up) and the
     * fraction carries. Movement stops at 25 and 200 m: {@link Step#moved()} counts only the settings actually
     * crossed, so a clamped step plays no tick. A non-finite or zero delta changes nothing.
     * 一次滚轮增量。与原版 {@code Scroller} 一样，与携带小数符号相反的增量先丢弃该小数；总和的整数部分（向零取整）移动
     * 相应刻度数（正数为射程增大），小数部分继续携带。移动止于 25 与 200 米：{@link Step#moved()} 只计实际跨过的刻度，
     * 因此被钳制的步进不播放刻度音。非有限或为零的增量不做任何改变。
     */
    public static Step step(int metres, double accumulated, double notches) {
        int current = M32Ballistics.clampDial(metres);
        double carried = Double.isFinite(accumulated) ? accumulated : 0.0;
        if (!Double.isFinite(notches) || notches == 0.0) {
            return new Step(current, carried, 0);
        }
        if (carried != 0.0 && Math.signum(carried) != Math.signum(notches)) {
            carried = 0.0;
        }
        double sum = carried + notches;
        double whole = sum < 0.0 ? Math.ceil(sum) : Math.floor(sum);
        double remainder = sum - whole;
        if (whole == 0.0) {
            return new Step(current, remainder, 0);
        }
        int steps = (int) Math.max(-MAX_STEPS, Math.min(MAX_STEPS, whole));
        int target = M32Ballistics.clampDial(current + steps * M32Ballistics.DIAL_STEP_METRES);
        return new Step(target, remainder, (target - current) / M32Ballistics.DIAL_STEP_METRES);
    }

    /**
     * Whether the dial's holding session survives: the main hand still holds an M32A1 ({@code holding}), for the same
     * player object, in the same world object, in the same selected hotbar slot. Identity, never equality: a respawn
     * or world change is a new object. The launcher stack itself is deliberately not part of the key (the server's
     * component sync replaces it after every shot).
     * 刻度的持握会话是否延续：主手仍持 M32A1（{@code holding}），且为同一玩家对象、同一世界对象、同一选中快捷栏格。比较身份
     * 而非相等：重生或换世界即为新对象。发射器物品本身刻意不纳入判定（服务端的组件同步在每次射击后都会替换它）。
     */
    public static boolean keepsSession(@Nullable Object sessionPlayer, @Nullable Object sessionWorld, int sessionSlot,
                                       boolean holding, @Nullable Object player, @Nullable Object world, int slot) {
        return holding && sessionPlayer != null && sessionWorld != null && player == sessionPlayer
                && world == sessionWorld && slot == sessionSlot;
    }

    /**
     * The pitch the fire intent carries (Minecraft degrees, positive looks down). Hip fire leaves along the look. An
     * aimed shot (the M2A1 scope profile is active) leaves above the look by the bore elevation that lands the round
     * {@code dialMetres} away on the line of sight ({@link M32Ballistics#elevationToHitRadians}), so the reticle at
     * the screen centre is the aim point at the dialled range; the sight draws the launcher nosed up by that same
     * angle. The whole elevation applies as soon as the profile is active, the 0.2 s the sight takes to come up
     * included (the drawn nose-up eases in; the input state, not the animation, decides the shot). No solution (the
     * round cannot reach that far along this look before the fuse) or a non-finite look falls back to the look itself;
     * the result never passes straight up.
     * 开火意图携带的俯仰角（Minecraft 角度，正值朝下）。腰射沿视线射出。开镜射击（M2A1 瞄具配置生效时）在视线之上再抬高一个
     * 枪膛抬高角，使榴弹落在视线上 {@code dialMetres} 远处（{@link M32Ballistics#elevationToHitRadians}），因此屏幕中心的分划
     * 就是当前刻度下的瞄准点；瞄具把发射器画成抬头同样的角度。配置一生效就应用完整的抬高角，包括瞄具抬起的 0.2 秒
     * （绘制的抬头是缓动的；决定射击的是输入状态而非动画）。无解（沿该视线在引信前飞不到这么远）或视线非有限时回退到视线
     * 本身；结果不会越过正上方。
     */
    public static float launchPitch(float lookPitch, boolean aiming, int dialMetres) {
        if (!aiming || !Float.isFinite(lookPitch)) {
            return lookPitch;
        }
        double elevation = M32Ballistics.elevationToHitRadians(lookPitch, dialMetres);
        if (!Double.isFinite(elevation)) {
            return lookPitch;
        }
        return (float) Math.max(-90.0, lookPitch - Math.toDegrees(elevation));
    }

    /**
     * The result of {@link #step}: the new setting, the fraction carried to the next delta, and the signed number of
     * settings crossed (one dial tick each).
     * {@link #step} 的结果：新刻度、携带到下一次增量的小数，以及带符号的跨越刻度数（每个一声刻度音）。
     */
    public record Step(int metres, double remainder, int moved) {
    }
}
