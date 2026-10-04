package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarPhysics;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * Pure server validation of owner-simulated car moves (plan §3.11.3). The validator protects the authoritative car
 * position that other players see and can break; it does not police the owner's view (a modified client can free-look
 * anyway). Checks, in order, and what a failure does:
 * <ol>
 *   <li>rate: at most {@link SeekerRules#MAX_MOVES_PER_TICK} per server tick, extras dropped silently;</li>
 *   <li>session: owner, sessionId, mode CAR and car id match, else dropped silently; {@code seq > lastSeq}, else
 *       dropped and counted toward CHEAT_SUSPECT;</li>
 *   <li>numbers: finite and {@code |Δ| < 8}, else corrected and counted;</li>
 *   <li>horizontal speed: {@code Δh <= budget + 0.05}, the budget refilling {@code CAR_SPEED × 1.2} per tick up to
 *       five ticks, else corrected and counted;</li>
 *   <li>vertical (server-side fall model, no client velocity trusted): a supported car may rise more than 0.05 only as
 *       far as a replayed honest step ({@code SeekerCarPhysics.move} with {@code -CAR_GRAVITY}) reaches; an unsupported
 *       car never rises and must end at or below the replayed free fall of the ticks it is charged: the horizontal
 *       distance implies at least {@link #chargedTicks} physics ticks, and the fall speed follows the car's own gravity
 *       and drag from rest over the {@code airTicks} already charged to this unsupported stretch (collisions honoured,
 *       so landing is never refused). The charge resets only when a move starts supported, never on a correction, so
 *       rejections cannot be farmed to restart it. Else corrected (not counted: latency can cause it);</li>
 *   <li>replay: {@code SeekerCarPhysics} from the server position must land within 0.0625 (squared, the vanilla
 *       {@code onVehicleMove} threshold) of the claim and the contracted box must be empty, else corrected (not
 *       counted: a door may close on the server first);</li>
 *   <li>area: feet below the play-area floor break the car at once (VOID); otherwise the horizontal position is
 *       clamped into the play area (accept, clamp, correct). There is no distance limit from the body
 *       (owner decision 2026-10-04).</li>
 * </ol>
 * More than {@link SeekerRules#CHEAT_REJECT_LIMIT} counted rejections within {@link SeekerRules#CHEAT_REJECT_WINDOW_TICKS}
 * end the session with CHEAT_SUSPECT; nobody is ever kicked.
 * 对拥有者模拟的小车移动进行服务端纯校验（计划 §3.11.3）。校验器保护的是其他玩家看到并能打坏的权威小车位置，
 * 不负责拥有者的视野（改过的客户端本来就能自由视角）。依次检查（失败时的处理）：速率（每服务端刻最多 3 个，多余静默丢弃）；
 * 会话（拥有者、会话 id、CAR 模式与小车 id 须匹配，否则静默丢弃；seq 须递增，否则丢弃并计入作弊计数）；
 * 数值（有限且 |Δ| < 8，否则纠正并计数）；水平速度（Δh 不超过配额 + 0.05，配额每刻补充 CAR_SPEED × 1.2，最多 5 刻，否则纠正并计数）；
 * 垂直（服务端下落模型，不信任客户端速度：有支撑时只有重放出的合法台阶（以 {@code -CAR_GRAVITY} 调用 SeekerCarPhysics.move）
 * 才允许上升超过 0.05；无支撑时不得上升，且终点须不高于按计费刻数重放的自由下落：水平位移至少对应 {@link #chargedTicks} 个物理刻，
 * 下落速度从静止开始，按小车自身重力与阻力累计本段无支撑已计费的 {@code airTicks}（考虑碰撞，因此落地永不被拒）；
 * 计费只在移动从有支撑处开始时清零、纠正从不清零，因此无法靠刷拒绝重启模型；否则纠正但不计数，延迟可能导致）；
 * 重放（从服务端位置用 SeekerCarPhysics 重放，落点与声明的平方偏差不超过 0.0625（原版 onVehicleMove 阈值）且收缩后的箱体为空，
 * 否则纠正但不计数，门可能先在服务端关上）；区域（脚底低于游戏区域底面立即以 VOID 损坏；否则把水平位置钳制进游戏区域：
 * 先接受、再钳制、再纠正；与本体之间没有距离限制，所有者决定 2026-10-04）。5 秒内计数超过 20 次即以 CHEAT_SUSPECT 结束会话；
 * 从不踢人。
 */
public final class SeekerCarMoveRules {
    public static final double MAX_DELTA = 8.0;
    public static final double SPEED_TOLERANCE = 1.2;
    public static final double SPEED_SLACK = 0.05;
    public static final int BURST_TICKS = 5;
    public static final double BUDGET_PER_TICK = SeekerRules.CAR_SPEED * SPEED_TOLERANCE;
    public static final double BUDGET_CAPACITY = BUDGET_PER_TICK * BURST_TICKS;
    public static final double STEP_SLACK = 0.05;
    /**
     * Float allowance on the replayed free fall. Owner and server run the same double math, and the owner keeps its fall
     * speed through a correction, so an honest move never falls slower than the model.
     * 重放自由下落的浮点余量。拥有者与服务端执行相同的双精度运算，且拥有者在纠正时保留下落速度，因此诚实移动永远不会比模型下落得慢。
     */
    public static final double FALL_TOLERANCE = 1.0E-3;
    /** Cap on charged air ticks; the fall speed is practically terminal long before. / 空中计费刻上限，此前早已接近终端速度。 */
    public static final int MAX_AIR_TICKS = 200;
    /** Float noise allowed on "never rises" in the air. / 空中“不得上升”允许的浮点误差。 */
    public static final double RISE_EPSILON = 1.0E-6;
    /** Squared deviation, as vanilla {@code onVehicleMove}. / 平方偏差，同原版 {@code onVehicleMove}。 */
    public static final double REPLAY_DEVIATION_SQUARED = 0.0625;
    public static final double SPACE_CONTRACTION = 0.0625;

    private SeekerCarMoveRules() {
    }

    /** What the service does with one move. / 服务对单个移动包的处理。 */
    public enum Outcome {
        /** Ignore silently. / 静默忽略。 */
        DROP,
        /** Keep the server position and send it back. / 保持服务端位置并下发纠正。 */
        CORRECT,
        /** Apply {@link Decision#position()}; send a correction when {@link Decision#clamped()}. / 应用位置，被钳制时下发纠正。 */
        ACCEPT,
        /** Apply the position, then break the car with source VOID. / 应用位置后以 VOID 损坏小车。 */
        VOID
    }

    /** Which check decided (for tests and logs). / 由哪项检查决定（供测试与日志）。 */
    public enum Check {
        RATE, SESSION, SEQUENCE, NUMBERS, SPEED, VERTICAL, REPLAY, AREA, VOID, OK
    }

    /**
     * @param position        the position to keep (server position on CORRECT/DROP) / 应保留的位置
     * @param clamped         ACCEPT moved the claim onto the area limit / 接受时是否被钳制
     * @param countsAsCheat   counts toward CHEAT_SUSPECT / 是否计入作弊计数
     */
    public record Decision(Outcome outcome, Check check, Vec3d position, boolean clamped, boolean countsAsCheat) {
        static Decision drop(Check check, Vec3d server, boolean counts) {
            return new Decision(Outcome.DROP, check, server, false, counts);
        }

        static Decision correct(Check check, Vec3d server, boolean counts) {
            return new Decision(Outcome.CORRECT, check, server, false, counts);
        }
    }

    /**
     * World seam for the replay: the service binds it to {@code SeekerCarPhysics} with the world collider.
     * 重放的世界接缝：服务端把它绑定到带世界碰撞的 {@code SeekerCarPhysics}。
     */
    public interface Replay {
        /** Replayed end point of moving the traversal box by {@code delta}. / 通行箱移动 delta 后的重放落点。 */
        Vec3d replay(Vec3d from, Vec3d delta);

        /** Server-computed support under {@code position}. / 服务端自行计算的支撑。 */
        boolean isSupported(Vec3d position);

        /** The traversal box at {@code position}, contracted by 0.0625, collides with nothing. / 收缩后的通行箱无碰撞。 */
        boolean isSpaceEmpty(Vec3d position);
    }

    /**
     * Everything the physical checks need.
     * 物理检查所需的全部输入。
     *
     * @param server          authoritative car position before the move / 移动前的权威小车位置
     * @param claimed         claimed end position / 声明的终点
     * @param yaw             claimed yaw / 声明的朝向
     * @param budget          horizontal budget available now / 当前可用的水平配额
     * @param airTicks        physics ticks already charged to the current unsupported stretch (reset only when a
     *                        move starts supported) / 本段无支撑已计费的物理刻数（仅在移动从有支撑处开始时清零）
     * @param playArea        Wathe play area, or null when unknown; the only area bound / Wathe 游戏区域，唯一的区域约束
     */
    public record Move(Vec3d server, Vec3d claimed, float yaw, double budget, int airTicks, @Nullable Box playArea) {
    }

    // ---- Admission (no physics) ----

    public static boolean isOverRate(int packetsThisTick) {
        return packetsThisTick > SeekerRules.MAX_MOVES_PER_TICK;
    }

    public static boolean sessionMatches(int packetSessionId, int activeSessionId, SeekerSessionMode mode,
                                         boolean carMatches) {
        return mode == SeekerSessionMode.CAR && packetSessionId == activeSessionId && carMatches;
    }

    public static boolean isFreshSequence(int seq, int lastSeq) {
        return seq > lastSeq;
    }

    /**
     * Rate, session and sequence, in that order; null means "go on to the physical checks".
     * 依次检查速率、会话与序号；返回 null 表示继续进行物理检查。
     */
    @Nullable
    public static Decision admit(int packetsThisTick, int packetSessionId, int activeSessionId,
                                 SeekerSessionMode mode, boolean carMatches, int seq, int lastSeq, Vec3d server) {
        if (isOverRate(packetsThisTick)) {
            return Decision.drop(Check.RATE, server, false);
        }
        if (!sessionMatches(packetSessionId, activeSessionId, mode, carMatches)) {
            return Decision.drop(Check.SESSION, server, false);
        }
        if (!isFreshSequence(seq, lastSeq)) {
            return Decision.drop(Check.SEQUENCE, server, true);
        }
        return null;
    }

    // ---- Physical checks ----

    public static boolean hasValidNumbers(Vec3d server, Vec3d claimed, float yaw) {
        if (!finite(server) || !finite(claimed) || !Float.isFinite(yaw)) {
            return false;
        }
        return claimed.subtract(server).lengthSquared() < MAX_DELTA * MAX_DELTA;
    }

    public static boolean isWithinSpeedBudget(double horizontalDelta, double budget) {
        return horizontalDelta <= Math.max(0.0, budget) + SPEED_SLACK;
    }

    /**
     * Physics ticks a move stands for: an honest owner sends one step of at most {@code CAR_SPEED} per tick, so a longer
     * horizontal hop (a burst saved up from the budget) is charged the ticks it would take. Always at least one.
     * 一次移动代表的物理刻数：诚实的拥有者每刻发送一步、水平不超过 {@code CAR_SPEED}，因此更长的水平位移（攒下的配额）
     * 按其所需刻数计费。至少为 1。
     */
    public static int chargedTicks(double horizontalDelta) {
        if (!(horizontalDelta > SPEED_SLACK)) {
            return 1;
        }
        double ticks = Math.ceil((horizontalDelta - SPEED_SLACK) / SeekerRules.CAR_SPEED - 1.0E-9);
        return (int) Math.max(1.0, Math.min(ticks, MAX_DELTA / SeekerRules.CAR_SPEED + 1.0));
    }

    /**
     * Car fall speed (negative) after {@code ticks} unsupported physics ticks from rest: the same gravity, drag and
     * terminal speed as {@code SeekerCarPhysics.inputVelocity}. / 从静止开始无支撑 {@code ticks} 个物理刻后的下落速度（负值）。
     */
    public static double fallSpeedAfter(int ticks) {
        double velocity = 0.0;
        for (int i = 0, n = Math.min(Math.max(0, ticks), MAX_AIR_TICKS); i < n; i++) {
            velocity = nextFallSpeed(velocity);
        }
        return velocity;
    }

    static double nextFallSpeed(double velocity) {
        return Math.max(-SeekerCarPhysics.TERMINAL_FALL_SPEED,
                (Math.min(velocity, 0.0) - SeekerRules.CAR_GRAVITY) * SeekerRules.CAR_DRAG);
    }

    /**
     * Lowest y an honest car could be required to reach: the replayed free fall of {@code ticks} physics ticks after
     * {@code airTicks} already charged, the horizontal delta spread evenly over them. Collisions are honoured, so a car
     * that lands early stops where the model stops.
     * 诚实小车必须达到的最低 y：在已计费 {@code airTicks} 之后重放 {@code ticks} 个物理刻的自由下落，水平位移均分到各刻。
     * 考虑碰撞，因此提前落地的小车与模型停在同一处。
     */
    public static double modelFallY(Vec3d server, Vec3d delta, int airTicks, int ticks, Replay replay) {
        Vec3d position = server;
        double velocity = fallSpeedAfter(airTicks);
        double stepX = delta.x / ticks;
        double stepZ = delta.z / ticks;
        for (int i = 0; i < ticks; i++) {
            velocity = nextFallSpeed(velocity);
            Vec3d next = replay.replay(position, new Vec3d(stepX, velocity, stepZ));
            if (next.y > position.y + velocity + RISE_EPSILON) {
                // Landed: the rest of the move rolls on the ground. / 已落地：剩余部分在地面上行驶。
                velocity = 0.0;
            }
            position = next;
        }
        return position.y;
    }

    /**
     * Vertical check against the server's own models (never the client's velocity). Supported: at most
     * {@link #STEP_SLACK}, or as high as the replayed honest step with the claimed horizontal delta reaches.
     * Unsupported: never rises, and ends no higher than {@link #modelFallY} plus {@link #FALL_TOLERANCE}.
     * 以服务端自有模型（从不使用客户端速度）做垂直检查。有支撑：最多 {@link #STEP_SLACK}，或不超过以声明的水平位移重放出的
     * 合法台阶高度。无支撑：不得上升，且终点不高于 {@link #modelFallY} 加 {@link #FALL_TOLERANCE}。
     */
    public static boolean isVerticalAllowed(Vec3d server, Vec3d delta, boolean supported, int airTicks,
                                            Replay replay) {
        if (supported) {
            if (delta.y <= STEP_SLACK) {
                return true;
            }
            if (delta.y > SeekerRules.CAR_STEP_HEIGHT + STEP_SLACK || delta.horizontalLengthSquared() <= 0.0) {
                return false;
            }
            Vec3d step = replay.replay(server, new Vec3d(delta.x, -SeekerRules.CAR_GRAVITY, delta.z));
            return server.y + delta.y <= step.y + STEP_SLACK;
        }
        if (delta.y > RISE_EPSILON) {
            return false;
        }
        int ticks = chargedTicks(delta.horizontalLength());
        return server.y + delta.y <= modelFallY(server, delta, airTicks, ticks, replay) + FALL_TOLERANCE;
    }

    /**
     * Charged air ticks after an accepted move: zero when it started supported, else increased by the move's
     * {@link #chargedTicks} (capped). / 已接受移动后的空中计费刻：从有支撑处开始时为 0，否则加上本次计费刻数（有上限）。
     */
    public static int nextAirTicks(boolean startedSupported, int airTicks, double horizontalDelta) {
        if (startedSupported) {
            return 0;
        }
        return Math.min(MAX_AIR_TICKS, Math.max(0, airTicks) + chargedTicks(horizontalDelta));
    }

    public static boolean replayMatches(Vec3d replayed, Vec3d claimed) {
        return replayed.squaredDistanceTo(claimed) <= REPLAY_DEVIATION_SQUARED;
    }

    /**
     * Horizontal clamp into the play area (null: no clamp); the y coordinate is kept, and a position already inside is
     * returned as the same instance. No radius around the body (2026-10-04).
     * 把水平位置钳制进游戏区域（为 null 时不钳制）；y 保持不变，已在区域内的位置原样返回同一实例。
     * 不再有以本体为中心的半径（2026-10-04）。
     */
    public static Vec3d clampToArea(Vec3d position, @Nullable Box playArea) {
        double x = position.x;
        double z = position.z;
        if (playArea != null) {
            x = MathHelper.clamp(x, playArea.minX, playArea.maxX);
            z = MathHelper.clamp(z, playArea.minZ, playArea.maxZ);
        }
        return x == position.x && z == position.z ? position : new Vec3d(x, position.y, z);
    }

    /**
     * Physical checks of one admitted move, in the order documented on the class.
     * 对一个已准入移动包按类注释顺序执行的物理检查。
     */
    public static Decision validate(Move move, Replay replay) {
        Vec3d server = move.server();
        Vec3d claimed = move.claimed();
        if (!hasValidNumbers(server, claimed, move.yaw())) {
            return Decision.correct(Check.NUMBERS, server, true);
        }
        Vec3d delta = claimed.subtract(server);
        if (!isWithinSpeedBudget(delta.horizontalLength(), move.budget())) {
            return Decision.correct(Check.SPEED, server, true);
        }
        if (!isVerticalAllowed(server, delta, replay.isSupported(server), move.airTicks(), replay)) {
            return Decision.correct(Check.VERTICAL, server, false);
        }
        if (!replayMatches(replay.replay(server, delta), claimed) || !replay.isSpaceEmpty(claimed)) {
            return Decision.correct(Check.REPLAY, server, false);
        }
        if (SeekerRemoteRules.isBelowPlayArea(move.playArea(), claimed.y)) {
            return new Decision(Outcome.VOID, Check.VOID, claimed, false, false);
        }
        Vec3d clamped = clampToArea(claimed, move.playArea());
        if (clamped != claimed) {
            if (!replay.isSpaceEmpty(clamped)) {
                return Decision.correct(Check.AREA, server, false);
            }
            return new Decision(Outcome.ACCEPT, Check.AREA, clamped, true, false);
        }
        return new Decision(Outcome.ACCEPT, Check.OK, claimed, false, false);
    }

    public static boolean isCheatSuspect(int countedRejectsInWindow) {
        return countedRejectsInWindow > SeekerRules.CHEAT_REJECT_LIMIT;
    }

    private static boolean finite(Vec3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    /**
     * Horizontal speed budget: refills {@link #BUDGET_PER_TICK} per server tick up to {@link #BURST_TICKS} ticks; a new
     * session starts with one tick's worth. For one move per tick this is exactly
     * {@code CAR_SPEED × min(ticksSince, 5) × 1.2}, and bursts of several packets share the same budget.
     * 水平速度配额：每服务端刻补充 {@link #BUDGET_PER_TICK}，最多累积 5 刻；新会话起始为一刻的量。每刻一个包时恰好等于
     * {@code CAR_SPEED × min(ticksSince, 5) × 1.2}；同一时刻的多个包共享同一配额。
     */
    public static final class Budget {
        private double available;
        private long tick;

        public Budget(long now) {
            this.available = BUDGET_PER_TICK;
            this.tick = now;
        }

        public double availableAt(long now) {
            long elapsed = Math.max(0L, now - tick);
            return Math.min(BUDGET_CAPACITY, available + elapsed * BUDGET_PER_TICK);
        }

        public void consume(long now, double used) {
            available = Math.max(0.0, availableAt(now) - Math.max(0.0, used));
            tick = Math.max(tick, now);
        }
    }

    /**
     * Sliding window of counted rejections ({@link SeekerRules#CHEAT_REJECT_WINDOW_TICKS}).
     * 计入作弊计数的拒绝的滑动窗口。
     */
    public static final class RejectWindow {
        private final ArrayDeque<Long> ticks = new ArrayDeque<>();

        /** Records one rejection and returns the count inside the window. / 记录一次拒绝并返回窗口内的次数。 */
        public int record(long now) {
            ticks.addLast(now);
            return count(now);
        }

        public int count(long now) {
            while (!ticks.isEmpty() && ticks.peekFirst() <= now - SeekerRules.CHEAT_REJECT_WINDOW_TICKS) {
                ticks.pollFirst();
            }
            return ticks.size();
        }
    }
}
