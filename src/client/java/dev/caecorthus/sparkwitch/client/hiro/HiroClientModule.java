package dev.caecorthus.sparkwitch.client.hiro;

import dev.caecorthus.sparkwitch.client.hud.HiroHudRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * 二阶堂希罗's client hub (presentation only; the server owns every rule). SCAFFOLD: registers the role-owned HUD
 * renderer; the ability-key dispatch for 死亡回归 lives in {@code SparkWitchClient} with the other roles' branches.
 * Later work (HUD rows, the witch-factor network colour, Rope presentation) registers here.
 * 二阶堂希罗的客户端注册中心（仅表现；全部规则由服务端负责）。骨架阶段：注册职业自有的 HUD 渲染器；死亡回归的技能键分发与其他
 * 职业的分支一样位于 {@code SparkWitchClient}。后续工作（HUD 行、魔女因子网络颜色、绳索表现）在此注册。
 */
public final class HiroClientModule {
    private static boolean registered;

    private HiroClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Role-owned HUD from a HudRenderCallback, like the Specter and Dominator lines; never the witch skill panel.
        // 职业自有 HUD，与幽魂、支配者一样经 HudRenderCallback 绘制；绝不使用魔女技能面板。
        HudRenderCallback.EVENT.register((context, tickCounter) -> HiroHudRenderer.render(context));
    }
}
