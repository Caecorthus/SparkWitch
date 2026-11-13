package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeHeatMask;
import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeHeatMath;

/**
 * Stable cross-mod contract (client only, render thread, BEAR WP0 2026-11-05, heat entries WP5): read access to the
 * BEAR smoke this client currently draws, so another mod's view (today SparkStrength's Undercover thermal, S4) can treat
 * smoke as opaque. SparkStrength reaches it by reflection, so this FQCN and the JDK-only signatures must not change.
 * Purely visual: the server never reads smoke.
 * 稳定跨模组契约（仅客户端、渲染线程，BEAR WP0 2026-11-05，热成像入口 WP5）：读取本客户端当前绘制的 BEAR 烟雾，使其他模组
 * 的视图（目前为 SparkStrength 卧底热成像，S4）能把烟视为不透明。SparkStrength 通过反射访问本类，因此完整类名与仅使用 JDK
 * 类型的签名都不得更改。纯视觉：服务端从不读取烟雾。
 *
 * <p>Heat image (S4 "hot smoke blocks thermal", owner Q6: smoke shows as a bright white blob and hides bodies behind it).
 * Call order inside one {@code WorldRenderer#render} pass (SparkStrength's {@code captureHeat} at
 * {@code BEFORE_DEBUG_RENDER}; any framebuffer may be bound, every GL state is restored):
 * <ol>
 *   <li>clear the heat target, copy the world depth into it, draw the bodies (writing their depth keeps the mask
 *   exact; without it the world depth behind them is used, which hides a little more, never less);</li>
 *   <li>{@link #maskHeatTarget}: heat behind smoke fades exactly where an instinct outline would (optical depth 0.3 to
 *   1.5 along each pixel's view ray up to its depth);</li>
 *   <li>{@link #drawHeatHaze} with {@link #DEFAULT_HEAT_HAZE}: smoke in front of the depth glows white-hot, never
 *   through a wall;</li>
 *   <li>the first-person arm and the bloom composite as before.</li>
 * </ol>
 * Fail closed: when {@link #maskHeatTarget} returns false the heat was not masked; SparkWitch has already tried to clear
 * it inside the smoke rectangle, and the caller must still hide its heat inside {@link #heatSmokeRect} (the whole
 * target when that returns the whole target). A false {@link #drawHeatHaze} needs no action (the haze is cosmetic).
 * Self-test: a client-side driver may call both on its own framebuffer during a world render, read the pixels back and
 * compare them with {@code heat * v} (mask) and {@code max(heat, haze * (1 - v))} (haze), v being the outline
 * visibility (1 up to optical depth 0.3, 0 from 1.5, linear between) of {@link #opticalDepth} along the pixel ray.
 * 热图（S4“热烟挡住热成像”，所有者 Q6：烟显示为一团亮白并遮住其后的人体）。在同一次 {@code WorldRenderer#render} 中的调用顺序
 * （SparkStrength 在 {@code BEFORE_DEBUG_RENDER} 的 {@code captureHeat}；可绑定任意帧缓冲，所有 GL 状态都会恢复）：
 * 1. 清空热图、拷入世界深度、画人体（写入人体深度可使遮罩精确；不写则使用其后的世界深度，只会多遮、不会少遮）；
 * 2. {@link #maskHeatTarget}：烟后的热值恰好在本能描边会变淡处变淡（沿每个像素视线到其深度的光学厚度 0.3 到 1.5）；
 * 3. 以 {@link #DEFAULT_HEAT_HAZE} 调用 {@link #drawHeatHaze}：深度之前的烟呈白热，绝不透墙；4. 第一人称手臂与柔光合成照旧。
 * 失败即遮挡：{@link #maskHeatTarget} 返回 false 表示热图未被遮罩；SparkWitch 已尝试清掉烟雾矩形内的热值，调用方仍须把
 * {@link #heatSmokeRect} 内的热值藏起来（返回整个目标时即全部）。{@link #drawHeatHaze} 返回 false 无需处理（热雾只是外观）。
 * 自测：客户端驱动可在世界渲染中对自己的帧缓冲调用二者，回读像素，并与 {@code heat * v}（遮罩）及
 * {@code max(heat, haze * (1 - v))}（热雾）比较，v 为像素视线上 {@link #opticalDepth} 的描边可见度（光学厚度 0.3 以内为 1，
 * 1.5 起为 0，其间线性）。</p>
 */
