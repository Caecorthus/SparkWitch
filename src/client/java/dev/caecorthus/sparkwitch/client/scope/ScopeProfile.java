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
     * Multiplier on the local player's FOV multiplier while scoped: 1 = no zoom, smaller zooms in (USEC's variable
     * zoom uses 1 / magnification through {@link ScopeVariableZoom#fovMultiplier}, so 0.25 = 4x; the launcher derives
     * its true 2.7x from the FOV option). Polled every frame, so a profile may return a value that changes smoothly.
     * Vanilla eases the change and clamps the eased multiplier at 0.1, so 10x is the deepest zoom. Non-finite or
     * non-positive values mean no zoom.
     * 开镜时本地玩家 FOV 倍率的乘数：1 = 不放大，越小放得越大（USEC 的可变倍率经 {@link ScopeVariableZoom#fovMultiplier}
     * 使用 1 / 倍率，因此 0.25 = 4 倍；炮筒按视场角选项换算出真实 2.7 倍）。每帧轮询，因此配置可以返回平滑变化的值。原版会
     * 缓动该变化，并把缓动后的倍率钳制在 0.1，因此最深为 10 倍。非有限值或非正值视为不放大。
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

    /**
     * The mouse wheel turned while this profile is active ({@code ScopeMouseScrollMixin}: in game, no screen or
     * overlay, after every HEAD lock on {@code Mouse#onMouseScroll} has passed). {@code notches} is the vertical delta
     * exactly as vanilla would accumulate it (discrete scrolling and wheel sensitivity applied; fractional on trackpads
     * and high-resolution wheels; positive = away from the player). Return true to consume the whole wheel event, so
     * vanilla neither accumulates it nor changes the hotbar slot; false (the default) leaves the wheel to vanilla.
     * 本配置生效时鼠标滚轮转动（{@code ScopeMouseScrollMixin}：游戏内、无界面或遮罩，且 {@code Mouse#onMouseScroll} 上所有
     * HEAD 锁都已放行之后）。{@code notches} 为原版累加时使用的竖直增量（已应用离散滚动与滚轮灵敏度；触控板与高精度滚轮为
     * 小数；正数为向前推）。返回 true 表示消耗整个滚轮事件，原版既不累加也不切换快捷栏；返回 false（默认）交给原版。
     */
    default boolean onWheel(double notches) {
        return false;
    }
}
