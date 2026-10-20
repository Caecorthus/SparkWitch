package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastGeometry;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the Mini Wither's auto-fire target (owner Q2: all around, 24 blocks, unobstructed from the Wither) and answers
 * the per-player questions the skulls and bursts share.
 * 选择小凋灵的自动射击目标（所有者 Q2：360°、24 格、从小凋灵出发无遮挡），并回答头颅与爆炸共用的逐玩家判定。
 */
public final class MiniWitherTargeting {
    private MiniWitherTargeting() {
    }

    /** A chosen target and the visible point the skull aims at. / 选中的目标及头颅瞄准的可见点。 */
    public record Target(ServerPlayerEntity player, Vec3d aimPoint) {
    }

    /**
     * The visible target with the smallest angle from the owner's look direction, then the nearest; null when none.
     * The owner steers the Wither by looking, and nobody behind a wall is ever revealed.
     * 与主人视线方向夹角最小、其次最近的可见目标；没有时为 null。主人可以用视线引导小凋灵，墙后的人也绝不会被暴露。
     */
    @Nullable
    public static Target find(ServerPlayerEntity owner, Vec3d origin, MiniWitherRules.Mode mode,
                              GameWorldComponent game) {
        ServerWorld world = owner.getServerWorld();
        Vec3d look = owner.getRotationVec(1.0F);
        Vec3d eye = owner.getEyePos();
        Target best = null;
        double bestAngle = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == owner || !isLiveParticipant(candidate, game)) {
                continue;
            }
            double distance = origin.distanceTo(candidate.getBoundingBox().getCenter());
            if (distance > MiniWitherRules.TARGET_RANGE
                    || !MiniWitherRules.isTarget(mode, false, isKillerTeam(candidate, game))
                    || !mayAffect(owner, candidate, game)) {
                continue;
            }
            Vec3d aim = visiblePoint(world, origin, candidate);
            if (aim == null) {
                continue;
            }
            double angle = MiniWitherRules.angleTo(look, aim.subtract(eye));
            if (angle < bestAngle || angle == bestAngle && distance < bestDistance) {
                best = new Target(candidate, aim);
                bestAngle = angle;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Alive, playing, survival or adventure, not a spectator (Rift Gate occupants, Taotie prey), not an active Wraith,
     * not in SparkTraits Last Escape, and holding a role.
     * 存活、在局、生存或冒险模式、非旁观（裂隙门内玩家、饕餮吞下者）、非激活冤魂、未处于 SparkTraits 最后逃脱，且拥有职业。
     */
    public static boolean isLiveParticipant(PlayerEntity player, GameWorldComponent game) {
        return GameFunctions.isPlayerAliveAndSurvival(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !(player instanceof ServerPlayerEntity serverPlayer && WraithStateService.isActive(serverPlayer))
                && !SparkTraitsKillerBridge.isLastEscapeActive(player)
                && game.getRole(player) != null;
    }

    /** Effective killer (Impostor included) or Undercover. / 有效阵营为杀手（含内鬼）或卧底。 */
    public static boolean isKillerTeam(PlayerEntity player, GameWorldComponent game) {
        return MiniWitherRules.isKillerTeam(SparkFactionApi.resolveEffectiveFaction(player, game),
                NoellesRoleIds.isUndercover(game.getRole(player)));
    }

    /**
     * SparkFactionAPI's affect veto plus the Vendetta isolation every Witch Maiden weapon honours.
     * SparkFactionAPI 的影响否决，加上巫女所有武器都遵守的复仇者隔离。
     */
    public static boolean mayAffect(ServerPlayerEntity owner, ServerPlayerEntity target, GameWorldComponent game) {
        if (!SparkFactionApi.canAffectPlayer(owner, target, MiniWitherRules.AFFECT_ACTION, game)) {
            return false;
        }
        boolean ownerVendetta = VendettaInteractionService.isActiveVendetta(owner);
        boolean targetVendetta = VendettaInteractionService.isActiveVendetta(target);
        return !(ownerVendetta || targetVendetta) || VendettaInteractionService.isExactPair(owner, target);
    }

    /** First unobstructed sight point (body centre, eye, feet). / 第一个无遮挡的视线采样点（身体中心、眼睛、脚下）。 */
    @Nullable
    public static Vec3d visiblePoint(ServerWorld world, Vec3d origin, PlayerEntity target) {
        var points = PotionBlastGeometry.sightPoints(target.getPos(), target.getBoundingBox(), target.getEyeY());
        for (Vec3d point : new Vec3d[]{points.get(1), points.get(2), points.get(0)}) {
            if (SeekerDamageRules.segmentClear(world, origin, point, null)) {
                return point;
            }
        }
        return null;
    }
}
