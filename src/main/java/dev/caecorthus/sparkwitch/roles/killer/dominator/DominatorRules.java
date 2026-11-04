package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Dominator tuning and the shared crosshair target resolver.
 * 支配者的数值与客户端/服务端共用的准星目标解析。
 */
public final class DominatorRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "dominator");
    public static final Identifier FORCED_SHOT_ID = Identifier.of("sparkwitch", "dominator_forced_shot");
    // Dark green as requested by the owner. / 所有者要求的深绿色。
    public static final int COLOR = 0x006400;
    // Starts empty; every personal kill grants one use, capped at two. / 开局为 0；亲手击杀 +1，上限 2。
    public static final int INITIAL_USES = 0;
    public static final int MAX_USES = 2;
    public static final int MARK_DURATION_TICKS = 30 * 20;
    // Paid to the Dominator when the forced shot kills a player; the forced shooter's own kill money stays suppressed.
    // 强制开枪打死玩家时发给支配者的奖金；被强制开枪者自己的击杀奖金仍被压制。
    public static final int FORCED_SHOT_KILL_REWARD = 100;
    // G stays on cooldown after the mark ends (expired, invalidated or released) and at round start.
    // 标记结束（到期/目标失效/释放）后以及开局时，G 进入冷却。
    public static final int MARK_END_COOLDOWN_TICKS = 10 * 20;
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;
    // Forced-shot ray: Wathe's revolver ray is 30 blocks; a range trait multiplies it.
    // 强制开枪射线：Wathe 左轮射线为 30 格，射程词条会按倍率延长。
    public static final double FORCED_SHOT_RANGE = 30.0D;
    // Owner decision: ignore distance and line of sight; 150 blocks is the practical cap.
    // 所有者决定：无视距离与视线遮挡，150 格作为实际上限。
    public static final double TARGET_RANGE = 150.0D;
    public static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;

    private DominatorRules() {
    }

    public static boolean isDominator(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Nearest player hit by the crosshair ray. Entity-only raycast: blocks never block the ray and visibility
     * ({@code canSee}) is deliberately not checked. Works on both sides.
     * 准星射线命中的最近玩家。只做实体射线：方块不遮挡，且刻意不检查 {@code canSee}。客户端与服务端通用。
     */
    public static @Nullable PlayerEntity findAimedPlayer(PlayerEntity caster) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(TARGET_RANGE));
        PlayerEntity nearest = null;
        double nearestSquared = TARGET_RANGE_SQUARED;
        // Only players are candidates, so this is O(roster) rather than scanning every entity within 150 blocks.
        // 候选只有玩家，因此复杂度是玩家数，而不是扫描 150 格内的所有实体。
        for (PlayerEntity target : caster.getWorld().getPlayers()) {
            if (target == caster
                    || target.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(target)) {
                continue;
            }
            Optional<Vec3d> hit = target.getBoundingBox()
                    .expand(target.getTargetingMargin())
                    .raycast(start, end);
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
