package dev.caecorthus.sparkwitch.client.renderer;

import org.joml.Vector3f;

/**
 * Pure chain geometry for {@link NinjaGrapplingHookEntityRenderer}: link count, the FLYING sag curve, and the two
 * cross-section axes. The axes never degenerate, so a chain hanging straight up or down keeps its width (vanilla's
 * leash math divides by the horizontal length and collapses there).
 * {@link NinjaGrapplingHookEntityRenderer} 的纯链条几何：链节数、飞行中的下垂曲线与两条截面轴。截面轴永不退化，
 * 因此垂直上下的链条仍保持宽度（原版拴绳算法除以水平长度，在垂直时会塌成一条线）。
 */
final class NinjaGrapplingChainGeometry {
    /** One drawn link, in blocks. / 每个链节的长度（方块）。 */
    static final float LINK_LENGTH = 0.15F;
    static final int MIN_LINKS = 2;
    /** 32 blocks (the break distance) / 0.15 ≈ 214 links, so the cap only guards odd frames. / 32 格断链距离约 214 节，上限只防异常帧。 */
    static final int MAX_LINKS = 256;
    static final float SLACK_SAG_PER_BLOCK = 0.03F;
    static final float MAX_SLACK_SAG = 0.5F;

    private static final float VERTICAL_EPSILON = 1.0E-4F;

    private NinjaGrapplingChainGeometry() {
    }

    static int linkCount(float length) {
        if (!(length > 0.0F)) {
            return MIN_LINKS;
        }
        int links = (int) Math.ceil(length / LINK_LENGTH);
        return Math.max(MIN_LINKS, Math.min(MAX_LINKS, links));
    }

    /** Deepest sag of a slack (FLYING) chain; a taut chain has none. / 松弛（飞行中）链条的最大下垂量；绷紧时为 0。 */
    static float sagDepth(float length, boolean slack) {
        if (!slack || !(length > 0.0F)) {
            return 0.0F;
        }
        return Math.min(length * SLACK_SAG_PER_BLOCK, MAX_SLACK_SAG);
    }

    /** Vertical offset at {@code t} in [0, 1]: a parabola that is 0 at both ends. / {@code t} 处的竖直偏移，两端为 0 的抛物线。 */
    static float sagOffset(float t, float depth) {
        return -depth * 4.0F * t * (1.0F - t);
    }

    /**
     * First cross-section axis: horizontal and perpendicular to the chain, or +X for a vertical chain.
     * 第一条截面轴：水平且垂直于链条；链条垂直时取 +X。
     */
    static Vector3f sideAxis(float dx, float dy, float dz, float halfWidth) {
        // chain × up = (-dz, 0, dx) / 链条 × 上方向 = (-dz, 0, dx)
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        if (horizontal < VERTICAL_EPSILON) {
            return new Vector3f(halfWidth, 0.0F, 0.0F);
        }
        return new Vector3f(-dz / horizontal * halfWidth, 0.0F, dx / horizontal * halfWidth);
    }

    /**
     * Second cross-section axis: perpendicular to both the chain and {@code side}.
     * 第二条截面轴：同时垂直于链条与 {@code side}。
     */
    static Vector3f crossAxis(float dx, float dy, float dz, Vector3f side, float halfWidth) {
        Vector3f axis = new Vector3f(dx, dy, dz).cross(side);
        float length = axis.length();
        if (length < VERTICAL_EPSILON) {
            return new Vector3f(0.0F, halfWidth, 0.0F);
        }
        return axis.mul(halfWidth / length);
    }
}
