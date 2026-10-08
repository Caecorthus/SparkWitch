package dev.caecorthus.sparkwitch.client.scope;

import net.minecraft.client.gui.DrawContext;

/**
 * Weapon-owned scope parameters, polled every frame while a provider returns this profile (the USEC rifle and the
 * Potion Gunner launcher). The scope module owns zoom easing, mouse scaling, the lens picture and the rim; the weapon
 * owns only these numbers and its reticle. Client-only presentation: the server never sees any of it.
 * 武器自有的瞄准镜参数，在提供者返回该配置期间每帧轮询（USEC 狙击枪与药炮手炮筒）。开镜模块负责放大缓动、
 * 鼠标缩放、镜片画面与镜框；武器只提供这些数值与自己的分划。纯客户端展示，服务端完全不可见。
 */
public interface ScopeProfile {
    /**
     * Multiplier on the local player's FOV multiplier while scoped: 0.25 = 4x, 0.125 = 8x. Vanilla eases the change and
     * clamps the eased multiplier at 0.1, so 10x is the deepest zoom. Non-finite or non-positive values mean no zoom.
     * 开镜时本地玩家 FOV 倍率的乘数：0.25 = 4 倍，0.125 = 8 倍。原版会缓动该变化，并把缓动后的倍率钳制在 0.1，
     * 因此最深为 10 倍。非有限值或非正值视为不放大。
     */
    float fovMultiplier();

    /**
     * Base mouse-look multiplier while scoped, before the player's "Scoped Sensitivity" percentage is applied.
     * 开镜时的基础鼠标视角倍率，再乘以玩家「开镜灵敏度」百分比。
     */
    float sensitivityMultiplier();

    /**
     * Draws the reticle on the HUD crosshair layer, after the scope module has drawn the lens rim; called only while
     * scoped and the HUD is shown. The matrix stack is untouched (identity); {@code frame} gives the lens geometry in
     * scaled GUI pixels and the real projection FOV.
     * 在 HUD 准星层绘制分划，开镜模块已先画好镜框；仅在开镜且 HUD 显示时调用。矩阵栈未改动（单位矩阵）；
     * {@code frame} 以缩放后的 GUI 像素给出镜片几何与真实投影视场角。
     */
    void drawReticle(DrawContext context, ScopeFrame frame);
}
