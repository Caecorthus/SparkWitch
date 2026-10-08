package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

/**
 * Pure rule of the USEC scoped instinct cloak (owner, 2026-10-08): while a USEC is scoped (the server-checked, synced
 * {@code UsecPlayerComponent.scoped} flag), a viewer at most {@link UsecRules#INSTINCT_CLOAK_RANGE} blocks away (feet
 * to feet, inclusive, the Apprentice Murder Sense and Vendetta measure) cannot see that USEC through keyed instinct.
 * Never cloaked: the viewer's own player, any target on a server that is not a confirmed SparkWitch server, and every
 * viewer that sees Wathe's spectator information (dead spectators and non-participants keep seeing everything).
 * 开镜 USEC 本能隐蔽（所有者 2026-10-08）的纯规则：USEC 开镜期间（服务端检查并同步的 {@code UsecPlayerComponent.scoped}
 * 标记），距离不超过 {@link UsecRules#INSTINCT_CLOAK_RANGE} 格（脚到脚、含边界，与预备魔女杀意感知和仇杀客的量法一致）
 * 的观察者无法通过按键本能看到该 USEC。永不隐蔽：观察者自己、未确认 SparkWitch 服务端时的任何目标，以及能看到 Wathe
 * 旁观者信息的观察者（死亡旁观者与非参与者照常看到一切）。
 */
public final class UsecInstinctCloakRules {
    public static final double RANGE_SQUARED = UsecRules.INSTINCT_CLOAK_RANGE * UsecRules.INSTINCT_CLOAK_RANGE;

    private UsecInstinctCloakRules() {
    }

    /**
     * Whether keyed instinct must not show this target to this viewer. A NaN distance never cloaks.
     * 该目标是否不得通过按键本能显示给该观察者。距离为 NaN 时从不隐蔽。
     *
     * @param confirmedServer   the server is a confirmed SparkWitch server / 服务端已确认为 SparkWitch
     * @param spectatorViewer   the viewer sees Wathe's spectator information / 观察者能看到 Wathe 旁观者信息
     * @param self              the target is the viewer's own player / 目标是观察者自己
     * @param scoped            the target's synced scoped flag / 目标同步的开镜标记
     * @param distanceSquared   squared feet-to-feet distance viewer to target / 观察者到目标脚到脚距离的平方
     */
    public static boolean cloaks(boolean confirmedServer, boolean spectatorViewer, boolean self, boolean scoped,
                                 double distanceSquared) {
        return scoped && confirmedServer && !self && !spectatorViewer && distanceSquared <= RANGE_SQUARED;
    }
}
