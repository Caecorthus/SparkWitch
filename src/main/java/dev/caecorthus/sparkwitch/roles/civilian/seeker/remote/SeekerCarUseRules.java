package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.mixin.seeker.SeekerTrapdoorBlockAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
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
 * Side-neutral rules of the driven car's own right-click ({@code seeker_car_use}): the supported-block whitelist, the
 * hint text, vanilla's hit tolerance, reach with a latency slack, the forward cone, the throttle, the line-of-sight ray
 * end, the halves of two-block doors, and the empty-hand Wathe door decision. The owner's client uses them to aim, hint
 * and send; the server re-checks every one of them. The car has no hands: nothing here ever looks at a held item.
 * 被驾驶小车自身右键交互（{@code seeker_car_use}）的两端通用规则：支持方块白名单、提示文本、原版命中容差、
 * 带延迟余量的触及距离、前方锥角、节流、视线射线终点、两格高门的上下半、以及空手的 Wathe 门判定。
 * 拥有者客户端用它们瞄准、提示与发送；服务端会逐条重新校验。小车没有手：这里从不查看任何手持物品。
 */
public final class SeekerCarUseRules {
    /** Vanilla's per-axis hit-to-block-centre limit ({@code onPlayerInteractBlock}). / 原版命中点到方块中心的逐轴上限。 */
    public static final double HIT_TOLERANCE = 1.0000001;
    /**
     * Server-only reach slack: the server's car may trail the owner-simulated one by a few ticks.
     * 仅服务端的触及余量：服务端小车可能比拥有者模拟的小车落后几刻。
     */
    public static final double REACH_SLACK = 1.0;
    /** The sight ray runs this far past the claimed hit, so a surface hit still lands. / 视线射线越过命中点的长度。 */
    public static final double RAY_OVERSHOOT = 0.05;
    /**
     * Server throttle window: shorter than the client's {@link SeekerRules#CAR_USE_INTERVAL_TICKS} send interval, so
     * one or two ticks of network jitter never drop a legitimate open-then-close; it still caps a flood.
     * 服务端节流窗口：短于客户端的 {@link SeekerRules#CAR_USE_INTERVAL_TICKS} 发送间隔，一两刻的网络抖动不会丢掉
     * 正常的先开后关；仍能限制刷包。
     */
    public static final int SERVER_THROTTLE_TICKS = SeekerRules.CAR_USE_INTERVAL_TICKS - 2;
    /**
     * Half-angle of the car's forward cone, in degrees: the horizontal direction from the car's eye to the claimed hit
     * must lie within this angle of the car's yaw. Wide enough for a server car whose heading trails the driven one by
     * a few ticks; a block beside or behind the car never qualifies.
     * 小车前方锥角的半角（度）：从小车眼睛到声明命中点的水平方向必须位于小车朝向的此角度内。足够宽，可容忍服务端小车的
     * 朝向落后被驾驶小车几刻；位于小车侧面或后方的方块永远不符合。
     */
    public static final double FORWARD_CONE_DEGREES = 80.0;
    /**
     * Below this horizontal distance from the eye the hit lies (nearly) straight above or below the nose, where the
     * horizontal direction means nothing, so the cone check is skipped.
     * 命中点与眼睛的水平距离小于此值时，它（几乎）位于车头正上方或正下方，水平方向没有意义，因此跳过锥角检查。
     */
    public static final double FORWARD_CONE_MIN_HORIZONTAL = 0.3;
    private static final double FORWARD_CONE_COS = Math.cos(Math.toRadians(FORWARD_CONE_DEGREES));
    static final String HINT_OPEN = "hud.sparkwitch.seeker.view.use_open";
    static final String HINT_CLOSE = "hud.sparkwitch.seeker.view.use_close";
    static final String HINT_PRESS = "hud.sparkwitch.seeker.view.use_press";
    static final String HINT_LEVER = "hud.sparkwitch.seeker.view.use_lever";

    private SeekerCarUseRules() {
    }

