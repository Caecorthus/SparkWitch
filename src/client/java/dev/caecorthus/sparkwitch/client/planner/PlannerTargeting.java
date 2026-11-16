package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Master Planner's client-side aim, shared by the ability key and the HUD preview so the HUD names whom a press
 * would go to (except a player hidden by BEAR smoke, whom the hint never names). The look ray from the eyes is
 * {@link PlannerRules#REACH} long and is clipped at the first block it hits (collider shapes, no fluids, like
 * {@code LivingEntity.canSee}), so a player behind a wall is never picked; PR #219's entity-only raycast went through
 * walls. The nearest visible living participant the clipped ray enters is the target; with none, the request names
 * the planner herself. Candidates are public-state only: not the viewer, not a spectator (Rift occupants, swallowed
 * players), playing and alive in Wathe, and visible to the viewer as a living player (not invisible, not a
 * NoellesRoles Morphling in corpse mode). BEAR smoke is deliberately not asked here: this selector builds a packet,
 * and the house BEAR smoke contract is for HUD hints only, so {@link PlannerHud} applies it to its preview alone.
 * Client/server authority: the UUID is only a hint; the server re-checks role, round, control state, cooldown, coins,
 * reach and line of sight, and may refuse. Client thread only.
 * 规划大师的客户端瞄准，由技能键与 HUD 预览共用，使 HUD 写出的就是按下后的对象（被 BEAR 烟雾挡住的玩家除外，提示从不写出
 * 其名字）。视线射线从眼睛出发、长 {@link PlannerRules#REACH} 格，并在命中的第一个方块处截断（碰撞形状、不含流体，与
 * {@code LivingEntity.canSee} 相同），因此墙后的玩家永远不会被选中；PR #219 只做实体射线，会穿墙。截断后的射线最先进入的
 * 可见、存活参与者即为目标；没有目标时请求指向规划大师自己。候选者只按公开状态过滤：不是观察者本人、不是旁观者（裂隙门内或
 * 被吞下的玩家）、在 Wathe 中参与且存活，并且在观察者眼中是可见的活人（未隐身、不是装死中的 NoellesRoles 变形者）。这里刻意
 * 不询问 BEAR 烟雾：本选择器用于构造数据包，而 BEAR 烟雾契约只用于 HUD 提示，因此只由 {@link PlannerHud} 在其预览中使用。
 * 客户端/服务端权威：UUID 只是提示；服务端会重新校验职业、回合、受控状态、冷却、金币、距离与视线，并可能拒绝。仅限客户端线程。
 */
public final class PlannerTargeting {
    private PlannerTargeting() {
    }

    /**
     * The player the planner aims at, or {@code null} for "nobody" (the request then names herself).
     * 规划大师瞄准的玩家；返回 {@code null} 表示“没有目标”（此时请求指向她自己）。
     */
    public static @Nullable PlayerEntity aimedPlayer(ClientPlayerEntity viewer) {
        Vec3d start = viewer.getEyePos();
        Vec3d look = viewer.getRotationVec(1.0F);
        BlockHitResult block = viewer.getWorld().raycast(new RaycastContext(start,
                start.add(look.multiply(PlannerRules.REACH)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, viewer));
        Vec3d end = segmentEnd(start, look, PlannerRules.REACH,
                block.getType() == HitResult.Type.MISS ? null : block.getPos());
        return nearestOnSegment(start, end, viewer.getWorld().getPlayers(),
                candidate -> isCandidate(candidate == viewer, candidate.isSpectator(),
                        GameFunctions.isPlayerPlayingAndAlive(candidate), isHiddenFrom(viewer, candidate)),
                candidate -> candidate.getBoundingBox().expand(candidate.getTargetingMargin()));
    }

    /**
     * Whether a player may be picked: never the viewer, a spectator, a dead or non-participant player, or one the
     * viewer cannot see as a living player.
     * 玩家能否被选中：观察者本人、旁观者、死亡或未参与的玩家，以及观察者看不见其为活人的玩家都不行。
     */
    public static boolean isCandidate(boolean self, boolean spectator, boolean playingAndAlive, boolean hidden) {
        return !self && !spectator && playingAndAlive && !hidden;
    }

    /**
     * Where the aim segment ends: the first block hit when there is one, else {@code reach} blocks along the look.
     * 瞄准线段的终点：有方块命中时为该命中点，否则为沿视线 {@code reach} 格处。
     */
    public static Vec3d segmentEnd(Vec3d start, Vec3d look, double reach, @Nullable Vec3d blockHit) {
        return blockHit != null ? blockHit : start.add(look.multiply(reach));
    }

    /**
     * The kept candidate whose box the segment enters first (a box around {@code start} counts at distance 0), or
     * {@code null}. A box past {@code end} (behind the clipping block, or out of reach) is never entered.
     * 线段最先进入其碰撞箱的保留候选者（包住 {@code start} 的碰撞箱按距离 0 计）；没有则为 {@code null}。位于 {@code end}
     * 之后（截断方块后方或超出距离）的碰撞箱永远不会被进入。
     */
    @Nullable
    public static <T> T nearestOnSegment(Vec3d start, Vec3d end, Iterable<? extends T> candidates,
                                         Predicate<? super T> keep, Function<? super T, Box> boxOf) {
        T nearest = null;
        double nearestSquared = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            if (!keep.test(candidate)) {
                continue;
            }
            Box box = boxOf.apply(candidate);
            Optional<Vec3d> hit = box.contains(start) ? Optional.of(start) : box.raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double squared = start.squaredDistanceTo(hit.get());
            if (squared < nearestSquared) {
                nearestSquared = squared;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * The UUID the request carries: the aimed player's, or the planner's own when nobody is aimed at.
     * 请求携带的 UUID：准星下玩家的 UUID；没有瞄准任何人时为规划大师自己的 UUID。
     */
    public static UUID payloadTarget(UUID self, @Nullable UUID aimed) {
        return aimed != null ? aimed : self;
    }

    /**
     * Invisible to the viewer (the Dominator HUD rule) or lying as a NoellesRoles Morphling corpse (it reads as a dead
     * body; naming it would reveal a living player).
     * 对观察者隐身（与支配者 HUD 规则相同），或是装死中的 NoellesRoles 变形者（看起来是尸体；写出名字会暴露活人）。
     */
    private static boolean isHiddenFrom(PlayerEntity viewer, PlayerEntity candidate) {
        return candidate.isInvisible() || candidate.isInvisibleTo(viewer)
                || MorphlingPlayerComponent.KEY.get(candidate).corpseMode;
    }
}
