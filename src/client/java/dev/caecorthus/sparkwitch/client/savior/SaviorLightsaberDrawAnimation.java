package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Third-person draw animation for the Savior lightsaber. When the main hand changes to the saber, the right arm
 * reaches to the front-left waist over 0.2 s (4 ticks) while the item is hidden, then settles to the down-right-back
 * resting pose over 0.3 s (6 ticks) and stays there. The left arm is left on the vanilla animation for now.
 * 救世主光剑的第三人称拔出动画。主手切换到光剑时，右臂在 0.2 秒（4 刻）内伸向左前方腰部，期间隐藏物品；随后在
 * 0.3 秒（6 刻）内落到下方偏右偏后的持握姿势并保持。左臂暂时保留原版动画。
 */
public final class SaviorLightsaberDrawAnimation {
    /**
     * Model predicate selecting the invisible model during the reach: only for the drawing player's own main-hand stack
     * while a hand draws it ({@link SaviorHandRenderScope}), never for GUI icons, which pass the local player too.
     * 伸手期间选择隐形模型的模型谓词：只针对正在拔剑玩家自己的主手物品堆、且由手部绘制时（{@link SaviorHandRenderScope}），
     * 绝不作用于同样传入本地玩家的 GUI 图标。
     */
    public static final Identifier DRAW_HIDDEN_ID = SparkWitch.id("draw_hidden");
    /** Reach to the waist: 0.2 s = 4 ticks. / 伸向腰部：0.2 秒 = 4 刻。 */
    public static final int REACH_TICKS = 4;
    /** Settle to the resting pose: 0.3 s = 6 ticks. / 落到持握姿势：0.3 秒 = 6 刻。 */
    public static final int SETTLE_TICKS = 6;
    public static final int TOTAL_TICKS = REACH_TICKS + SETTLE_TICKS;
    /** Idle left-arm raise: 20 degrees forward. / 站立时左臂向前抬 20 度。 */
    public static final float LEFT_ARM_IDLE_PITCH = -0.349F;
    /** Ticks used to raise the left arm after the draw finishes. / 拔出完成后左臂抬起的过渡刻数。 */
    private static final int LEFT_ARM_RAISE_TICKS = 4;
    /** Running right-arm pose: hand lower and further out from the body. / 跑步时右臂：手更低、更远离身体。 */
    private static final ArmAngles RUNNING = new ArmAngles(0.300F, 0.365F, 0.455F);
    /** Attack start pose from the Blockbench reference (converted: pitch = -X, yaw = -Y, roll = Z).
     *  攻击起始姿势，取自新的 Blockbench 参考（换算：pitch = -X、yaw = -Y、roll = Z）。 */
    private static final ArmAngles ATTACK = new ArmAngles(-1.944F, -0.227F, 0.600F);
    /** Attack swing phase: 0.25 s = 5 ticks. / 攻击挥砍阶段：0.25 秒 = 5 刻。 */
    public static final float ATTACK_SWING_TICKS = 5.0F;
    /** Hold the end pose for 0.5 s = 10 ticks. / 结束后保持 0.5 秒 = 10 刻。 */
    public static final float ATTACK_HOLD_TICKS = 10.0F;
    /** Blend back to the normal pose over 4 ticks. / 用 4 刻过渡回常态。 */
    public static final float ATTACK_RETURN_TICKS = 4.0F;
    public static final float ATTACK_TOTAL_TICKS = ATTACK_SWING_TICKS + ATTACK_HOLD_TICKS + ATTACK_RETURN_TICKS;
    /** Left arm starts 25 degrees backward and ends 20 degrees forward during the swing. / 挥砍时左臂从后 25 度转到前 20 度。 */
    public static final float LEFT_ARM_ATTACK_BACK_PITCH = 0.436F;
    public static final float LEFT_ARM_ATTACK_FRONT_PITCH = -0.349F;
    /** Ignition takes 19 ticks + 1 tick of frames; the block stance starts 0.3 s (6 ticks) after the blade lights.
     *  点火为 19 刻预热 + 1 刻动画；格挡持握姿势在刀刃亮起 0.3 秒（6 刻）后开始。 */
    public static final float BLOCK_HOLD_DELAY_TICKS = 26.0F;
    /** Sword-block right-arm pose from the 1.20.1 weapon_extended mod (head pitch added separately).
     *  取自 1.20.1 武器拓展模组的剑格挡右臂姿势（头部俯仰单独叠加）。 */
    private static final float BLOCK_HOLD_PITCH = -1.2F;
    private static final float BLOCK_HOLD_YAW = -0.50F;
    private static final float BLOCK_HOLD_ROLL = 0.40F;
    /** Blend into the block stance over 4 ticks. / 用 4 刻过渡到格挡姿势。 */
    private static final float BLOCK_HOLD_BLEND_TICKS = 4.0F;
    /** Left arm backward 20 degrees in the block stance. / 格挡姿势下左臂向后 20 度。 */
    public static final float LEFT_ARM_BACK_PITCH = 0.349F;
    /** Per-tick run-weight step (1.0 over 4 ticks). / 每刻的跑步权重增量（4 刻到 1）。 */
    private static final float RUNNING_RAMP = 0.25F;

