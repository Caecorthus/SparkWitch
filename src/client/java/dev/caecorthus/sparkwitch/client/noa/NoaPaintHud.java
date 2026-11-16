package dev.caecorthus.sparkwitch.client.noa;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.hud.CachedHudText;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Draws Noa's owner-only painting line ({@link NoaPaintHudRules}). Reads only the local player's real synced role and
 * its owner-only {@link NoaComponent} ready tick, counted down against the client world clock for display; the server
 * alone decides a painting. It never reads or shows her sanity (owner rule). Called by {@link NoaClient} only on a
 * confirmed SparkWitch server. Role-owned presentation: never the action bar and never the
 * {@code gui.sparkwitch.skills} panel.
 * 绘制诺亚仅本人可见的作画行（{@link NoaPaintHudRules}）。只读取本地玩家同步的真实职业及其仅本人同步的
 * {@link NoaComponent} 就绪刻，并依据客户端世界时钟倒数用于展示；作画是否生效只由服务端决定。从不读取或显示她的
 * 理智（所有者规则）。仅在已确认的 SparkWitch 服务器上由 {@link NoaClient} 调用。属于职业自有展示：绝不使用动作栏，
 * 也绝不进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class NoaPaintHud {
    // Render-thread cache keyed on the line, the ability key text, the Language, the font renderer and the glyph
    // options (see CachedHudText). / 渲染线程缓存，键为该行、技能按键文本、Language、字体渲染器与字形选项（见 CachedHudText）。
    private static final CachedHudText<NoaPaintHudRules.Line> CACHE = new CachedHudText<>(NoaPaintHud::text,
            NoaPaintHudRules.Line::seconds, line -> NoaPaintHudRules.measuredSeconds(line.seconds()));

    private NoaPaintHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the real-role check. getRole stays raw, so a Black Raven
        // acting as Noa never gets her line.
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回。getRole 始终是真实身份，扮演诺亚的黑羽鸦不会得到她的行。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean noa = NoaRules.isNoa(game.getRole(player));
        if (!noa) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!NoaPaintHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, noa,
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        NoaPaintHudRules.Line line = NoaPaintHudRules.line(
                NoaComponent.KEY.get(player).paintCooldownRemaining(client.world.getTime()));
        // The corner row is taken by the shared witch skill line (defensive: Noa has no witch skill id) or by the
        // Saint's Karma line, which any killer of a Hellfire Saint can carry, drawn flush in the corner.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：诺亚没有魔女技能 id），或被圣徒业报行占用（任何在地狱火
        // 期间击杀圣徒的人都可能带有，紧贴角落绘制）。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        CACHE.update(line, line.keyHint() ? SparkWitchClient.abilityKeyText() : null, renderer);
        Text text = CACHE.text();
        int measured = CACHE.measuredWidth();
        int width = context.getScaledWindowWidth();
        int x = NoaPaintHudRules.rowX(width, CACHE.width());
        int y = NoaPaintHudRules.rowY(width, context.getScaledWindowHeight(), measured, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, text, x, y, line.color());
    }

    private static Text text(NoaPaintHudRules.Line line, int seconds, Text keyText) {
        if (line.keyHint()) {
            return Text.translatable(line.key(), keyText);
        }
        return line.hasSeconds() ? Text.translatable(line.key(), seconds) : Text.translatable(line.key());
    }
}
