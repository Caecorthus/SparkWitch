package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecFireRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Scoped AXMC bolt sway (owner, 2026-10-08): while the shooter looks through the scope, working the bolt after a shot
 * shakes the view in step with the bolt sound. Pure rules, curve and per-player state; {@link UsecBoltSwayClient} feeds
 * it once per client tick and {@code UsecBoltSwayCameraMixin} reads it per frame. Purely visual, like the recoil kick:
 * an extra view rotation on the camera transform, so the player's rotation, the crosshair ray and every server check
 * are untouched (the rifle cannot fire during the bolt cooldown anyway).
 * <p>
 * Trigger: a fire request the local client just sent whose synced state chambers a new round
 * ({@link #chambersRound}, the server's own condition for scheduling the bolt sound) arms a short pending window; the
 * first synced bolt cooldown entry ({@link UsecCooldowns.Status#bolt()}) seen inside it confirms the server accepted
 * the shot and anchors the timeline at the entry's start tick. The cooldown packet and the bolt sound packet share the
 * one-way latency, so the anchor stays in step with the sound at any ping. An empty-chamber click (no cooldown, nothing
 * armed), the last round (no round chambered, no sound), another player's shot (it never touches the local cooldown or
 * the local send) and the attachment-screen bolts (no local shot, and a screen clears the pending shot) never sway.
 * <p>
 * Feel (owner, 2026-10-08, Escape from Tarkov's scoped bolt cycling as the reference): the rifle cants a few degrees
 * as the hand lifts and pulls the handle, the sight dips and drifts to one side, gets jolted on the rear and forward
 * clacks, then swings back onto the aim point as the handle locks and settles. Angles are world angles in degrees
 * (yaw &gt; 0 turns the view right, pitch &gt; 0 tilts it up, roll &gt; 0 rolls the camera clockwise), so the scope's
 * magnification enlarges the yaw/pitch jolts like real optics while the cant reads the same at every zoom.
 * 开镜 AXMC 拉栓晃动（所有者 2026-10-08）：射手通过瞄准镜观察时，射击后的拉栓会随拉栓声晃动视角。这里是纯规则、曲线与
 * 本地玩家状态；{@link UsecBoltSwayClient} 每个客户端刻喂入一次，{@code UsecBoltSwayCameraMixin} 每帧读取。与后坐抖动一样
 * 纯视觉：只在镜头变换上追加旋转，玩家朝向、准星射线与所有服务端检查都不变（拉栓冷却期间本就无法开火）。
 * <p>
 * 触发：本地客户端刚发出的开火请求若在同步状态下会有新子弹上膛（{@link #chambersRound}，即服务端安排拉栓声的条件），就进入
 * 短暂的待确认窗口；窗口内看到的第一个同步拉栓冷却条目（{@link UsecCooldowns.Status#bolt()}）确认服务端接受了这次射击，
 * 并以该条目的起始刻为时间轴锚点。冷却数据包与拉栓声数据包经过同一段单程延迟，因此任何延迟下锚点都与声音同步。空膛点击
 * （无冷却、不预备）、最后一发（无新子弹上膛、无声音）、其他玩家的射击（从不影响本地冷却或本地发送）以及配件界面中的拉栓
 * （没有本地射击，且打开界面会清除待确认射击）都不会晃动。
 * <p>
 * 手感（所有者 2026-10-08，以《逃离塔科夫》开镜拉栓为参照）：手抬起并后拉拉柄时枪身倾斜几度，视野下沉并向一侧漂移，在后拉
 * 与前推撞击时被震动，随后在拉柄闭锁时摆回瞄准点并回稳。角度为世界角度（度）：偏航 &gt; 0 视角右转，俯仰 &gt; 0 视角上抬，
 * 横滚 &gt; 0 镜头顺时针滚转；因此瞄准镜放大倍率会像真实光学一样放大偏航/俯仰抖动，而倾斜在任何倍率下看起来都一样。
 */
public final class UsecBoltSway {
    public static final double TICKS_PER_SECOND = 20.0;
    /**
     * Cycle ticks (since the synced bolt cooldown entry started) at which the bolt sound starts. The server schedules
     * the sound {@link UsecFireRules#BOLT_SOUND_DELAY_TICKS} ticks after the shot on Wathe's Scheduler, which runs it at
     * the end of its last server tick (one tick less plus the rest of the shot's tick), and the client sees the entry
     * start up to a tick after its packet arrived; half a tick earlier than the nominal delay centres both.
     * 拉栓声开始时的循环刻（自同步拉栓冷却条目开始起算）。服务端在 Wathe 调度器上于射击后 {@link UsecFireRules#BOLT_SOUND_DELAY_TICKS}
     * 刻安排声音，调度器在最后一个服务端刻结束时执行（少一刻再加射击所在刻的剩余部分），而客户端最多在数据包到达一刻后才看到
     * 条目开始；比名义延迟早半刻可使两者居中。
     */
    public static final double SOUND_START_TICKS = UsecFireRules.BOLT_SOUND_DELAY_TICKS - 0.5;

    // The four transients of item.usec_rifle.bolt (1.0 s, archived synth notes), in seconds from the sound start.
    // item.usec_rifle.bolt（1.0 秒，归档合成说明）的四个瞬态，单位为自声音开始起的秒数。
    /** Handle lift click. / 拉柄抬起的咔嗒声。 */
    public static final double HANDLE_LIFT_S = 0.020;
    /** Bolt pulled back, rear clack. / 枪机后拉到位的撞击声。 */
    public static final double REAR_CLACK_S = 0.300;
    /** Bolt pushed forward, the heavier clack as the round seats. / 枪机前推、子弹入膛的较重撞击声。 */
    public static final double FORWARD_CLACK_S = 0.605;
    /** Handle down and lock. / 拉柄压下闭锁。 */
    public static final double LOCK_S = 0.800;

    // Slow hand motion between the transients: quintic ramps, seconds from the sound start. The handle-up ramp centres
    // on the lift click, the pull and push ramps end on their clacks, and the return straddles the lock so the sight
    // swings back onto the aim point as the handle locks (done by 0.86 s, before Fast Reload's 0.925 s cooldown end).
    // 瞬态之间的缓慢手部动作：五次平滑过渡，单位为自声音开始起的秒数。抬柄过渡以抬柄咔嗒声为中心，后拉与前推过渡结束于
    // 各自的撞击声，回位过渡跨过闭锁，使视野在拉柄闭锁时摆回瞄准点（0.86 秒完成，早于快速装填 0.925 秒的冷却结束）。
    static final double HANDLE_UP_FROM_S = -0.040;
    static final double HANDLE_UP_SPAN_S = 0.140;
    static final double PULL_FROM_S = 0.060;
    static final double PULL_SPAN_S = REAR_CLACK_S - PULL_FROM_S;
    static final double PUSH_FROM_S = 0.390;
    static final double PUSH_SPAN_S = FORWARD_CLACK_S - PUSH_FROM_S;
    static final double RETURN_FROM_S = 0.640;
    static final double RETURN_SPAN_S = 0.220;

    // ---- Amplitudes, world degrees (Tarkov-style feel, owner 2026-10-08). Most of the visible motion is the cant
    // (roll), which no zoom magnifies, plus a dip and a sideways drift; the clack jolts are sharper but smaller
    // because the scope magnifies yaw and pitch (0.39° forward jolt ≈ 3 % of the view height at 6×). Each slow phase
    // adds (yaw, pitch, roll): the rifle cants to about -3.5° by the rear clack, dips about 0.56° and drifts about
    // 0.42° to the right, then eases back a little on the push. Per-cycle gains are 1 ± GAIN_SPREAD.
    // 振幅，世界角度（塔科夫式手感，所有者 2026-10-08）。可见动作主要是倾斜（横滚，任何倍率都不会放大它），加上下沉与侧向漂移；
    // 撞击抖动更尖锐但更小，因为瞄准镜会放大偏航与俯仰（0.39° 的前推抖动在 6 倍下约为视野高度的 3%）。每个慢动作阶段叠加
    // （偏航、俯仰、横滚）：到后拉撞击时枪身倾斜约 -3.5°、下沉约 0.56°、向右漂移约 0.42°，前推时略有回收。每次循环增益为
    // 1 ± GAIN_SPREAD。
    /** Handle lift: the cant starts (55 %). / 抬柄：开始倾斜（55%）。 */
    static final double LIFT_YAW = 0.10;
    static final double LIFT_PITCH = -0.06;
    static final double LIFT_ROLL = -1.925;
    /** Pull back: full cant, the dip and the drift to the right. / 后拉：完全倾斜、下沉并向右漂移。 */
    static final double PULL_YAW = 0.32;
    static final double PULL_PITCH = -0.50;
    static final double PULL_ROLL = -1.575;
    /** Push forward: the hand eases the cant and drift back a little. / 前推：手部略微收回倾斜与漂移。 */
    static final double PUSH_YAW = -0.18;
    static final double PUSH_PITCH = 0.10;
    static final double PUSH_ROLL = 0.70;
    /** Onset of every clack response (C1 start, no velocity jump). / 每次撞击响应的起始时间常数（C1 起步，速度无跳变）。 */
    static final double CLACK_ONSET_S = 0.005;
    /** Per-cycle gain spread of every component. / 每个分量每次循环的增益浮动。 */
    public static final double GAIN_SPREAD = 0.15;
    /** Per-cycle turn of each clack's yaw/pitch direction, degrees. / 每次撞击偏航/俯仰方向每次循环的旋转（度）。 */
    public static final double TURN_SPREAD_DEGREES = 15.0;

    /**
     * The four clacks: time, ring frequency (Hz), decay (s) and first-peak (yaw, pitch, roll). The two bolt clacks are
     * short, sharp jolts; the lift is a small tick; the lock rings slower and longer as the rifle settles.
     * 四次撞击：时间、振荡频率（赫兹）、衰减（秒）与第一峰值（偏航、俯仰、横滚）。两次枪机撞击是短促尖锐的抖动；抬柄是一次
     * 小抖动；闭锁振荡更慢更久，表现枪身回稳。
     */
    private static final Clack[] CLACKS = {
            Clack.of(HANDLE_LIFT_S, 8.0, 0.035, 0.03, 0.04, -0.20),
            Clack.of(REAR_CLACK_S, 7.0, 0.050, 0.16, 0.20, 0.30),
            Clack.of(FORWARD_CLACK_S, 6.5, 0.055, -0.22, 0.32, -0.40),
            Clack.of(LOCK_S, 4.0, 0.065, 0.03, -0.06, 0.35),
    };

    // ---- Timeline and fades, in client ticks. / 时间轴与淡入淡出，单位为客户端刻。
    /** The settle after the lock is over by here (sound seconds 1.15). / 闭锁后的回稳在此结束（声音第 1.15 秒）。 */
    public static final double NATURAL_END_TICKS = SOUND_START_TICKS + 1.15 * TICKS_PER_SECOND;
    /** Cycle ticks of the lock transient. / 闭锁瞬态所在的循环刻。 */
    public static final double LOCK_TICKS = cycleTicksOf(LOCK_S);
    /** Longest and shortest smooth fade into the end of the cycle. / 循环末尾平滑淡出的最长与最短时长。 */
    static final double MAX_END_FADE_TICKS = 4.0;
    static final double MIN_END_FADE_TICKS = 2.0;
    /** Scope-in fade (shows the rest of a running cycle without a pop). / 开镜淡入（显示进行中循环的剩余部分，无跳变）。 */
    public static final float SCOPE_IN_FADE_TICKS = 3.0F;
    /** Scope-out fade, quick but never a pop (it rides the zoom-out). / 退镜淡出，快速但不会跳变（与缩小视野同步）。 */
    public static final float SCOPE_OUT_FADE_TICKS = 3.0F;
    /** Fade when a running cycle is interrupted. / 进行中的循环被打断时的淡出。 */
    public static final float RELEASE_FADE_TICKS = 3.0F;
    /** A sent shot waits this long for its bolt cooldown (covers a 1 s round trip). / 已发射击等待拉栓冷却的时长。 */
    public static final int PENDING_TIMEOUT_TICKS = 20;

    private UsecBoltSway() {
    }

    /**
     * Trigger rule: a shot sent with this synced rifle state chambers a new round, which is exactly when the server
     * schedules the bolt sound ({@link UsecFireRules#cycle}). An empty chamber (a dry click) or the last round never
     * arms the sway.
     * 触发规则：以该同步步枪状态发出的射击会有新子弹上膛，这正是服务端安排拉栓声的条件（{@link UsecFireRules#cycle}）。
     * 空膛（空击）或最后一发永远不会预备晃动。
     */
    public static boolean chambersRound(@Nullable UsecRifleState stateAtSend) {
        UsecFireRules.Cycle cycle = UsecFireRules.cycle(stateAtSend);
        return cycle != null && cycle.chambered();
    }

    /**
     * Accessibility: the sway follows the lower of vanilla's Distortion Effects (Screen Effect Scale) and Damage Tilt,
     * so turning either motion slider down or off reduces or removes it; 0 for anything not a positive number.
     * 无障碍：晃动跟随原版“视觉扭曲效果”（屏幕效果缩放）与“受伤倾斜”中较低的一项，因此调低或关闭任一项都会减弱或移除它；
     * 非正数一律为 0。
     */
    public static float accessibilityScale(double distortionEffectScale, double damageTiltStrength) {
        double scale = Math.min(distortionEffectScale, damageTiltStrength);
        return scale > 0.0 ? (float) Math.min(1.0, scale) : 0.0F;
    }

    /** Seconds since the bolt sound started at {@code cycleTicks}. / 循环刻对应的自拉栓声开始起的秒数。 */
    public static double soundSeconds(double cycleTicks) {
        return (cycleTicks - SOUND_START_TICKS) / TICKS_PER_SECOND;
    }

    /** Cycle ticks at {@code soundSeconds} into the bolt sound. / 拉栓声第 {@code soundSeconds} 秒对应的循环刻。 */
    public static double cycleTicksOf(double soundSeconds) {
        return SOUND_START_TICKS + soundSeconds * TICKS_PER_SECOND;
    }

    /**
     * Cycle ticks at which the sway is over: the natural settle end, never after the bolt cooldown ends (Fast Reload's
     * 28 ticks end it 2.5 ticks after the lock, the plain 40 ticks never cut it).
     * 晃动结束的循环刻：自然回稳结束，但绝不晚于拉栓冷却结束（快速装填的 28 刻在闭锁后 2.5 刻结束，普通 40 刻从不截断）。
     */
    public static double endTicks(int boltTotalTicks) {
        return Math.max(0.0, Math.min(NATURAL_END_TICKS, boltTotalTicks));
    }

    /**
     * Smooth window that brings the cycle exactly to rest at {@code endTicks}: 1 until the fade, then a smoothstep to
     * 0 over 2-4 ticks after the lock, 0 from the end on.
     * 使循环在 {@code endTicks} 处恰好归零的平滑窗口：淡出前为 1，随后在闭锁后的 2-4 刻内以 smoothstep 降到 0，结束后为 0。
     */
    public static double endWindow(double cycleTicks, double endTicks) {
        if (!(cycleTicks < endTicks)) {
            return 0.0;
        }
        double fade = Math.max(MIN_END_FADE_TICKS, Math.min(MAX_END_FADE_TICKS, endTicks - LOCK_TICKS));
        double from = endTicks - fade;
        return cycleTicks <= from ? 1.0 : 1.0 - smoothstep((cycleTicks - from) / fade);
    }

    /**
     * The raw curve at {@code soundSeconds}: the slow hand motion plus the four clack jolts. Exactly zero before the
     * hand moves, continuous with a continuous rate everywhere, and back at rest after the lock settles.
     * 声音第 {@code soundSeconds} 秒的原始曲线：缓慢手部动作加上四次撞击抖动。手动作开始前恰好为零，处处连续且速度连续，
     * 闭锁回稳后回到静止。
     */
    public static Angles curve(double soundSeconds, Variation variation) {
        Angles hand = handMotion(soundSeconds, variation);
        Angles jolts = clackJolts(soundSeconds, variation);
        return hand.isZero() && jolts.isZero() ? Angles.ZERO : new Angles(hand.yaw() + jolts.yaw(),
                hand.pitch() + jolts.pitch(), hand.roll() + jolts.roll());
    }

    /**
     * The slow part: lift, pull and push ramps times the return, so it is exactly zero before the hand moves and from
     * 0.86 s on. / 慢动作部分：抬柄、后拉、前推过渡乘以回位过渡，因此手动作开始前与 0.86 秒之后恰好为零。
     */
    static Angles handMotion(double soundSeconds, Variation variation) {
        double s = soundSeconds;
        if (!(s > HANDLE_UP_FROM_S)) {
            return Angles.ZERO;
        }
        Variation v = Objects.requireNonNull(variation, "variation");
        double lift = smootherstep((s - HANDLE_UP_FROM_S) / HANDLE_UP_SPAN_S);
        double pull = smootherstep((s - PULL_FROM_S) / PULL_SPAN_S);
        double push = smootherstep((s - PUSH_FROM_S) / PUSH_SPAN_S);
        // A product, not a sum, so the slow part is exactly zero once the sight is back. / 用乘积而非求和，回位后恰好为零。
        double held = 1.0 - smootherstep((s - RETURN_FROM_S) / RETURN_SPAN_S);
        if (held <= 0.0) {
            return Angles.ZERO;
        }
        double drift = held * v.driftGain;
        double cant = held * v.poseGain;
        return new Angles((float) (drift * (lift * LIFT_YAW + pull * PULL_YAW + push * PUSH_YAW)),
                (float) (drift * (lift * LIFT_PITCH + pull * PULL_PITCH + push * PUSH_PITCH)),
                (float) (cant * (lift * LIFT_ROLL + pull * PULL_ROLL + push * PUSH_ROLL)));
    }

    /** The four clack jolts; each is exactly zero before its moment. / 四次撞击抖动；每次在其时刻之前恰好为零。 */
    static Angles clackJolts(double soundSeconds, Variation variation) {
        double s = soundSeconds;
        if (!(s > HANDLE_LIFT_S)) {
            return Angles.ZERO;
        }
        Variation v = Objects.requireNonNull(variation, "variation");
        double yaw = 0.0;
        double pitch = 0.0;
        double roll = 0.0;
        for (int i = 0; i < CLACKS.length; i++) {
            Clack clack = CLACKS[i];
            double k = clack.shape(s - clack.at) * v.gains[i];
            if (k != 0.0) {
                double cos = Math.cos(v.turns[i]);
                double sin = Math.sin(v.turns[i]);
                yaw += k * (clack.yaw * cos - clack.pitch * sin);
                pitch += k * (clack.yaw * sin + clack.pitch * cos);
                roll += k * clack.roll;
            }
        }
        return new Angles((float) yaw, (float) pitch, (float) roll);
    }

    /** The curve at {@code cycleTicks} with the end window applied. / 应用结束窗口后循环刻处的曲线。 */
    public static Angles cycle(double cycleTicks, double endTicks, Variation variation) {
        double window = endWindow(cycleTicks, endTicks);
        return window <= 0.0 ? Angles.ZERO : curve(soundSeconds(cycleTicks), variation).scaled(window);
    }

    static double smoothstep(double x) {
        double t = clamp01(x);
        return t * t * (3.0 - 2.0 * t);
    }

    static double smootherstep(double x) {
        double t = clamp01(x);
        return t * t * t * (t * (6.0 * t - 15.0) + 10.0);
    }

    private static double clamp01(double x) {
        return x > 0.0 ? Math.min(1.0, x) : 0.0;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /**
     * A view rotation in world degrees. / 以世界角度（度）表示的视角旋转。
     *
     * @param yaw   &gt; 0 turns the view right / &gt; 0 视角右转
     * @param pitch &gt; 0 tilts the view up / &gt; 0 视角上抬
     * @param roll  &gt; 0 rolls the camera clockwise / &gt; 0 镜头顺时针滚转
     */
    public record Angles(float yaw, float pitch, float roll) {
        public static final Angles ZERO = new Angles(0.0F, 0.0F, 0.0F);

        public boolean isZero() {
            return yaw == 0.0F && pitch == 0.0F && roll == 0.0F;
        }

        /** Scaled by a factor in [0, 1]; anything not positive is zero. / 按 [0, 1] 系数缩放；非正数为零。 */
        public Angles scaled(double factor) {
            if (!(factor > 0.0)) {
                return ZERO;
            }
            if (factor == 1.0) {
                return this;
            }
            return new Angles((float) (yaw * factor), (float) (pitch * factor), (float) (roll * factor));
        }
    }

    /**
     * Per-cycle variation so no two bolts look alike: each clack's gain and yaw/pitch direction, the pose and drift
     * gains. Timing never varies, so the sway stays on the sound.
     * 每次循环的变化，使两次拉栓不会一模一样：每次撞击的增益与偏航/俯仰方向，以及姿态与漂移增益。时间从不变化，因此始终与声音同步。
     */
    public static final class Variation {
        public static final Variation NONE = new Variation(new double[]{1.0, 1.0, 1.0, 1.0},
                new double[]{0.0, 0.0, 0.0, 0.0}, 1.0, 1.0);

        private final double[] gains;
        private final double[] turns;
        private final double poseGain;
        private final double driftGain;

        private Variation(double[] gains, double[] turns, double poseGain, double driftGain) {
            this.gains = gains;
            this.turns = turns;
            this.poseGain = poseGain;
            this.driftGain = driftGain;
        }

        public static Variation random(RandomGenerator random) {
            double[] gains = new double[CLACKS.length];
            double[] turns = new double[CLACKS.length];
            double turn = Math.toRadians(TURN_SPREAD_DEGREES);
            for (int i = 0; i < CLACKS.length; i++) {
                gains[i] = 1.0 + GAIN_SPREAD * signed(random);
                turns[i] = turn * signed(random);
            }
            return new Variation(gains, turns, 1.0 + GAIN_SPREAD * signed(random),
                    1.0 + GAIN_SPREAD * signed(random));
        }

        private static double signed(RandomGenerator random) {
            return Math.max(-1.0, Math.min(1.0, 2.0 * random.nextDouble() - 1.0));
        }
    }

    /**
     * One clack: a short damped ring, C1 at its onset, normalised so its first peak is 1.
     * 一次撞击：短暂的阻尼振荡，起点 C1 连续，归一化使第一个峰值为 1。
     */
    private record Clack(double at, double hz, double decay, double yaw, double pitch, double roll, double norm) {
        static Clack of(double at, double hz, double decay, double yaw, double pitch, double roll) {
            double peak = 0.0;
            for (int i = 1; i <= 50_000; i++) {
                peak = Math.max(peak, ring(i * 1.0e-5, hz, decay));
            }
            return new Clack(at, hz, decay, yaw, pitch, roll, peak);
        }

        double shape(double tau) {
            return tau > 0.0 ? ring(tau, hz, decay) / norm : 0.0;
        }

        private static double ring(double tau, double hz, double decay) {
            return (1.0 - Math.exp(-tau / CLACK_ONSET_S)) * Math.exp(-tau / decay) * Math.sin(2.0 * Math.PI * hz * tau);
        }
    }

    /**
     * The local shooter's sway state, ticked once per client tick and read per frame (several reads per frame, e.g.
     * the world and hand passes, give the same answer). Client thread only.
     * 本地射手的晃动状态：每个客户端刻推进一次、每帧读取（同一帧内多次读取，如世界与手部绘制，结果相同）。仅客户端线程。
     */
    public static final class Tracker {
        private static final long NONE = Long.MIN_VALUE;

        private final RandomGenerator random;
        private long ticks;
        private long pendingAt = NONE;
        private float scopeWeight;
        private float prevScopeWeight;
        private @Nullable Cycle cycle;
        /** A released cycle still fading out when a newer one started. / 新循环开始时仍在淡出的已释放循环。 */
        private @Nullable Cycle fading;

        public Tracker(RandomGenerator random) {
            this.random = Objects.requireNonNull(random, "random");
        }

        /**
         * A fire request was just sent (client thread, inside the tick). Only one that chambers a new round arms the
         * pending window; any running sway is released, since a new shot starts a new cycle.
         * 刚发出一个开火请求（客户端线程，在刻内）。只有会使新子弹上膛的请求才进入待确认窗口；任何进行中的晃动都会被释放，
         * 因为新的射击开始新的循环。
         */
        public void onShotSent(boolean chambersRound) {
            if (cycle != null) {
                cycle.release();
            }
            pendingAt = chambersRound ? ticks : NONE;
        }

        /**
         * End of every client tick.
         * 每个客户端刻末尾。
         *
         * @param scoped      the USEC scope profile is active / USEC 开镜配置处于激活
         * @param interrupted no living local player holding the rifle in the main hand with no screen open, or not a
         *                    SparkWitch server / 没有主手持步枪、未打开界面的存活本地玩家，或不是 SparkWitch 服务器
         * @param cooldown    the local player's synced rifle cooldown / 本地玩家已同步的步枪冷却
         */
        public void tick(boolean scoped, boolean interrupted, UsecCooldowns.Status cooldown) {
            ticks++;
            prevScopeWeight = scopeWeight;
            scopeWeight = approach(scopeWeight, scoped ? 1.0F : 0.0F,
                    scoped ? 1.0F / SCOPE_IN_FADE_TICKS : 1.0F / SCOPE_OUT_FADE_TICKS);
            UsecCooldowns.Status status = cooldown == null ? UsecCooldowns.Status.NONE : cooldown;
            if (pendingAt != NONE) {
                long sinceShot = ticks - pendingAt;
                if (interrupted || sinceShot > PENDING_TIMEOUT_TICKS) {
                    pendingAt = NONE;
                } else if (status.bolt()) {
                    // The entry cannot predate the shot (no request is sent while cooling down).
                    // 条目不可能早于射击（冷却期间不会发出请求）。
                    long elapsed = Math.min(Math.max(0, status.total() - status.remaining()), sinceShot);
                    if (cycle != null) {
                        // Let the old one finish its fade instead of vanishing. / 让旧循环完成淡出而不是突然消失。
                        cycle.release();
                        fading = cycle;
                    }
                    cycle = new Cycle(ticks - elapsed, endTicks(status.total()), Variation.random(random));
                    pendingAt = NONE;
                }
            }
            if (fading != null && fading.tickAndFinished(ticks)) {
                fading = null;
            }
            Cycle current = cycle;
            if (current == null) {
                return;
            }
            if (interrupted || !status.coolingDown()) {
                // Death, a screen, a dropped rifle, or a cooldown cleared early: fades now and never resumes.
                // 死亡、打开界面、步枪离手或冷却被提前清除：立即淡出且永不恢复。
                current.release();
            }
            if (current.tickAndFinished(ticks)) {
                cycle = null;
            }
        }

        /**
         * The sway this frame, before accessibility: the windowed curve times the scope fade and any release fade.
         * 本帧的晃动（未应用无障碍缩放）：加窗曲线乘以开镜淡入淡出与释放淡出。
         */
        public Angles angles(float tickDelta) {
            if (cycle == null && fading == null) {
                return Angles.ZERO;
            }
            float delta = (float) clamp01(tickDelta);
            double scope = smoothstep(prevScopeWeight + (scopeWeight - prevScopeWeight) * delta);
            if (!(scope > 0.0)) {
                return Angles.ZERO;
            }
            Angles now = sample(cycle, delta, scope);
            Angles old = sample(fading, delta, scope);
            return old.isZero() ? now : new Angles(now.yaw() + old.yaw(), now.pitch() + old.pitch(),
                    now.roll() + old.roll());
        }

        public void reset() {
            pendingAt = NONE;
            cycle = null;
            fading = null;
            scopeWeight = 0.0F;
            prevScopeWeight = 0.0F;
        }

        private Angles sample(@Nullable Cycle sampled, float delta, double scope) {
            if (sampled == null) {
                return Angles.ZERO;
            }
            double weight = scope * sampled.weight(delta);
            if (!(weight > 0.0)) {
                return Angles.ZERO;
            }
            return cycle((ticks - sampled.anchor) + delta, sampled.end, sampled.variation).scaled(weight);
        }

        private static final class Cycle {
            final long anchor;
            final double end;
            final Variation variation;
            private boolean releasing;
            private float release = 1.0F;
            private float prevRelease = 1.0F;

            Cycle(long anchor, double end, Variation variation) {
                this.anchor = anchor;
                this.end = end;
                this.variation = variation;
            }

            void release() {
                releasing = true;
            }

            /** Advances the release fade; true once the cycle shows nothing any more. / 推进释放淡出；不再显示任何内容时为 true。 */
            boolean tickAndFinished(long ticks) {
                prevRelease = release;
                if (releasing) {
                    release = Math.max(0.0F, release - 1.0F / RELEASE_FADE_TICKS);
                }
                return ticks - anchor >= end || (releasing && release <= 0.0F && prevRelease <= 0.0F);
            }

            double weight(float delta) {
                return smoothstep(prevRelease + (release - prevRelease) * delta);
            }
        }
    }
}