    /** One arm pose in model radians. / 一个模型弧度制的臂姿。 */
    public record ArmAngles(float pitch, float yaw, float roll) {
    }

    private static final ArmAngles REST = new ArmAngles(0.0F, 0.0F, 0.0F);
    /** Pose from the Blockbench reference, converted from its Y-up frame (pitch = -X, yaw = Y, roll = -Z).
     *  Blockbench 参考姿势换算到 MC 的 Y 向下模型坐标：pitch = -X、yaw = Y、roll = -Z。 */
    private static final ArmAngles REACH = new ArmAngles(-0.613F, -0.849F, -0.385F);
    /** After the saber appears, bring it down to the new Blockbench reference position (converted: pitch = -X,
     *  yaw = -Y, roll = Z to match the in-game left/right). / 光剑出现后落到新参考姿势（换算：pitch = -X、yaw = -Y、
     *  roll = Z，与游戏内左右一致）。 */
    private static final ArmAngles SETTLED = new ArmAngles(0.274F, 0.433F, 0.455F);

    /**
     * Draw start tick per player UUID. Client-side only: every client sees the same held stack and runs the same
     * animation, so no packet is needed. / 每名玩家的拔出开始刻。纯客户端：每个客户端看到相同的主手物品并跑同一动画，
     * 因此不需要数据包。
     */
    private static final Map<UUID, Long> START_TICKS = new HashMap<>();
    /** Run-pose weight per player (0 = hold, 1 = sprinting). / 每名玩家的跑步姿势权重（0 = 持握，1 = 跑步）。 */
    private static final Map<UUID, Float> RUNNING_AMOUNTS = new HashMap<>();
    /** Attack start tick per player. / 每名玩家的攻击开始刻。 */
    private static final Map<UUID, Long> ATTACK_START = new HashMap<>();
    /** Previous hand-swinging state, for rising-edge attack detection. / 上一刻是否在挥剑，用于检测攻击起始。 */
    private static final Map<UUID, Boolean> WAS_SWINGING = new HashMap<>();

    private SaviorLightsaberDrawAnimation() {
    }

