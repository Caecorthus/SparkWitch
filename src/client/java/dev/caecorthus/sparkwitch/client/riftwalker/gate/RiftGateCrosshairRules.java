package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUser;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the melee pass-through (R1): a gate is crosshair-targetable ({@code canHit}) only so its users can
 * right-click it. For everyone else the vanilla crosshair ignores gates, so a gate can never shield a player behind it;
 * a gate user's left-click aimed at a gate is retargeted to whatever is behind it, or swallowed.
 * 近战穿门的纯规则（R1）：门可被准星选中（{@code canHit}）只是为了让使用者右键进门。对其他所有人，原版准星忽略门，因此门永远
 * 挡不住门后的玩家；使用者对着门的左键会改打门后的目标，没有目标则吞掉这次攻击。
 */
public final class RiftGateCrosshairRules {
    /** What a left-click resolved to. / 左键攻击的结果。 */
    public enum AttackOutcome {
        /** The crosshair target was not a gate: attack it unchanged. / 准星目标不是门：照常攻击。 */
        ATTACK_AIMED,
        /** Aimed at a gate: attack the entity the gate-free re-pick found behind it. / 对着门：攻击忽略门后选中的实体。 */
        ATTACK_BEHIND,
        /** Aimed at a gate with no entity behind it: nothing is attacked (no packet). / 门后没有实体：不攻击（不发包）。 */
        SWALLOW
    }

    private RiftGateCrosshairRules() {
    }

    /**
     * A local gate user: may use gates by RAW role and faction ({@link RiftGateUser#mayUse()}), playing and alive in
     * Wathe, a survival/adventure body (not spectator or creative, so in-gate occupants and dead players are not), and
     * not an active Wraith. Mirrors the server's entry participation check; unknown (null) is never a user.
     * 本地门使用者：按原始职业与阵营可用门（{@link RiftGateUser#mayUse()}）、在 Wathe 中参与且存活、本体为生存/冒险（非旁观或创造，
     * 因此门内者与死者都不算）、且不是激活冤魂。与服务端进门的参与者检查一致；未知（null）永远不是使用者。
     */
    public static boolean isLocalGateUser(@Nullable RiftGateUser user, boolean playingAndAlive, boolean survivalBody,
                                          boolean activeWraith) {
        return user != null && user.mayUse() && playingAndAlive && survivalBody && !activeWraith;
    }

    /**
     * Whether gates stay in the vanilla crosshair pick: only for the local player's own view, as a gate user, and not
     * during the gate-free re-pick of an attack.
     * 原版准星选取是否保留门：仅当镜头是本地玩家本人、其为门使用者、且不在攻击的「忽略门」重选中。
     */
    public static boolean keepsGatesTargetable(boolean cameraIsLocalPlayer, boolean ignoringGates,
                                               boolean localGateUser) {
        return cameraIsLocalPlayer && !ignoringGates && localGateUser;
    }

    /** Resolves a left-click (see {@link AttackOutcome}). / 解析一次左键攻击。 */
    public static AttackOutcome attackOutcome(boolean aimedAtGate, boolean repickIsEntity, boolean repickIsGate) {
        if (!aimedAtGate) {
            return AttackOutcome.ATTACK_AIMED;
        }
        return repickIsEntity && !repickIsGate ? AttackOutcome.ATTACK_BEHIND : AttackOutcome.SWALLOW;
    }
}
