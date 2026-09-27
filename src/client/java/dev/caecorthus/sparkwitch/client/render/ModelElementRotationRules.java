package dev.caecorthus.sparkwitch.client.render;

/**
 * Which rejected model element angles the 1.21.1 baker still renders correctly.
 * 哪些被拒绝的模型元素角度，1.21.1 烘焙器仍能正确渲染。
 *
 * <p>Mirrors the 1.21.6 rule (single axis, any finite angle within 45 degrees) except {@code rescale}: the 1.21.1
 * {@code BakedQuadFactory} only has rescale factors for 22.5 and 45 degrees. Only vanilla's own rejection of the raw
 * JSON angle is rescued, so an angle another mod rejected or rewrote is left alone.
 * 对应 1.21.6 规则（单轴、45 度以内任意有限角度），但不含 {@code rescale}：1.21.1 的
 * {@code BakedQuadFactory} 只有 22.5 与 45 度的缩放系数。只挽救原版对 JSON 原始角度的拒绝，
 * 其他模组拒绝或改写过的角度保持不动。
 */
public final class ModelElementRotationRules {
    private static final float MAX_ANGLE = 45.0F;

    private ModelElementRotationRules() {
    }

    public static boolean canRescue(float angle, boolean rescale) {
        return !rescale && !isVanillaAngle(angle) && Float.isFinite(angle) && Math.abs(angle) <= MAX_ANGLE;
    }

    public static boolean isVanillaAngle(float angle) {
        float magnitude = Math.abs(angle);
        return magnitude == 0.0F || magnitude == 22.5F || magnitude == MAX_ANGLE;
    }

    /** Matches the exact 1.21.1 {@code ModelElement.Deserializer} message for this angle. */
    public static boolean isVanillaRejection(float angle, String message) {
        return ("Invalid rotation " + angle + " found, only -45/-22.5/0/22.5/45 allowed").equals(message);
    }
}
