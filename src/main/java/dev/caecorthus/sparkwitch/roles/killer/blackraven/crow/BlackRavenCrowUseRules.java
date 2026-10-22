package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.mixin.seeker.SeekerTrapdoorBlockAccessor;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral rules of the crow's own right-click ({@code sparkwitch:black_raven_crow_use}), a Raven-owned copy of
 * {@code SeekerCarUseRules} adapted to a flying camera: the supported-block whitelist (Wathe small and train doors,
 * hand-openable vanilla doors and trapdoors, fence gates, buttons, levers), vanilla's hit tolerance, reach with a
 * latency slack, a 3D look cone instead of the car's horizontal one, the throttle, the sight-ray end, the halves of
 * two-block doors and the empty-hand Wathe door decision. The owner's client may use them to aim and send; the server
 * re-checks every one of them. The crow has no hands: nothing here ever looks at a held item.
 * 乌鸦自身右键交互（{@code black_raven_crow_use}）的两端通用规则，是 {@code SeekerCarUseRules} 的黑羽鸦自有副本，并按飞行镜头调整：
 * 支持方块白名单（Wathe 小门与列车门、可徒手开启的原版门与活板门、栅栏门、按钮、拉杆）、原版命中容差、带延迟余量的触及距离、
 * 三维视线锥（取代小车的水平锥角）、节流、视线射线终点、两格高门的上下半，以及空手的 Wathe 门判定。主人客户端可用它们瞄准与发送；
 * 服务端逐条重新校验。乌鸦没有手：这里从不查看任何手持物品。
 */
public final class BlackRavenCrowUseRules {
    /** Vanilla's per-axis hit-to-block-centre limit. / 原版命中点到方块中心的逐轴上限。 */
    public static final double HIT_TOLERANCE = 1.0000001;
    /** Eye-to-hit reach the owner's client aims with, in blocks. / 主人客户端瞄准使用的眼到命中点距离（格）。 */
    public static final double USE_REACH = 3.0;
    /**
     * Server-only reach slack: at 1.2 blocks per tick the server's crow may trail the client-predicted one by a tick
     * or so. The server accepts up to {@code USE_REACH + REACH_SLACK} (vanilla's 4.5-block player reach).
     * 仅服务端的触及余量：以每刻 1.2 格飞行时，服务端乌鸦可能落后客户端预测约一刻。服务端最多接受
     * {@code USE_REACH + REACH_SLACK}（即原版玩家的 4.5 格触及距离）。
     */
    public static final double REACH_SLACK = 1.5;
    /** The sight ray runs this far past the claimed hit, so a surface hit still lands. / 视线射线越过命中点的长度。 */
    public static final double RAY_OVERSHOOT = 0.05;
    /** Minimum client ticks between two sends (vanilla's item-use delay). / 客户端两次发送的最小间隔（原版物品使用间隔）。 */
    public static final int USE_INTERVAL_TICKS = 4;
    /**
     * Server throttle window, shorter than the client interval so network jitter never drops an open-then-close.
     * 服务端节流窗口，短于客户端间隔，网络抖动不会丢掉正常的先开后关。
     */
    public static final int SERVER_THROTTLE_TICKS = USE_INTERVAL_TICKS - 2;
    /**
     * Half-angle of the crow's look cone in degrees: the direction from the crow's eye to the claimed hit must lie
     * within this angle of the crow's look (yaw and pitch). Wide enough for a server crow whose look trails by a tick;
     * a block behind the crow never qualifies.
     * 乌鸦视线锥的半角（度）：从乌鸦眼睛到声明命中点的方向与乌鸦视线（偏航与俯仰）的夹角不得超过此值。足以容忍服务端乌鸦视线
     * 落后一刻；乌鸦身后的方块永远不符合。
     */
    public static final double LOOK_CONE_DEGREES = 80.0;
    /** Below this eye-to-hit distance the direction means nothing, so the cone is skipped. / 距离过近时跳过锥角检查。 */
    public static final double LOOK_CONE_MIN_DISTANCE = 0.3;
    private static final double LOOK_CONE_COS = Math.cos(Math.toRadians(LOOK_CONE_DEGREES));

    private BlackRavenCrowUseRules() {
    }