    /**
     * The whitelist: Wathe small and train doors, hand-openable vanilla doors and trapdoors, fence gates, buttons
     * (Wathe's included) and levers. Vents, containers, beds, seats and everything else answer null.
     * 白名单：Wathe 小门与列车门、可徒手开启的原版门与活板门、栅栏门、按钮（含 Wathe 按钮）与拉杆。
     * 通风口、容器、床、座椅及其他一切返回 null。
     */
    @Nullable
    public static Target target(@Nullable BlockState state) {
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

    /** Lang key of the CCTV hint; {@code active} is open (doors) or powered. / CCTV 提示的语言键。 */
    public static String hintKey(Target target, boolean active) {
        return switch (target) {
            case BUTTON -> HINT_PRESS;
            case LEVER -> HINT_LEVER;
            default -> active ? HINT_CLOSE : HINT_OPEN;
        };
    }

    /** Open for doors, trapdoors and gates; powered for buttons and levers. / 门类是否打开；按钮与拉杆是否通电。 */
    public static boolean isActive(BlockState state) {
        if (state.contains(Properties.OPEN)) {
            return state.get(Properties.OPEN);
        }
        return state.contains(Properties.POWERED) && state.get(Properties.POWERED);
    }

    /**
     * Server throttle (not a cooldown): at least {@link #SERVER_THROTTLE_TICKS} between admitted requests. A last use
     * "in the future" (a restarted tick counter) never throttles. / 服务端节流（非冷却）；记录在“未来”时不节流。
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

    /**
     * Server reach: {@link SeekerRules#CAR_USE_REACH} plus {@link #REACH_SLACK}, inclusive and NaN-safe.
     * 服务端触及判定：{@link SeekerRules#CAR_USE_REACH} 加 {@link #REACH_SLACK}，含边界且对 NaN 安全。
     */
    public static boolean withinServerReach(double squaredDistance) {
        double limit = SeekerRules.CAR_USE_REACH + REACH_SLACK;
        return squaredDistance >= 0.0 && squaredDistance <= limit * limit;
    }

    /**
     * Forward cone: true when the horizontal direction from {@code eye} to {@code hit} is within
     * {@link #FORWARD_CONE_DEGREES} of {@code yawDegrees} (vanilla yaw: 0 faces +Z, 90 faces -X), or when the hit is
     * within {@link #FORWARD_CONE_MIN_HORIZONTAL} horizontally (straight above or below the nose). False for any
     * non-finite input. The server checks it against its own car; the owner's client mirrors it for the hint and outline.
     * 前方锥角：从 {@code eye} 到 {@code hit} 的水平方向与 {@code yawDegrees}（原版朝向：0 朝 +Z，90 朝 -X）的夹角不超过
     * {@link #FORWARD_CONE_DEGREES} 时为 true；命中点水平距离小于 {@link #FORWARD_CONE_MIN_HORIZONTAL}（车头正上方或正下方）
     * 时也为 true。任一输入非有限值时为 false。服务端以自己的小车校验；拥有者客户端为提示与描边做同样的判定。
     */
    public static boolean withinForwardCone(@Nullable Vec3d eye, float yawDegrees, @Nullable Vec3d hit) {
        if (!isFinite(eye) || !isFinite(hit) || !Float.isFinite(yawDegrees)) {
            return false;
        }
        double dx = hit.x - eye.x;
        double dz = hit.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < FORWARD_CONE_MIN_HORIZONTAL) {
            return true;
        }
        double yaw = Math.toRadians(yawDegrees);
        return (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / horizontal >= FORWARD_CONE_COS;
    }

    /** End of the server's sight ray: just past the claimed hit, seen from the car's eye. / 服务端视线射线终点。 */
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
     * 同一位置，或同一扇门的上下两半（方块相同、都有上下半属性、下半位置相同）时为 true。
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
     * Client aim fix: a two-block door's outline spans both halves, so a steep ray can hit one half's shape inside the
     * other half's cell, which vanilla's tolerance rejects. The hit is then re-attributed to the half that contains it;
     * null when neither half fits. / 客户端瞄准修正：两格高门的轮廓覆盖上下两半，陡峭射线可能在另一半的格子里命中
     * 这一半的形状，原版容差会拒绝它；此时把命中归到包含命中点的那一半，两半都不符合时返回 null。
     */
    @Nullable
    public static BlockHitResult attributeHit(BlockView world, BlockHitResult hit) {
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
     * Wathe's {@code SmallDoorBlock}/{@code TrainDoorBlock#onUse} outcome for an empty hand, after
     * {@code DoorInteraction.EVENT}: ALLOW toggles, DENY and HANDLED do nothing, PASS runs Wathe's own branches.
     * {@code DoorInteraction.EVENT} 之后，Wathe 小门/列车门 {@code onUse} 在空手时的结果：ALLOW 切换，DENY 与 HANDLED
     * 不做任何事，PASS 执行 Wathe 自身的分支。
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

    /**
     * Empty-hand door result; refusals carry Wathe's own action-bar key and its locked sound.
     * 空手开门的结果；拒绝时附带 Wathe 自己的动作栏键与上锁音效。
     */
    public enum DoorOutcome {
        TOGGLE(null),
        NONE(null),
        REQUIRES_KEY("tip.door.requires_key"),
        JAMMED("tip.door.jammed"),
        LOCKED("tip.door.locked");

        @Nullable
        private final String messageKey;

        DoorOutcome(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** Wathe's tip key, or null when the door toggles or nothing happens. / Wathe 的提示键。 */
        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }
}