    public static void register() {
        FabricModelPredicateProviderRegistry.register(DRAW_HIDDEN_ID, (stack, world, entity, seed) -> {
            if (world == null || !(entity instanceof PlayerEntity player)
                    || !stack.isOf(SparkWitchItems.saviorLightsaber())
                    || player.getMainHandStack() != stack
                    || !SaviorHandRenderScope.isRendering(stack)) {
                return 0.0F;
            }
            return elapsedTicks(player) < REACH_TICKS ? 1.0F : 0.0F;
        });
        ClientTickEvents.END_CLIENT_TICK.register(SaviorLightsaberDrawAnimation::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            reset();
            return;
        }
        Set<UUID> holding = new HashSet<>();
        long now = client.world.getTime();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.getMainHandStack().isOf(SparkWitchItems.saviorLightsaber())) {
                UUID id = player.getUuid();
                holding.add(id);
                START_TICKS.putIfAbsent(id, now);
                boolean swinging = player.handSwinging;
                if (swinging && !WAS_SWINGING.getOrDefault(id, false)) {
                    ATTACK_START.put(id, now);
                }
                WAS_SWINGING.put(id, swinging);
                float target = player.isSprinting() ? 1.0F : 0.0F;
                float current = RUNNING_AMOUNTS.getOrDefault(id, 0.0F);
                float next = current < target
                        ? Math.min(target, current + RUNNING_RAMP)
                        : Math.max(target, current - RUNNING_RAMP);
                RUNNING_AMOUNTS.put(id, next);
            }
        }
        START_TICKS.keySet().retainAll(holding);
        RUNNING_AMOUNTS.keySet().retainAll(holding);
        ATTACK_START.keySet().retainAll(holding);
        WAS_SWINGING.keySet().retainAll(holding);
    }

    /**
     * Connection edges ({@code SparkWitchClient.releaseClosedWorldPlayers}, client thread): drops every per-player
     * animation state. / 连接边界（{@code SparkWitchClient.releaseClosedWorldPlayers}，客户端线程）：丢弃所有按玩家记录的
     * 动画状态。
     */
    public static void reset() {
        START_TICKS.clear();
        RUNNING_AMOUNTS.clear();
        ATTACK_START.clear();
        WAS_SWINGING.clear();
    }

    /** Whether this player is in the draw/rest animation (main hand holds the saber). / 该玩家是否处于拔出/持握动画中。 */
    public static boolean isHolding(PlayerEntity player) {
        return START_TICKS.containsKey(player.getUuid());
    }

    /** Elapsed ticks since the draw started, including the render partial tick. / 拔出开始以来的刻数，含渲染偏刻。 */
    public static float elapsedTicks(PlayerEntity player) {
        Long start = START_TICKS.get(player.getUuid());
        if (start == null) {
            return TOTAL_TICKS;
        }
        float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        return Math.max(0.0F, player.getWorld().getTime() - start + partial);
    }

    /** Current right-arm pose; holds the settled pose after 0.5 s, blended into the running pose while sprinting.
     *  当前右臂姿势；0.5 秒后保持持握姿势，跑步时过渡到跑步姿势。 */
    public static ArmAngles armAngles(PlayerEntity player, float headPitch) {
        float elapsed = elapsedTicks(player);
        ArmAngles base;
        if (elapsed < REACH_TICKS) {
            base = lerp(REST, REACH, smooth(elapsed / REACH_TICKS));
        } else {
            float k = Math.min(1.0F, (elapsed - REACH_TICKS) / SETTLE_TICKS);
            base = lerp(REACH, SETTLED, smooth(k));
            if (elapsed >= BLOCK_HOLD_DELAY_TICKS) {
                float b = Math.min(1.0F, (elapsed - BLOCK_HOLD_DELAY_TICKS) / BLOCK_HOLD_BLEND_TICKS);
                ArmAngles block = new ArmAngles(
                        BLOCK_HOLD_PITCH + headPitch, BLOCK_HOLD_YAW, BLOCK_HOLD_ROLL);
                base = lerp(base, block, smooth(b));
            }
        }
        float run = smooth(runningAmount(player));
        if (run > 0.0F) {
            base = lerp(base, RUNNING, run);
        }
        float weight = attackWeight(player);
        if (weight <= 0.0F) {
            return base;
        }
        ArmAngles attack = lerp(ATTACK, RUNNING, attackSwingBlend(player));
        return lerp(base, attack, weight);
    }

    /** Attack elapsed ticks since the swing started, including the render partial tick. / 攻击开始以来的刻数，含渲染偏刻。 */
    private static float attackElapsed(PlayerEntity player) {
        Long start = ATTACK_START.get(player.getUuid());
        if (start == null) {
            return Float.MAX_VALUE;
        }
        float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        return Math.max(0.0F, player.getWorld().getTime() - start + partial);
    }

    /** Swing progress 0 (attack pose + normal wrist angle) to 1 (running pose + running wrist angle).
     *  挥砍过渡：0 = 攻击姿势 + 常态剑角，1 = 跑步姿势 + 跑步剑角。 */
    public static float attackSwingBlend(PlayerEntity player) {
        return smooth(Math.min(1.0F, attackElapsed(player) / ATTACK_SWING_TICKS));
    }

    /** Attack weight: 1 through swing and hold, fades to 0 over the return. / 攻击权重：挥砍与保持期间为 1，回位时渐出。 */
    public static float attackWeight(PlayerEntity player) {
        float elapsed = attackElapsed(player);
        if (elapsed >= ATTACK_TOTAL_TICKS) {
            return 0.0F;
        }
        if (elapsed < ATTACK_SWING_TICKS + ATTACK_HOLD_TICKS) {
            return 1.0F;
        }
        return smooth(1.0F - (elapsed - ATTACK_SWING_TICKS - ATTACK_HOLD_TICKS) / ATTACK_RETURN_TICKS);
    }

    /** Whether the post-ignition block stance is active (blade lit + 0.3 s). / 点火后的格挡持握是否已生效。 */
    public static boolean blockHoldActive(PlayerEntity player) {
        return isHolding(player) && elapsedTicks(player) >= BLOCK_HOLD_DELAY_TICKS;
    }

    /** Current run-pose weight, 0 (holding) to 1 (sprinting). / 当前跑步姿势权重，0（持握）到 1（跑步）。 */
    public static float runningAmount(PlayerEntity player) {
        return RUNNING_AMOUNTS.getOrDefault(player.getUuid(), 0.0F);
    }

    /** Whether the left arm should be raised: saber drawn, draw animation finished, standing still.
     *  左臂是否该抬起：光剑已拔出、拔出动画结束、站立不动。 */
    public static boolean shouldRaiseLeftArm(PlayerEntity player) {
        return isHolding(player)
                && elapsedTicks(player) >= TOTAL_TICKS
                && player.isOnGround()
                && player.getVelocity().horizontalLengthSquared() < 1.0E-4;
    }

    /** Smoothed run weight used by the render hooks. / 渲染钩子用的平滑跑步权重。 */
    public static float runningBlend(PlayerEntity player) {
        return smooth(runningAmount(player));
    }

    /** Left-arm pitch for the idle stance, ramped in over 0.2 s. / 站立时左臂俯仰角，0.2 秒内渐入。 */
    public static float leftArmPitch(PlayerEntity player) {
        float k = Math.min(1.0F, (elapsedTicks(player) - TOTAL_TICKS) / LEFT_ARM_RAISE_TICKS);
        return LEFT_ARM_IDLE_PITCH * smooth(k);
    }

    private static float smooth(float k) {
        float c = Math.max(0.0F, Math.min(1.0F, k));
        return c * c * (3.0F - 2.0F * c);
    }

    private static ArmAngles lerp(ArmAngles from, ArmAngles to, float k) {
        return new ArmAngles(
                from.pitch() + (to.pitch() - from.pitch()) * k,
                from.yaw() + (to.yaw() - from.yaw()) * k,
                from.roll() + (to.roll() - from.roll()) * k
        );
    }
}
