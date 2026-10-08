package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

/**
 * Pure rule of the USEC scoped instinct cloak (owner, 2026-10-08): while a USEC is scoped (the server-checked, synced
 * {@code UsecPlayerComponent.scoped} flag), a viewer at most {@link UsecRules#INSTINCT_CLOAK_RANGE} blocks away (feet
 * to feet, inclusive, the Apprentice Murder Sense and Vendetta measure) cannot see that USEC through keyed instinct.
 * Never cloaked: the viewer's own player, any target on a server that is not a confirmed SparkWitch server, and every
 * exempt viewer ({@link #exemptViewer}), who keeps seeing a scoped USEC at any distance.
 * 开镜 USEC 本能隐蔽（所有者 2026-10-08）的纯规则：USEC 开镜期间（服务端检查并同步的 {@code UsecPlayerComponent.scoped}
 * 标记），距离不超过 {@link UsecRules#INSTINCT_CLOAK_RANGE} 格（脚到脚、含边界，与预备魔女杀意感知和仇杀客的量法一致）
 * 的观察者无法通过按键本能看到该 USEC。永不隐蔽：观察者自己、未确认 SparkWitch 服务端时的任何目标，以及所有豁免观察者
 * （{@link #exemptViewer}），他们在任何距离都照常看到开镜的 USEC。
 */
public final class UsecInstinctCloakRules {
    public static final double RANGE_SQUARED = UsecRules.INSTINCT_CLOAK_RANGE * UsecRules.INSTINCT_CLOAK_RANGE;

    private UsecInstinctCloakRules() {
    }

    /**
     * Viewers the cloak never applies to (owner, 2026-10-08): the same viewers SparkStrength's Corrupt Cop instinct
     * concealment exempts ({@code CorruptCopConcealmentRules.shouldConceal}: not Wathe playing-and-alive, or spectating
     * or creative), which covers dead spectators, non-participants, creative players, active Wraiths (Wathe-dead) and
     * Rift Gate occupants (alive spectators). Active Wraiths and Rift occupants are also named through SparkWitch's own
     * synced state ({@code WraithClientState.isActive}, {@code RiftSessionService.isInside}), so they stay exempt even
     * if their liveness or game mode were ever out of step.
     * 隐蔽永不适用的观察者（所有者 2026-10-08）：与 SparkStrength 黑警本能屏蔽豁免的观察者相同
     * （{@code CorruptCopConcealmentRules.shouldConceal}：在 Wathe 中不处于对局存活，或处于旁观/创造模式），即死亡旁观者、
     * 非参与者、创造模式玩家、活跃冤魂（Wathe 中已死亡）与隙行门内的玩家（存活旁观者）。活跃冤魂与隙行门内玩家还通过
     * SparkWitch 自己的同步状态（{@code WraithClientState.isActive}、{@code RiftSessionService.isInside}）直接认定，
     * 因此即使其存活状态或游戏模式不同步也仍然豁免。
     *
     * @param playingAndAlive       {@code GameFunctions.isPlayerPlayingAndAlive(viewer)}
     * @param spectatingOrCreative  {@code GameFunctions.isPlayerSpectatingOrCreative(viewer)}
     * @param activeWraith          {@code WraithClientState.isActive(viewer)}
     * @param riftOccupant          {@code RiftSessionService.isInside(viewer)}
     */
    public static boolean exemptViewer(boolean playingAndAlive, boolean spectatingOrCreative, boolean activeWraith,
                                       boolean riftOccupant) {
        return !playingAndAlive || spectatingOrCreative || activeWraith || riftOccupant;
    }

    /**
     * Whether keyed instinct must not show this target to this viewer. A NaN distance never cloaks.
     * 该目标是否不得通过按键本能显示给该观察者。距离为 NaN 时从不隐蔽。
     *
     * @param confirmedServer   the server is a confirmed SparkWitch server / 服务端已确认为 SparkWitch
     * @param exemptViewer      {@link #exemptViewer} for the viewer / 观察者的豁免判定
     * @param self              the target is the viewer's own player / 目标是观察者自己
     * @param scoped            the target's synced scoped flag / 目标同步的开镜标记
     * @param distanceSquared   squared feet-to-feet distance viewer to target / 观察者到目标脚到脚距离的平方
     */
    public static boolean cloaks(boolean confirmedServer, boolean exemptViewer, boolean self, boolean scoped,
                                 double distanceSquared) {
        return scoped && confirmedServer && !self && !exemptViewer && distanceSquared <= RANGE_SQUARED;
    }
}
