package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;

/**
 * The auto-fit (owner 2026-11-02: no user scale). The target is the {@link #envelope} of the vanilla model: a cube on
 * the vanilla box's centre whose side is that box's largest side, so a skin's longest side matches the vanilla
 * model's longest side whatever its proportions (a winged figure is not shrunk by its depth alone). A skin's bind pose
 * is scaled uniformly to fit inside it and centred on it; each frame the posed model may grow to {@link #ALLOWANCE}
 * times that cube around its centre, and is shrunk about the centre when an animation would push it further, so no
 * skin ever reaches past the vanilla Mini Wither's or skull's largest extent (no visibility advantage). All boxes are
 * in the same frame: the caller's, right before the model is drawn.
 * 自动缩放（所有者 2026-11-02：不提供手动缩放）。目标是原版模型的 {@link #envelope}：以原版包围盒中心为中心、边长等于其最长
 * 边的立方体，因此无论比例如何，皮肤的最长边都与原版模型的最长边一致（带翅膀的造型不会只因厚度被缩小）。皮肤的绑定姿态按统一
 * 比例缩放到其中并居中对齐；每帧摆姿后的模型最多可以达到该立方体的 {@link #ALLOWANCE} 倍（以中心为准），动画若超出就绕中心
 * 缩小，因此任何皮肤都不会超出原版小凋灵或头颅的最大尺寸（不会带来视野优势）。所有包围盒处于同一坐标系：调用方绘制模型前的
 * 坐标系。
 */
public final class MiniWitherSkinFit {
    /** How far an animated pose may reach past the fitted box. / 动画姿态可超出适配包围盒的倍数。 */
    public static final float ALLOWANCE = 1.25F;
    private static final float MIN_SIZE = 1.0E-4F;

    private MiniWitherSkinFit() {
    }

    /**
     * The cube on {@code vanilla}'s centre with its largest side. / 以 {@code vanilla} 中心为中心、边长为其最长边的立方体。
     */
    public static BedrockBounds envelope(BedrockBounds vanilla) {
        float half = Math.max(vanilla.sizeX(), Math.max(vanilla.sizeY(), vanilla.sizeZ())) * 0.5F;
        return new BedrockBounds(vanilla.centerX() - half, vanilla.centerY() - half, vanilla.centerZ() - half,
                vanilla.centerX() + half, vanilla.centerY() + half, vanilla.centerZ() + half);
    }

    /**
     * Uniform scale and offset putting {@code rest} inside {@code target}, centre on centre. Axes where either box is
     * flat do not limit the scale; a skin flat on every axis keeps scale 1.
     * 把 {@code rest} 放进 {@code target} 并使中心重合的统一缩放与偏移。任一包围盒在某轴上为扁平时该轴不限制缩放；所有轴都扁平的
     * 皮肤保持缩放 1。
     */
    public static Transform fit(BedrockBounds target, BedrockBounds rest) {
        float scale = Float.POSITIVE_INFINITY;
        scale = limit(scale, target.sizeX(), rest.sizeX());
        scale = limit(scale, target.sizeY(), rest.sizeY());
        scale = limit(scale, target.sizeZ(), rest.sizeZ());
        if (!Float.isFinite(scale)) {
            scale = 1.0F;
        }
        return new Transform(scale,
                target.centerX() - scale * rest.centerX(),
                target.centerY() - scale * rest.centerY(),
                target.centerZ() - scale * rest.centerZ());
    }

    /**
     * {@code base} shrunk about the target centre just enough that {@code posed} (in the model's own frame) stays
     * within {@link #ALLOWANCE} times the target's half extents; {@code base} itself when it already does.
     * 把 {@code base} 绕目标中心缩小到恰好让 {@code posed}（模型自身坐标系）保持在目标半尺寸的 {@link #ALLOWANCE} 倍之内；
     * 已经满足时直接返回 {@code base}。
     */
    public static Transform cap(Transform base, BedrockBounds target, BedrockBounds posed) {
        float shrink = 1.0F;
        shrink = shrinkFor(shrink, target.centerX(), target.sizeX(), base.apply(posed.minX(), base.x),
                base.apply(posed.maxX(), base.x));
        shrink = shrinkFor(shrink, target.centerY(), target.sizeY(), base.apply(posed.minY(), base.y),
                base.apply(posed.maxY(), base.y));
        shrink = shrinkFor(shrink, target.centerZ(), target.sizeZ(), base.apply(posed.minZ(), base.z),
                base.apply(posed.maxZ(), base.z));
        if (shrink >= 1.0F) {
            return base;
        }
        return new Transform(base.scale * shrink,
                target.centerX() + shrink * (base.x - target.centerX()),
                target.centerY() + shrink * (base.y - target.centerY()),
                target.centerZ() + shrink * (base.z - target.centerZ()));
    }

    private static float limit(float scale, float targetSize, float restSize) {
        if (targetSize < MIN_SIZE || restSize < MIN_SIZE) {
            return scale;
        }
        return Math.min(scale, targetSize / restSize);
    }

    private static float shrinkFor(float shrink, float center, float targetSize, float min, float max) {
        float allowed = Math.max(targetSize, MIN_SIZE) * 0.5F * ALLOWANCE;
        float reach = Math.max(Math.abs(min - center), Math.abs(max - center));
        if (!Float.isFinite(reach)) {
            return 0.0F;
        }
        return reach > allowed ? Math.min(shrink, allowed / reach) : shrink;
    }

    /**
     * Draw as {@code translate(x, y, z)} then {@code scale(scale)}. / 绘制时先 {@code translate(x, y, z)} 再
     * {@code scale(scale)}。
     */
    public record Transform(float scale, float x, float y, float z) {
        float apply(float value, float offset) {
            return offset + scale * value;
        }
    }
}
