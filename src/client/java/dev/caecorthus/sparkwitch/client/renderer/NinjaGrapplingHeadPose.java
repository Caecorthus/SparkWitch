package dev.caecorthus.sparkwitch.client.renderer;

import net.minecraft.util.math.Vec3d;

/**
 * Pure pose math for the 3D hook head drawn by {@link NinjaGrapplingHookEntityRenderer}: which way the claws point and
 * where on the model the chain is tied. The model's pivot is the crown tip, so the head is drawn at the hook's exact
 * position (the block hit point once LATCHED) and everything else lies behind it.
 * {@link NinjaGrapplingHookEntityRenderer} 所绘 3D 钩头的纯姿态数学：爪尖朝向哪里、链条系在模型何处。模型枢轴是钩冠
 * 尖端，因此钩头画在钩爪的精确位置（钩住后即方块命中点），其余部分都在它后方。
 */
final class NinjaGrapplingHeadPose {
    /**
     * The chain ties into the middle of the ring's back bar, this far behind the crown tip: the {@code ring_back}
     * element of {@code models/item/projectile/ninja_grappling_hook.json} spans 9.75..10.5 model pixels behind the
     * pivot, so 10.125 / 16 blocks. Re-check it whenever the model changes.
     * 链条系在圆环后横杆的中点，位于钩冠尖端后方这么远：模型 {@code ring_back} 元素位于枢轴后 9.75..10.5 像素，
     * 即 10.125 / 16 格。修改模型时需复核。
     */
    static final double RING_DISTANCE = 10.125D / 16.0D;
    /**
     * A hand-to-hook vector shorter than this (a hook still at the thrower's hand) cannot aim the head.
     * 手到钩爪的向量短于此值（钩爪仍在投掷者手边）时无法用于钩头朝向。
     */
    static final double MIN_AIM_LENGTH = 0.05D;

    private static final double MIN_FALLBACK_LENGTH_SQ = 1.0E-8D;
    private static final Vec3d NORTH = new Vec3d(0.0D, 0.0D, -1.0D);

    private NinjaGrapplingHeadPose() {
    }

    /**
     * Unit direction of the claws. A FLYING head points along its velocity, claws first; other players see the hand
     * ahead of the eyes the hook spawns at, so the chain would point it backwards on its first frame. A LATCHED or
     * PULLING head points away from the thrower along the chain ({@code hook - hand}), into the surface, and pivots
     * as the owner moves. Each falls back to the other, then to the owner's look, then north.
     * 爪尖的单位方向。飞行中的钩头沿速度方向、爪尖在前；其他玩家看到的手在钩爪生成的眼部前方，按链条朝向会在第一帧
     * 指向后方。钩住或拉拽时沿链条背离投掷者（{@code 钩爪 - 手}），指向表面并随主人移动而转动。两者互为后备，再退回
     * 主人视线，最后取北方。
     */
    static Vec3d facing(boolean flying, Vec3d handToHook, Vec3d velocity, Vec3d ownerLook) {
        boolean velocityUsable = velocity.lengthSquared() > MIN_FALLBACK_LENGTH_SQ;
        if (flying && velocityUsable) {
            return velocity.normalize();
        }
        if (handToHook.lengthSquared() >= MIN_AIM_LENGTH * MIN_AIM_LENGTH) {
            return handToHook.normalize();
        }
        if (velocityUsable) {
            return velocity.normalize();
        }
        if (ownerLook.lengthSquared() > MIN_FALLBACK_LENGTH_SQ) {
            return ownerLook.normalize();
        }
        return NORTH;
    }

    /**
     * Where the chain starts relative to the hook position, for a unit {@code facing}.
     * 给定单位朝向 {@code facing} 时，链条起点相对钩爪位置的偏移。
     */
    static Vec3d ringOffset(Vec3d facing) {
        return facing.multiply(-RING_DISTANCE);
    }
}