public final class BearSmokeViewApi {
    /**
     * Owner Q6 haze heat (0..1, a body is 1; 1 = bright white) for {@link #drawHeatHaze}. / {@link #drawHeatHaze} 的
     * 所有者 Q6 热雾热值（0..1，人体为 1；1 即亮白）。
     */
    public static final float DEFAULT_HEAT_HAZE = SmokeHeatMath.DEFAULT_HAZE;

    private BearSmokeViewApi() {
    }

    /**
     * Optical depth of the smoke on the segment between two world points, from the densities prepared for the last
     * rendered frame (bullet tunnels included); 0 when there is no smoke (transmittance is {@code exp(-depth)}).
     * 两个世界坐标之间线段上的烟雾光学厚度，取最近一次渲染帧准备的浓度（含子弹通道）；没有烟时为 0（透射率为
     * {@code exp(-depth)}）。
     */
    public static float opticalDepth(double x0, double y0, double z0, double x1, double y1, double z1) {
        return SmokeVolumes.opticalDepth(x0, y0, z0, x1, y1, z1);
    }

    /** True while this client knows at least one smoke cloud. / 本客户端已知至少一团烟时为 true。 */
    public static boolean hasSmoke() {
        return !SmokeClientState.isEmpty();
    }

    /**
     * Fades the heat behind BEAR smoke in a heat target, pixel by pixel (see the class comment for the call order).
     * {@code colorTexture} is the heat target's colour texture (every channel is scaled), {@code depthTexture} its depth
     * texture (sampled, never written, compare mode off as vanilla sets it), {@code width}/{@code height} their size;
     * the whole texture must show exactly the current world render's view. Returns true when the target is right
     * (masked, or no smoke touches this pass); false for bad arguments, a call off the render thread or outside a world
     * render, or a GL failure: the caller then hides its heat inside {@link #heatSmokeRect}.
     * 逐像素淡化热图中 BEAR 烟雾之后的热值（调用顺序见类注释）。{@code colorTexture} 为热图颜色纹理（每个通道都被缩放），
     * {@code depthTexture} 为其深度纹理（只采样，不写入，比较模式关闭，与原版设置相同），{@code width}/{@code height} 为其尺寸；
     * 整张纹理必须恰好对应当前世界渲染的视野。热图正确（已遮罩，或烟不触及本次
     * 渲染）时返回 true；参数错误、不在渲染线程或世界渲染之中、或 GL 失败时返回 false：调用方随即把 {@link #heatSmokeRect} 内的
     * 热值藏起来。
     */
    public static boolean maskHeatTarget(int colorTexture, int depthTexture, int width, int height) {
        return SmokeHeatMask.apply(colorTexture, depthTexture, width, height, -1.0F);
    }

    /**
     * Draws BEAR smoke as a warm haze into a heat target (after {@link #maskHeatTarget}, same arguments):
     * {@code heat} in (0, 1] is what fully opaque smoke writes, blended with MAX and limited by the target's depth so it
     * never shows through a wall. Returns false (no action needed) for bad arguments or a failure.
     * 把 BEAR 烟雾作为暖色热雾画进热图（在 {@link #maskHeatTarget} 之后，参数相同）：{@code heat}（属于 (0, 1]）为完全不透明的烟
     * 写入的热值，以 MAX 混合，并受热图深度限制，绝不透墙显示。参数错误或失败时返回 false（无需处理）。
     */
    public static boolean drawHeatHaze(int colorTexture, int depthTexture, int width, int height, float heat) {
        return SmokeHeatMath.validHaze(heat) && SmokeHeatMask.apply(colorTexture, depthTexture, width, height, heat);
    }

    /**
     * Rectangle {@code {x0, y0, x1, y1}} (exclusive ends, GL origin bottom-left) of a {@code width} x {@code height}
     * heat target that smoke can touch in the current pass; the whole target when that is unknown; null when no smoke
     * can touch it. The area to hide when {@link #maskHeatTarget} fails.
     * 当前渲染中烟可能触及的 {@code width} x {@code height} 热图矩形 {@code {x0, y0, x1, y1}}（不含终点，GL 原点在左下）；无法得知时
     * 为整个目标；烟不可能触及时为 null。即 {@link #maskHeatTarget} 失败时需要隐藏的区域。
     */
    public static int[] heatSmokeRect(int width, int height) {
        return SmokeHeatMask.smokeRect(width, height);
    }
}
