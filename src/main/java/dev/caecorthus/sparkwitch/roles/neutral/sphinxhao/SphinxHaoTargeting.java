package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral 嘉豪病毒 targeting (Specter model): the nearest player hit by the crosshair ray within
 * {@link SphinxHaoRules#INFECT_RANGE} blocks, in line of sight, that passes the Specter's public side-neutral filter
 * {@link SpecterRules#isMarkable} (never the caster; a living, non-spectator, non-creative participant, so never a Rift
 * Gate occupant or a swallowed player; not invisible; not an active Wraith; not hidden from the caster's instinct by
 * SparkTraits). A HUD may name exactly this player; the server then refuses an already infected one and asks
 * SparkFactionAPI {@code canAffectPlayer} itself (server-only). A refused target is never swapped for the one behind it.
 * 两端通用的嘉豪病毒选目标（幽魂模式）：准星射线 {@link SphinxHaoRules#INFECT_RANGE} 格内、视线可见、且通过幽魂公开两端通用
 * 过滤 {@link SpecterRules#isMarkable} 的最近玩家（不是施法者；存活、非旁观、非创造的参与者，因此不会是裂隙门内或被吞下的
 * 玩家；未隐身；不是激活冤魂；未被 SparkTraits 对施法者本能隐藏）。HUD 可以点名的正是该玩家；服务端随后拒绝已感染者，并自行
 * 询问 SparkFactionAPI {@code canAffectPlayer}（仅服务端）。被拒绝的目标不会换成其身后的玩家。
 */
public final class SphinxHaoTargeting {
    private SphinxHaoTargeting() {
    }

    public static @Nullable PlayerEntity findInfectTarget(PlayerEntity caster) {
        double range = SphinxHaoRules.INFECT_RANGE;
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(range));
        PlayerEntity nearest = null;
        double nearestSquared = range * range;
        for (PlayerEntity target : caster.getWorld().getPlayers()) {
            if (!SpecterRules.isMarkable(caster, target) || !caster.canSee(target)) {
                continue;
            }
            Optional<Vec3d> hit = target.getBoundingBox().expand(target.getTargetingMargin()).raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double hitSquared = hit.get().squaredDistanceTo(start);
            if (hitSquared <= nearestSquared) {
                nearestSquared = hitSquared;
                nearest = target;
            }
        }
        return nearest;
    }
}
