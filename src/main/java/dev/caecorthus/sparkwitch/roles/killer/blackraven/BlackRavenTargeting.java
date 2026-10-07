package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves Feather Blade targets along the Black Raven's aim. Side-neutral: the caller supplies where each candidate
 * can be hit (current boxes, or the server's lag-compensated volumes). The aim stops at the first collider block, so
 * reaching a target means the Black Raven saw it; reach is still the feet-to-feet distance, taken where the aim hit.
 * 沿黑羽鸦的瞄准方向解析羽刃目标。两端通用：由调用方提供候选者可被命中的位置（当前箱体，或服务端的延迟补偿体积）。
 * 瞄准射线止于第一个碰撞方块，因此能命中即代表黑羽鸦看得见；射程仍是脚到脚的距离，取瞄准命中处计算。
 */
public final class BlackRavenTargeting {
    /**
     * Feather Blade hit margin: none. This is the bare player box that vanilla {@code ProjectileUtil.raycast} tested
     * before lag compensation (players have no targeting margin).
     * 羽刃命中余量：无。即延迟补偿前原版 {@code ProjectileUtil.raycast} 检测的裸玩家箱体（玩家没有瞄准余量）。
     */
    public static final double FEATHER_BOX_EXPANSION = 0.0D;

    private BlackRavenTargeting() {
    }

    /** The aimed player and its squared feet distance where the aim hit. / 被瞄准的玩家及瞄准命中处的脚部平方距离。 */
    public record Aim<T>(T player, double feetDistanceSquared) {
    }

    /**
     * Nearest eligible player on {@code user}'s eye ray within {@link BlackRavenRules#FEATHER_REACH}, tested against
     * {@code hitVolumes} (grown by {@link #FEATHER_BOX_EXPANSION} only). On the server, vanilla has already applied
     * the use packet's yaw and pitch, so this is the Black Raven's aim.
     * {@code user} 视线射线上 {@link BlackRavenRules#FEATHER_REACH} 内最近的合格玩家，按 {@code hitVolumes}
     * （只含 {@link #FEATHER_BOX_EXPANSION} 余量）判定。服务端调用时原版已应用使用物品数据包中的朝向，因此这就是黑羽鸦的瞄准方向。
     */
    public static <T extends PlayerEntity> @Nullable Aim<T> findAimedPlayer(PlayerEntity user,
                                                                         Iterable<? extends T> candidates,
                                                                         Function<PlayerEntity, List<Box>> hitVolumes) {
        Vec3d eye = user.getEyePos();
        Vec3d end = eye.add(user.getRotationVec(1.0F).multiply(BlackRavenRules.FEATHER_REACH));
        HitResult block = user.getWorld().raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        // Spectators are transparent: a Rift Gate occupant is an ALIVE spectator that no mark may reach (Riftwalker
        // D3), and Wathe's liveness check ignores the game mode. An active Vendetta is an adventure-mode Wraith.
        // 旁观者是透明的：裂隙门内的玩家是任何标记都不能触及的存活旁观者（隙行者 D3），而 Wathe 的存活检查不看游戏模式。
        // 激活的复仇者是冒险模式的冤魂，不受影响。
        return nearest(eye, end, user.getPos(), candidates,
                candidate -> candidate != user && !candidate.isSpectator()
                        && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(user, candidate),
                hitVolumes, PlayerEntity::getBoundingBox);
    }

    /**
     * Pure pick. Ineligible candidates are transparent; the eligible one whose volumes the segment enters first takes
     * the aim. It is returned only when, in a volume the aim entered, the nearest feet position a box of the
     * candidate's current size could have had is within reach of {@code userFeet}. Out of reach, it still shields the
     * players behind it, as before.
     * 纯选择。不合格的候选者是透明的；线段最先进入其体积的合格候选者承受这次瞄准。只有当瞄准射线进入的某个体积中，
     * 按候选者当前尺寸可能的最近脚部位置在 {@code userFeet} 射程内时才返回它；超出射程时它仍会像以前一样替身后的玩家挡下。
     */
    static <T> @Nullable Aim<T> nearest(Vec3d eye, Vec3d end, Vec3d userFeet, Iterable<? extends T> candidates,
                                        Predicate<? super T> eligible, Function<? super T, List<Box>> volumesOf,
                                        Function<? super T, Box> currentBoxOf) {
        T selected = null;
        List<Box> selectedVolumes = List.of();
        double closest = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }
            List<Box> volumes = volumesOf.apply(candidate);
            double entry = HitscanLagRules.entryDistanceSquared(eye, end, volumes);
            if (entry >= 0.0D && entry < closest) {
                closest = entry;
                selected = candidate;
                selectedVolumes = volumes;
            }
        }
        if (selected == null) {
            return null;
        }
        Box size = currentBoxOf.apply(selected);
        double feetDistance = Double.POSITIVE_INFINITY;
        for (Box volume : selectedVolumes) {
            if (HitscanLagRules.entryDistanceSquared(eye, end, List.of(volume)) >= 0.0D) {
                feetDistance = Math.min(feetDistance, feetRegion(volume, size).squaredMagnitude(userFeet));
            }
        }
        return feetDistance <= BlackRavenRules.FEATHER_REACH * BlackRavenRules.FEATHER_REACH
                ? new Aim<>(selected, feetDistance)
                : null;
    }

    /**
     * Feet positions a box of {@code size} could have had inside {@code volume}: a single point for a still box (the
     * old feet-to-feet reach), and for a swept one a region that always holds the target's current feet, so the
     * rewound reach is never stricter than the current one. / {@code size} 大小的箱体在 {@code volume} 内可能的脚部位置：
     * 静止箱体时为单点（即原来的脚到脚射程）；扫掠体积时该区域总包含目标当前脚部，因此回溯射程绝不比当前位置更严格。
     */
    static Box feetRegion(Box volume, Box size) {
        double margin = FEATHER_BOX_EXPANSION;
        double halfX = size.getLengthX() / 2.0D + margin;
        double halfZ = size.getLengthZ() / 2.0D + margin;
        return new Box(
                volume.minX + halfX, volume.minY + margin, volume.minZ + halfZ,
                volume.maxX - halfX, volume.maxY - margin - size.getLengthY(), volume.maxZ - halfZ);
    }
}
