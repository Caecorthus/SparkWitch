package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Fiend Moment countdown shown to every player (alive, dead or spectating) while the synced moment is active. It reads
 * only the all-player {@link FiendMomentWorldComponent}, whose remaining ticks the client counts down locally for display;
 * the server alone decides the outcome. Called by {@link FiendClient} only on a confirmed SparkWitch server. Role-owned
 * presentation: never the action bar and never the {@code gui.sparkwitch.skills} panel.
 * 时刻进行中向每位玩家（存活、死亡或旁观）显示的魔人时刻倒计时。只读取全员同步的 {@link FiendMomentWorldComponent}，
 * 其剩余 tick 由客户端在本地倒数用于展示，胜负只由服务端决定。仅在已确认的 SparkWitch 服务器上由 {@link FiendClient}
 * 调用。属于职业自有展示：绝不使用动作栏，也绝不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class FiendMomentHud {
    private FiendMomentHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同。
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        // Follow Wathe's HUD visibility so the line never floats alone.
        // 跟随 Wathe 的 HUD 显示状态，避免文本单独悬浮。
        if (WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(client.world);
        if (!moment.isActive()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text text = Text.translatable(FiendMomentHudRules.KEY, FiendMomentHudRules.clock(moment.remainingTicks()));
        int x = FiendMomentHudRules.lineX(context.getScaledWindowWidth(), renderer.getWidth(text));
        context.drawTextWithShadow(renderer, text, x, FiendMomentHudRules.LINE_Y, FiendMomentHudRules.TEXT_COLOR);
    }
}
