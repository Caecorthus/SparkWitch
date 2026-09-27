package dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Pure, side-neutral Taotie swallow and return rules (plan §3.16). The server validation order is fixed here so the
 * service only supplies facts; later checks are consulted only after every earlier one passed. The client press claim
 * encodes the day-one probe: "whatever the crosshair is on gets swallowed" (vanilla's crosshair also hits players, so a
 * car under the crosshair is nearer than any player on that ray), a press is claimed only when it was really pressed
 * (so both Fear mixin orders agree), and repeated presses can swallow at most once because the cooldown is consumed
 * before the car is removed.
 * 纯粹且两端通用的饕餮吞车与归还规则（计划 §3.16）。服务端校验顺序固定在此处，服务层只提供事实；前序检查全部通过后
 * 才会查询后续检查。客户端按键认领编码了首日探针：“准星对着什么就吞什么”（原版准星也会命中玩家，所以准星上的
 * 小车比该射线上的任何玩家都近）；只有真实按下时才认领（因此两种 Fear mixin 顺序结果一致）；由于冷却在移除小车
 * 之前就已消耗，连续按键最多只会吞一次。
 */
public final class SeekerTaotieRules {
    /**
     * NoellesRoles' client swallow reach, measured from the eye to the crosshair hit point (its
     * {@code crosshairTargetDistance <= 3.0}). / NoellesRoles 客户端吞噬距离，从眼睛量到准星命中点。
     */
    public static final double CLIENT_REACH = 3.0;

    /** Why a swallow request was refused; every refusal is silent. / 吞车请求被拒绝的原因；所有拒绝都不提示。 */
    public enum Denial {
        NONE,
        NOT_RUNNING,
        NOT_TAOTIE,
        NOT_ALIVE,
        SWALLOWED,
        FEARED,
        STUNNED,
        SKILL_BLOCKED,
        COOLDOWN,
        NO_CAR,
        NOT_DEPLOYED,
        OUT_OF_REACH,
        NO_LINE_OF_SIGHT,
        FACTION_VETO;

        public boolean allowed() {
            return this == NONE;
        }
    }

    /**
     * Facts about one swallow request, supplied lazily by the server service (or a test fake).
     * 一次吞车请求的事实，由服务端服务（或测试替身）按需提供。
     */
    public interface SwallowChecks {
        boolean running();

        boolean exactTaotie();

        /** Playing, alive, survival-mode, not an active Wraith. / 参与中、存活、生存模式、非激活冤魂。 */
        boolean aliveParticipant();

        boolean swallowed();

        boolean feared();

        boolean stunned();

        boolean roleSkillBlocked();

        int swallowCooldownTicks();

        /** The id resolves to a live Search Car whose owner is online. / id 对应一辆存活且拥有者在线的搜寻小车。 */
        boolean liveCar();

        /** The owner's synced state says DEPLOYED with this exact car id. / 拥有者状态为 DEPLOYED 且车 id 一致。 */
        boolean carDeployed();

        double squaredDistanceToCar();

        int reachSquared();

        boolean canSeeCar();

        /** {@code SparkFactionApi.canAffectPlayer(taotie, owner, SWALLOW_ACTION_ID, game)}. */
        boolean factionAllows();
    }

    private SeekerTaotieRules() {
    }

    /**
     * Server validation in plan order: (1) running, exact Taotie, alive, not swallowed, not feared, not stunned, not
     * skill-blocked; (2) cooldown; (3) live DEPLOYED car; (4) reach and line of sight; (5) faction veto.
     * 按计划顺序的服务端校验：(1) 对局进行中、恰为饕餮、存活、未被吞、未被恐惧、未被眩晕、技能未被封锁；
     * (2) 冷却；(3) 存活且 DEPLOYED 的小车；(4) 距离与视线；(5) 阵营否决。
     */
    public static Denial swallowDenial(SwallowChecks checks) {
        if (!checks.running()) {
            return Denial.NOT_RUNNING;
        }
        if (!checks.exactTaotie()) {
            return Denial.NOT_TAOTIE;
        }
        if (!checks.aliveParticipant()) {
            return Denial.NOT_ALIVE;
        }
        if (checks.swallowed()) {
            return Denial.SWALLOWED;
        }
        if (checks.feared()) {
            return Denial.FEARED;
        }
        if (checks.stunned()) {
            return Denial.STUNNED;
        }
        if (checks.roleSkillBlocked()) {
            return Denial.SKILL_BLOCKED;
        }
        if (!isCooldownReady(checks.swallowCooldownTicks())) {
            return Denial.COOLDOWN;
        }
        if (!checks.liveCar()) {
            return Denial.NO_CAR;
        }
        if (!checks.carDeployed()) {
            return Denial.NOT_DEPLOYED;
        }
        if (!withinReach(checks.squaredDistanceToCar(), checks.reachSquared())) {
            return Denial.OUT_OF_REACH;
        }
        if (!checks.canSeeCar()) {
            return Denial.NO_LINE_OF_SIGHT;
        }
        if (!checks.factionAllows()) {
            return Denial.FACTION_VETO;
        }
        return Denial.NONE;
    }

    public static boolean isCooldownReady(int remainingTicks) {
        return remainingTicks <= 0;
    }

    /** Inclusive, NaN-safe reach check. / 含边界且对 NaN 安全的距离判定。 */
    public static boolean withinReach(double squaredDistance, int reachSquared) {
        return squaredDistance >= 0.0 && reachSquared >= 0 && squaredDistance <= reachSquared;
    }