    /**
     * The whitelist: Wathe small and train doors, hand-openable vanilla doors and trapdoors, fence gates, buttons
     * (Wathe's included) and levers. Vents, containers, beds, seats and everything else answer null.
     * 白名单：Wathe 小门与列车门、可徒手开启的原版门与活板门、栅栏门、按钮（含 Wathe 按钮）与拉杆。其余一律返回 null。
     */
    public static @Nullable Target target(@Nullable BlockState state) {
        if (state == null) {
            return null;
        }
        Block block = state.getBlock();
        if (block instanceof TrainDoorBlock) {
            return Target.WATHE_TRAIN_DOOR;
        }
        if (block instanceof SmallDoorBlock) {
            return Target.WATHE_DOOR;
        }
        if (block instanceof DoorBlock) {
            return DoorBlock.canOpenByHand(state) ? Target.DOOR : null;
        }
        if (block instanceof TrapdoorBlock) {
            // Read-only reuse of the Seeker's vanilla-field accessor (no behaviour of the target changes).
            // 只读复用搜寻者的原版字段访问器（不改变目标的任何行为）。
            return block instanceof SeekerTrapdoorBlockAccessor trapdoor
                    && trapdoor.sparkwitch$getBlockSetType().canOpenByHand() ? Target.TRAPDOOR : null;
        }
        if (block instanceof FenceGateBlock) {
            return Target.FENCE_GATE;
        }
        if (block instanceof ButtonBlock) {
            return Target.BUTTON;
        }
        return block instanceof LeverBlock ? Target.LEVER : null;
    }

    /**
     * Server throttle (not a cooldown); a last use "in the future" (a restarted tick counter) never throttles.
     * 服务端节流（非冷却）；记录在“未来”时不节流。
     */
    public static boolean isThrottled(long lastUseTick, long now) {
        return lastUseTick >= 0 && now >= lastUseTick && now - lastUseTick < SERVER_THROTTLE_TICKS;
    }

