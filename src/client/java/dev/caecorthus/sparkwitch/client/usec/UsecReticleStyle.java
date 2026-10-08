package dev.caecorthus.sparkwitch.client.usec;

/**
 * USEC reticle styles (plan §4.3, WP6 spec sheet). Both are code-drawn from {@link UsecReticleGeometry}; neither bakes
 * marks into a texture. Switching is one constant: {@link #DEFAULT}, or a client setting later.
 * USEC 分划样式（计划 §4.3，WP6 规格表）。两者都由 {@link UsecReticleGeometry} 用代码绘制，均不把刻度烘焙进贴图。
 * 切换只需改一个常量 {@link #DEFAULT}，或之后做成客户端设置。
 */
public enum UsecReticleStyle {
    /**
     * R2 tactical MRAD tree, first focal plane: open centre with an illuminated dot, 1-mrad hashes, the illuminated AP
     * holdover ladder (100-200, labelled) and PSO-style person-height brackets. Holds are true at every magnification.
     * R2 战术 MRAD 树形，第一焦平面：镂空中心与发光点、每 1 密位刻度、带标注的发光 AP 抬枪梯（100-200）与 PSO 式人形
     * 高度括号。任何倍率下抬枪量都准确。
     */
    TACTICAL,
    /**
     * R1 classic Mil-Dot, second focal plane calibrated at 6x: continuous wire, a dot every mil (extended to 14 below),
     * no labels. / R1 经典密位点，第二焦平面、6 倍校准：连续细线、每密位一个点（下方延伸到 14），无标注。
     */
    MIL_DOT;

    /** Owner pick 2026-10-07: R2 tactical tree (plan §0 "美术"). / 所有者 2026-10-07 选定：R2 战术树形。 */
    public static final UsecReticleStyle DEFAULT = TACTICAL;
}
