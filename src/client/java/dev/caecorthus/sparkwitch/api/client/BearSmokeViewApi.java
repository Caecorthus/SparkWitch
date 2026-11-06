package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;

/**
 * Stable cross-mod contract (client only, render thread, BEAR WP0 2026-11-05): read access to the BEAR smoke this
 * client currently draws, so another mod's view (today SparkStrength's Undercover thermal, S4) can treat smoke as
 * opaque. SparkStrength reaches it by reflection, so this FQCN and the JDK-only signatures must not change. Purely
 * visual: the server never reads smoke. WP5 adds the per-pixel mask entry for the thermal view.
 * 稳定跨模组契约（仅客户端、渲染线程，BEAR WP0 2026-11-05）：读取本客户端当前绘制的 BEAR 烟雾，使其他模组的视图（目前为
 * SparkStrength 卧底热成像，S4）能把烟视为不透明。SparkStrength 通过反射访问本类，因此完整类名与仅使用 JDK 类型的签名都
 * 不得更改。纯视觉：服务端从不读取烟雾。WP5 会为热成像视图追加逐像素遮罩入口。
 */
public final class BearSmokeViewApi {
    private BearSmokeViewApi() {
    }

    /**
     * Optical depth of the smoke on the segment between two world points, from the densities prepared for the last
     * rendered frame; 0 when there is no smoke (transmittance is {@code exp(-depth)}).
     * 两个世界坐标之间线段上的烟雾光学厚度，取最近一次渲染帧准备的浓度；没有烟时为 0（透射率为 {@code exp(-depth)}）。
     */
    public static float opticalDepth(double x0, double y0, double z0, double x1, double y1, double z1) {
        return SmokeVolumes.opticalDepth(x0, y0, z0, x1, y1, z1);
    }

    /** True while this client knows at least one smoke cloud. / 本客户端已知至少一团烟时为 true。 */
    public static boolean hasSmoke() {
        return !SmokeClientState.isEmpty();
    }
}
