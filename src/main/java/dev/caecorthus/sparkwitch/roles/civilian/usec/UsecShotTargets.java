package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server player pick for a match shot (D5): the nearest eligible player along the traced path, tested against
 * lag-compensated volumes ({@link PlayerHitboxHistory#hitVolumes} grown by {@link UsecRules#HIT_MARGIN}), because the
 * shooter aimed at its delayed client view. Players never cut the block trace; the caller compares the pick with Seeker
 * devices and Magician puppets on the same path distance.
 * 对局射击的服务端玩家选取（D5）：沿追踪路径最近的合格玩家，针对延迟补偿体积（{@link PlayerHitboxHistory#hitVolumes}
 * 按 {@link UsecRules#HIT_MARGIN} 扩大）检测，因为射手瞄准的是客户端的延迟画面。玩家不截断方块追踪；调用方用同一路径距离把
 * 选取结果与搜寻者设备和魔术师皮套比较。
 */
final class UsecShotTargets {
    /**
     * Padding of the path's bounds when pre-filtering candidates: a rewound volume trails the current box by at most
     * one second of movement. / 预筛候选时路径包围盒的外扩量：回溯体积落后当前箱体至多一秒的移动距离。
     */
    private static final double CANDIDATE_PADDING = 24.0;

    private UsecShotTargets() {
    }

    /** The player a match shot hits and its path distance. / 对局射击命中的玩家及其路径距离。 */
    record PlayerHit(ServerPlayerEntity player, double distance) {
    }

    /** Nearest eligible player entered along {@code path}, or null. / 沿 {@code path} 最先进入的合格玩家；没有时为 null。 */
    static @Nullable PlayerHit nearestPlayer(ServerPlayerEntity shooter, UsecShotPath path) {
        Box bounds = bounds(path).expand(CANDIDATE_PADDING);
        PlayerHit nearest = null;
        for (ServerPlayerEntity candidate : shooter.getServerWorld().getPlayers()) {
            if (!candidate.getBoundingBox().intersects(bounds) || !isEligible(shooter, candidate)) {
                continue;
            }
            List<Box> volumes = PlayerHitboxHistory.hitVolumes(shooter, candidate, UsecRules.HIT_MARGIN);
            double limit = nearest == null ? path.length() : nearest.distance();
            double distance = path.entryDistance(volumes, limit);
            if (distance >= 0.0 && (nearest == null || distance < nearest.distance())) {
                nearest = new PlayerHit(candidate, distance);
            }
        }
        return nearest;
    }

    /** {@link UsecFireRules#targetEligible} from live state. / 依据实时状态的 {@link UsecFireRules#targetEligible}。 */
    static boolean isEligible(ServerPlayerEntity shooter, ServerPlayerEntity candidate) {
        boolean self = candidate == shooter || candidate.getUuid().equals(shooter.getUuid());
        return UsecFireRules.targetEligible(
                self,
                GameFunctions.isPlayerSpectatingOrCreative(candidate),
                GameFunctions.isPlayerPlayingAndAlive(candidate),
                WraithStateService.isActive(candidate),
                SparkTraitsKillerBridge.isLastEscapeActive(candidate),
                VendettaInteractionService.isActiveVendetta(shooter),
                VendettaInteractionService.isActiveVendetta(candidate),
                !self && VendettaInteractionService.isExactPair(shooter, candidate));
    }

    private static Box bounds(UsecShotPath path) {
        Vec3d first = path.start();
        double minX = first.x;
        double minY = first.y;
        double minZ = first.z;
        double maxX = first.x;
        double maxY = first.y;
        double maxZ = first.z;
        for (Vec3d point : path.points()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
