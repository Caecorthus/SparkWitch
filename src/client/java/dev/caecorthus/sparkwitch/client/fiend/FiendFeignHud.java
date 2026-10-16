package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendParticipation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Draws the dormant Fiend's owner-only feign-death line ({@link FiendFeignHudRules}) in the Dash line's row. Reads only
 * the local player's synced role and its own {@link FiendFeignComponent}, which the server syncs to that player alone
 * and the client counts down locally for display; the server alone decides when a feign starts and ends. Called by
 * {@link FiendClient} only on a confirmed SparkWitch server. Role-owned presentation: never the action bar and never
 * the {@code gui.sparkwitch.skills} panel.
 * 在疾驰行所在行绘制休眠魔人仅本人可见的假死行（{@link FiendFeignHudRules}）。只读取本地玩家同步的职业及其自身的
 * {@link FiendFeignComponent}；服务端只把该组件同步给本人，客户端在本地倒数用于展示，假死何时开始与结束只由服务端决定。
 * 仅在已确认的 SparkWitch 服务器上由 {@link FiendClient} 调用。属于职业自有展示：绝不使用动作栏，也绝不进入
 * {@code gui.sparkwitch.skills} 面板。
 */
public final class FiendFeignHud {
    private FiendFeignHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: the server shows a cycle only to a dormant Fiend's own client, so every other client
        // leaves here.
        // 先做开销最小的判断：服务端只向休眠魔人本人的客户端显示周期，其他所有客户端都在此处返回。
        FiendFeignComponent feign = FiendFeignComponent.get(player);
        boolean shown = feign.isShown();
        if (!shown) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!FiendFeignHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive,
                FiendParticipation.isFiend(player), FiendParticipation.isMomentFiend(player),
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player), shown)) {
            return;
        }
        FiendFeignHudRules.Line line = FiendFeignHudRules.line(feign.isFeigning(), feign.remainingTicks());
        // Same row rule as the Dash line: one row up over the shared witch skill line or the Saint's Karma line.
        // 与疾驰行相同的行规则：共享魔女技能行或圣徒业报行占用时上移一行。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        Text text = Text.translatable(line.key(), line.seconds());
        int measured = renderer.getWidth(Text.translatable(line.key(),
                FiendDashHudRules.measuredSeconds(line.seconds())));
        int width = context.getScaledWindowWidth();
        int x = FiendDashHudRules.rowX(width, renderer.getWidth(text));
        int y = FiendDashHudRules.rowY(width, context.getScaledWindowHeight(), measured, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, text, x, y, line.color());
    }
}