    /**
     * Cost of a car swallow: NoellesRoles' dynamic cooldown, or its fixed fallback while the dynamic value is unset.
     * 吞车的代价：NoellesRoles 的动态冷却；动态值未设置时使用其固定回退值。
     */
    public static int swallowCostTicks(int calculatedTicks, int fallbackTicks) {
        return calculatedTicks > 0 ? calculatedTicks : Math.max(0, fallbackTicks);
    }

    /**
     * Whether a Search Car under the vanilla crosshair shields the press: within NoellesRoles' own eye-to-hit reach
     * (3.0), so anything NoellesRoles could target behind it is farther away. A shielded press is always claimed, even
     * when the server's feet-to-feet reach would refuse the swallow (car on a step or table), so a player behind the
     * car is never swallowed through it. / 原版准星上的搜寻小车是否遮挡本次按键：处于 NoellesRoles 自己的眼到命中点
     * 距离（3.0）内，其后的任何目标都更远。被遮挡的按键总会被认领，即使服务端脚到脚距离会拒绝吞车（小车在台阶或
     * 桌上），车后的玩家因此不会被隔车吞掉。
     */
    public static boolean shieldsPress(double eyeToHitDistance) {
        return eyeToHitDistance >= 0.0 && eyeToHitDistance <= CLIENT_REACH;
    }

    /**
     * Client reach for sending {@code seeker_car_swallow} and for the hint: the crosshair shield distance and the
     * server's feet-to-feet check, so the client never sends a request the server must refuse on reach.
     * 发送 {@code seeker_car_swallow} 与显示提示的客户端距离：同时满足准星遮挡距离与服务端脚到脚判定，
     * 客户端因此不会发送必然因距离被拒绝的请求。
     */
    public static boolean withinClientReach(double eyeToHitDistance, double squaredDistance, int reachSquared) {
        return shieldsPress(eyeToHitDistance) && withinReach(squaredDistance, reachSquared);
    }

    /**
     * Client claim of one shared-key press. Cheap gates first: only a real press of the shared ability key on a
     * confirmed SparkWitch server that accepts {@code seeker_car_swallow}; then the local Taotie must be ready and the
     * vanilla crosshair must be on a Search Car that {@linkplain #shieldsPress shields} the press. Claimed means
     * NoellesRoles sees no press this call.
     * 客户端对一次共享键按下的认领。先做廉价判定：只有在已确认、能接收 {@code seeker_car_swallow} 的 SparkWitch
     * 服务器上真实按下共享技能键；之后本地饕餮必须就绪，且原版准星落在遮挡本次按键的搜寻小车上。认领后
     * NoellesRoles 在这次调用中看不到按键。
     */
    public static boolean claimsPress(boolean pressed, boolean sharedAbilityKey, boolean connectionReady,
                                      BooleanSupplier taotieReady, BooleanSupplier carInReach) {
        return pressed
                && sharedAbilityKey
                && connectionReady
                && taotieReady.getAsBoolean()
                && carInReach.getAsBoolean();
    }

    /**
     * Local Taotie readiness, mirroring the server's actor checks the client can see.
     * 本地饕餮是否就绪，对应客户端可见的服务端施放者检查。
     */
    public static boolean taotieReady(boolean exactTaotie, boolean aliveParticipant, boolean swallowed,
                                      boolean feared, boolean stunned, int swallowCooldownTicks) {
        return exactTaotie && aliveParticipant && !swallowed && !feared && !stunned
                && isCooldownReady(swallowCooldownTicks);
    }

    /**
     * Final death for the return poll: marked dead and not a pending SparkTraits Last Stand.
     * 归还轮询用的最终死亡：已标记死亡且不处于 SparkTraits 最后一搏待定。
     */
    public static boolean isFinalDeath(boolean markedDead, boolean lastStandPending) {
        return markedDead && !lastStandPending;
    }

    /**
     * Owner poll (every 20 ticks while SWALLOWED or PendingReturn): a pending return is always retried; a swallowed car
     * returns when its exact Taotie finally died or no longer holds the Taotie role (e.g. recruitment). An unknown
     * {@code lostTo} returns at once so a car can never be stranded. A Taotie swallowed by another Taotie is alive.
     * 拥有者轮询（SWALLOWED 或 PendingReturn 时每 20 刻）：待归还总是重试；被吞的小车在其确切的饕餮最终死亡或不再
     * 是饕餮（如被招募）时归还。未知的 {@code lostTo} 立即归还，小车永不丢失。被另一只饕餮吞下的饕餮仍算存活。
     */
    public static boolean shouldAttemptReturn(SeekerCarState carState, boolean pendingReturn, @Nullable UUID lostTo,
                                              BooleanSupplier lostToFinallyDead, BooleanSupplier lostToStillTaotie) {
        if (pendingReturn) {
            return true;
        }
        if (carState != SeekerCarState.SWALLOWED) {
            return false;
        }
        if (lostTo == null) {
            return true;
        }
        return lostToFinallyDead.getAsBoolean() || !lostToStillTaotie.getAsBoolean();
    }

    /**
     * {@code KillPlayer.AFTER}: the victim is exactly the Taotie that holds this car and the death was not intercepted
     * by Last Stand (an intercepted death is picked up by the poll at final death).
     * {@code KillPlayer.AFTER}：受害者恰为吞下这辆车的饕餮，且死亡未被最后一搏拦截（被拦截的死亡由轮询在最终死亡时处理）。
     */
    public static boolean killReturnsCar(SeekerCarState carState, @Nullable UUID lostTo, @Nullable UUID victim,
                                         boolean deathIntercepted) {
        return !deathIntercepted
                && carState == SeekerCarState.SWALLOWED
                && lostTo != null
                && lostTo.equals(victim);
    }
}
