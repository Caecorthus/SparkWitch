package dev.caecorthus.sparkwitch.client.scope;

/**
 * One frame of scope geometry handed to {@link ScopeProfile#drawReticle}. Positions and the radius are scaled GUI
 * pixels; the lens centre is the exact framebuffer centre (fractional at odd sizes), the same point the lens shader
 * uses.
 * 交给 {@link ScopeProfile#drawReticle} 的一帧开镜几何。位置与半径均为缩放后的 GUI 像素；镜片中心是帧缓冲的精确中心
 * （奇数尺寸时为小数），与镜片着色器使用的点相同。
 *
 * @param scaledWidth          scaled GUI width / 缩放后的 GUI 宽度
 * @param scaledHeight         scaled GUI height / 缩放后的 GUI 高度
 * @param lensCenterX          lens centre x / 镜片中心 x
 * @param lensCenterY          lens centre y / 镜片中心 y
 * @param lensRadius           lens radius, scaled GUI px / 镜片半径（缩放后的 GUI 像素）
 * @param projectionFovDegrees vertical FOV the world was projected with this frame (options FOV x the eased zoom, plus
 *                             any other {@code getFov} change), so screen offsets of angles match the picture /
 *                             本帧世界投影实际使用的竖直视场角（选项 FOV x 缓动后的放大倍率，再加其他 {@code getFov} 变化），
 *                             使角度换算的屏幕偏移与画面一致
 * @param tickDelta            render tick delta / 渲染 tick 插值
 * @param shadowX              scope-shadow (exit-pupil) offset in lens radii, GUI axes: +x right, pointing the way the
 *                             view swings; the dark crescent enters from the opposite edge; 0 when still /
 *                             镜内阴影（出瞳）偏移，单位为镜片半径，GUI 坐标轴：+x 向右，指向视角摆动方向；暗色月牙从
 *                             相反一侧边缘进入；静止时为 0
 * @param shadowY              as {@code shadowX}, +y down / 同 {@code shadowX}，+y 向下
 * @param mode                 the mode that actually renders this frame ({@link ScopeClient#effectiveMode()}) /
 *                             本帧实际渲染的模式
 */
public record ScopeFrame(int scaledWidth, int scaledHeight, float lensCenterX, float lensCenterY, float lensRadius,
                         double projectionFovDegrees, float tickDelta, float shadowX, float shadowY, ScopeMode mode) {
}