    public static boolean isFinite(@Nullable Vec3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    /** Vanilla's block-hit sanity check: every axis within 1.0000001 of the block centre. / 原版命中合理性检查。 */
    public static boolean withinHitTolerance(Vec3d hit, BlockPos pos) {
        return Math.abs(hit.x - (pos.getX() + 0.5)) < HIT_TOLERANCE
                && Math.abs(hit.y - (pos.getY() + 0.5)) < HIT_TOLERANCE
                && Math.abs(hit.z - (pos.getZ() + 0.5)) < HIT_TOLERANCE;
    }

    /** Server reach: {@link #USE_REACH} plus {@link #REACH_SLACK}, inclusive and NaN-safe. / 服务端触及判定。 */
    public static boolean withinServerReach(double squaredDistance) {
        double limit = USE_REACH + REACH_SLACK;
        return squaredDistance >= 0.0 && squaredDistance <= limit * limit;
    }

    /**
     * Look cone: true when the direction from {@code eye} to {@code hit} is within {@link #LOOK_CONE_DEGREES} of the look
     * given by vanilla {@code yawDegrees}/{@code pitchDegrees} (yaw 0 faces +Z, 90 faces -X; pitch 90 faces down), or
     * when the hit is closer than {@link #LOOK_CONE_MIN_DISTANCE}. False for any non-finite input.
     * 视线锥：从 {@code eye} 到 {@code hit} 的方向与原版 {@code yawDegrees}/{@code pitchDegrees} 给出的视线夹角不超过
     * {@link #LOOK_CONE_DEGREES} 时为 true；命中点距离小于 {@link #LOOK_CONE_MIN_DISTANCE} 时也为 true。任一输入非有限值时为 false。
     */
    public static boolean withinLookCone(@Nullable Vec3d eye, float yawDegrees, float pitchDegrees,
                                         @Nullable Vec3d hit) {
        if (!isFinite(eye) || !isFinite(hit) || !Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) {
            return false;
        }
        double dx = hit.x - eye.x;
        double dy = hit.y - eye.y;
        double dz = hit.z - eye.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < LOOK_CONE_MIN_DISTANCE) {
            return true;
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double lookX = -Math.sin(yaw) * Math.cos(pitch);
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * Math.cos(pitch);
        return (dx * lookX + dy * lookY + dz * lookZ) / length >= LOOK_CONE_COS;
    }

    /** End of the server's sight ray: just past the claimed hit. / 服务端视线射线终点：略过声明命中点。 */
    public static Vec3d rayEnd(Vec3d eye, Vec3d hit) {
        Vec3d delta = hit.subtract(eye);
        double length = delta.length();
        if (length < 1.0E-7) {
            return hit;
        }
        return eye.add(delta.multiply((length + RAY_OVERSHOOT) / length));
    }

    /** Lower half of a two-block door. / 两格高门的下半格。 */
    public static BlockPos lowerHalf(BlockPos pos, boolean upper) {
        return upper ? pos.down() : pos;
    }

    /**
     * True for the same position, or the two halves of one door (same block, both halves, same lower position).
     * 同一位置，或同一扇门的上下两半时为 true。
     */
    public static boolean sameTarget(BlockView world, BlockPos a, BlockPos b) {
        if (a.equals(b)) {
            return true;
        }
        BlockState first = world.getBlockState(a);
        BlockState second = world.getBlockState(b);
        if (first.getBlock() != second.getBlock() || !first.contains(Properties.DOUBLE_BLOCK_HALF)
                || !second.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        return lowerHalf(a, first.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                .equals(lowerHalf(b, second.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER));
    }

    /**
     * Client aim fix: a steep ray can hit one door half's shape inside the other half's cell, which vanilla's tolerance
     * rejects; the hit is re-attributed to the half that contains it, null when neither fits.
     * 客户端瞄准修正：陡峭射线可能在另一半的格子里命中这一半的形状；此时把命中归到包含命中点的那一半，两半都不符合时返回 null。
     */
    public static @Nullable BlockHitResult attributeHit(BlockView world, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3d point = hit.getPos();
        if (withinHitTolerance(point, pos)) {
            return hit;
        }
        BlockState state = world.getBlockState(pos);
        if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return null;
        }
        BlockPos other = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
        if (!withinHitTolerance(point, other) || !sameTarget(world, pos, other)) {
            return null;
        }
        return new BlockHitResult(point, hit.getSide(), other, hit.isInsideBlock());
    }

    /**
     * Wathe's own interaction type for an empty hand (its key, lockpick and crowbar branches cannot occur).
     * 空手时 Wathe 自己的交互类型（钥匙、撬锁器与撬棍分支不会出现）。
     */
    public static DoorInteraction.DoorInteractionType emptyHandInteraction(boolean train, boolean blasted,
                                                                           boolean open, boolean requiresKey) {
        if (blasted) {
            return DoorInteraction.DoorInteractionType.BLASTED;
        }
        if (open) {
            return DoorInteraction.DoorInteractionType.CLOSE;
        }
        return train || requiresKey
                ? DoorInteraction.DoorInteractionType.INTERACT
                : DoorInteraction.DoorInteractionType.OPEN;
    }

    /**
     * Wathe's empty-hand door outcome after {@code DoorInteraction.EVENT}: ALLOW toggles, DENY and HANDLED do nothing,
     * PASS runs Wathe's own branches (locked train doors, key doors and jammed doors refuse as for an empty hand).
     * {@code DoorInteraction.EVENT} 之后 Wathe 空手开门的结果：ALLOW 切换，DENY 与 HANDLED 不做任何事，PASS 执行 Wathe 自身分支
     * （上锁的列车门、需要钥匙的门与被卡住的门如同空手一样拒绝）。
     */
    public static DoorOutcome doorOutcome(DoorInteraction.DoorInteractionResult event, boolean train,
                                          boolean creative, boolean gameInactive, boolean blasted, boolean open,
                                          boolean requiresKey, boolean jammed) {
        if (event == DoorInteraction.DoorInteractionResult.ALLOW) {
            return DoorOutcome.TOGGLE;
        }
        if (event != DoorInteraction.DoorInteractionResult.PASS || blasted) {
            return DoorOutcome.NONE;
        }
        if (creative || open || train && gameInactive) {
            return DoorOutcome.TOGGLE;
        }
        if (train) {
            return DoorOutcome.LOCKED;
        }
        if (requiresKey && !jammed) {
            return DoorOutcome.REQUIRES_KEY;
        }
        return jammed ? DoorOutcome.JAMMED : DoorOutcome.TOGGLE;
    }

    /** Supported block kinds. / 支持的方块种类。 */
    public enum Target {
        WATHE_DOOR,
        WATHE_TRAIN_DOOR,
        DOOR,
        TRAPDOOR,
        FENCE_GATE,
        BUTTON,
        LEVER
    }

    /** Empty-hand door result; refusals carry Wathe's own action-bar key. / 空手开门结果；拒绝时附带 Wathe 自己的提示键。 */
    public enum DoorOutcome {
        TOGGLE(null),
        NONE(null),
        REQUIRES_KEY("tip.door.requires_key"),
        JAMMED("tip.door.jammed"),
        LOCKED("tip.door.locked");

        private final @Nullable String messageKey;

        DoorOutcome(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        public @Nullable String messageKey() {
            return messageKey;
        }
    }
}
