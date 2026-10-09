package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.block.BedBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server player pick for a match shot (D5): the nearest eligible player along the traced path, tested against
 * lag-compensated volumes ({@link PlayerHitboxHistory#hitVolumes} grown by {@link UsecRules#HIT_MARGIN}), because the
 * shooter aimed at its delayed client view. Players never cut the block trace; the caller compares the pick with Seeker
 * devices and Magician puppets on the same path distance. {@link #withSleeperOnBed} adds Wathe's revolver bed rule for
 * sleepers ({@link UsecShotBeds}); sleep state is read at the server's current tick, not rewound.
 * 对局射击的服务端玩家选取（D5）：沿追踪路径最近的合格玩家，针对延迟补偿体积（{@link PlayerHitboxHistory#hitVolumes}
 * 按 {@link UsecRules#HIT_MARGIN} 扩大）检测，因为射手瞄准的是客户端的延迟画面。玩家不截断方块追踪；调用方用同一路径距离把
 * 选取结果与搜寻者设备和魔术师皮套比较。{@link #withSleeperOnBed} 加入 Wathe 左轮对睡觉玩家的床铺规则（{@link UsecShotBeds}）；
 * 睡眠状态读取服务端当前刻，不做延迟回溯。
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

    /**
     * Wathe revolver parity for sleepers ({@link UsecShotBeds}): when the first block the round met is a bed whose
     * sleeper Wathe's own lookup finds and the AXMC may hit ({@link #isEligible}), that sleeper competes with
     * {@code boxHit} at the bed's path distance and the nearer one is returned (the box pick keeps a tie); otherwise
     * {@code boxHit} unchanged. Reads only the block state of a cell the tracer already reached, so no chunk loads.
     * 与 Wathe 左轮一致地处理睡觉的玩家（{@link UsecShotBeds}）：子弹遇到的第一个方块是床、Wathe 自身的查找能找到其上睡觉的玩家
     * 且 AXMC 可以命中该玩家（{@link #isEligible}）时，该玩家以床的路径距离与 {@code boxHit} 比较，返回较近者（距离相同时保留
     * 箱体选取）；否则原样返回 {@code boxHit}。只读取追踪器已到达方块格的方块状态，不会加载区块。
     */
    static @Nullable PlayerHit withSleeperOnBed(ServerPlayerEntity shooter, UsecTracer.Trace trace,
                                                @Nullable PlayerHit boxHit) {
        ServerWorld world = shooter.getServerWorld();
        UsecShotBeds.Hit<ServerPlayerEntity> sleeper = UsecShotBeds.sleeperHit(trace,
                pos -> world.getBlockState(pos).getBlock() instanceof BedBlock,
                bed -> sleeperOn(world, bed),
                candidate -> isEligible(shooter, candidate));
        UsecShotBeds.Hit<ServerPlayerEntity> nearest = UsecShotBeds.nearer(
                boxHit == null ? null : new UsecShotBeds.Hit<>(boxHit.player(), boxHit.distance()), sleeper);
        return sleeper != null && nearest == sleeper ? new PlayerHit(sleeper.target(), sleeper.distance()) : boxHit;
    }

    /**
     * External seam: Wathe's own sleeper lookup ({@code RevolverItem.findSleepingPlayerOnBed}: a head or foot part
     * resolves to the head cell, then the first sleeping player whose sleeping position is that head cell or the hit
     * cell), so the AXMC finds exactly the player the revolver would. Only the hit result's block position is read.
     * SparkWitch's client-only Wraith filter on that method returns server-world answers unchanged.
     * 外部接缝：Wathe 自身的睡觉玩家查找（{@code RevolverItem.findSleepingPlayerOnBed}：床头或床尾都解析到床头格，再取睡眠位置为
     * 该床头格或命中格的第一名睡觉玩家），因此 AXMC 找到的正是左轮会找到的玩家。只读取命中结果的方块位置。SparkWitch 挂在该方法
     * 上的仅客户端冤魂过滤对服务端世界的结果原样返回。
     */
    private static @Nullable ServerPlayerEntity sleeperOn(ServerWorld world, UsecShotBeds.BedContact bed) {
        return RevolverItem.findSleepingPlayerOnBed(world,
                        new BlockHitResult(bed.point(), Direction.UP, bed.pos(), false))
                .filter(ServerPlayerEntity.class::isInstance)
                .map(ServerPlayerEntity.class::cast)
                .orElse(null);
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
