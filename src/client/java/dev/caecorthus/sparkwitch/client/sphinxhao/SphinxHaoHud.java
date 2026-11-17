package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.hud.CachedHudText;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Draws the Sphinx Hao's four owner-only rows ({@link SphinxHaoHudRules}). Display only: it reads the local player's
 * real synced role, its owner-synced {@link SphinxHaoPlayerComponent} (hao and both ready ticks, counted down against
 * the client world clock) and the synced {@link SphinxHaoWorldComponent} (its own crawl window and the infected set
 * only a Sphinx Hao receives), and never predicts a skill. Called by {@link SphinxHaoClient} only on a confirmed
 * SparkWitch server. Role-owned presentation: never the action bar and never the {@code gui.sparkwitch.skills} panel.
 * 绘制狮身豪仅本人可见的四行（{@link SphinxHaoHudRules}）。仅用于展示：读取本地玩家同步的真实职业、只同步给本人的
 * {@link SphinxHaoPlayerComponent}（豪度值与两个就绪刻，依据客户端世界时钟倒数）以及同步的 {@link SphinxHaoWorldComponent}
 * （自己的陆泳窗口与只发给狮身豪的感染集合），从不预测技能。仅在已确认的 SparkWitch 服务器上由 {@link SphinxHaoClient} 调用。
 * 属于职业自有展示：绝不使用动作栏，也绝不进入 {@code gui.sparkwitch.skills} 面板。
 */
final class SphinxHaoHud {
    // Render-thread caches, one per row (see CachedHudText). / 渲染线程缓存，每行一个（见 CachedHudText）。
    private static final CachedHudText<SphinxHaoHudRules.Line> INFECT = row();
    private static final CachedHudText<SphinxHaoHudRules.Line> ROAR = row();
    private static final CachedHudText<SphinxHaoHudRules.Line> INFECTED = row();
    private static final CachedHudText<SphinxHaoHudRules.Line> HAO = row();

    private SphinxHaoHud() {
    }

    private static CachedHudText<SphinxHaoHudRules.Line> row() {
        return new CachedHudText<>(SphinxHaoHud::text, SphinxHaoHudRules.Line::value, SphinxHaoHudRules::measuredValue);
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the real-role check (getRole stays raw).
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回（getRole 始终是真实身份）。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean sphinxHao = SphinxHaoRules.isSphinxHao(game.getRole(player));
        if (!sphinxHao) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!SphinxHaoHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive,
                sphinxHao, GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        // Fail closed: no synced component, no rows. / 失败即关闭：没有同步组件就不画。
        SphinxHaoPlayerComponent state = SphinxHaoPlayerComponent.KEY.getNullable(player);
        SphinxHaoWorldComponent world = SphinxHaoWorldComponent.KEY.getNullable(client.world);
        if (state == null || world == null) {
            return;
        }
        long now = client.world.getTime();
        int crawlTicks = world.isCrawling(player.getUuid(), now) ? world.roarRemaining(now) : 0;
        SphinxHaoHudRules.Line infect = SphinxHaoHudRules.infectLine(world.isRoarActive(now),
                state.infectCooldownRemaining(now));
        SphinxHaoHudRules.Line roar = SphinxHaoHudRules.roarLine(crawlTicks, state.roarCooldownRemaining(now));
        SphinxHaoHudRules.Line infected = SphinxHaoHudRules.infectedLine(world.infectedIds().size());
        SphinxHaoHudRules.Line hao = SphinxHaoHudRules.haoLine(state.hao());
        // The corner row may be taken by the shared witch skill line (defensive: it has no witch skill id) or by the
        // Saint's Karma line, which any killer of a Hellfire Saint can carry.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：它没有魔女技能 id），或被圣徒业报行占用（任何在地狱火期间击杀圣徒的人
        // 都可能带有）。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        INFECT.update(infect, infect.keyHint() ? SparkWitchClient.abilityKeyText() : null, renderer);
        ROAR.update(roar, roar.keyHint() ? SecondaryAbilityController.secondaryKeyText() : null, renderer);
        INFECTED.update(infected, null, renderer);
        HAO.update(hao, null, renderer);
        int measured = Math.max(Math.max(INFECT.measuredWidth(), ROAR.measuredWidth()),
                Math.max(INFECTED.measuredWidth(), HAO.measuredWidth()));
        int width = context.getScaledWindowWidth();
        int bottomY = SphinxHaoHudRules.bottomRowY(width, context.getScaledWindowHeight(), measured,
                renderer.fontHeight, sharedLineOccupied);
        draw(context, renderer, width, INFECT, infect, SphinxHaoHudRules.rowY(bottomY, renderer.fontHeight, 0));
        draw(context, renderer, width, ROAR, roar, SphinxHaoHudRules.rowY(bottomY, renderer.fontHeight, 1));
        draw(context, renderer, width, INFECTED, infected, SphinxHaoHudRules.rowY(bottomY, renderer.fontHeight, 2));
        draw(context, renderer, width, HAO, hao, SphinxHaoHudRules.rowY(bottomY, renderer.fontHeight, 3));
    }

    private static void draw(DrawContext context, TextRenderer renderer, int width,
                             CachedHudText<SphinxHaoHudRules.Line> row, SphinxHaoHudRules.Line line, int y) {
        context.drawTextWithShadow(renderer, row.text(), SphinxHaoHudRules.rowX(width, row.width()), y, line.color());
    }

    private static Text text(SphinxHaoHudRules.Line line, int value, Text keyText) {
        if (line.keyHint()) {
            return Text.translatable(line.key(), keyText);
        }
        return line.argument() == SphinxHaoHudRules.Argument.NONE ? Text.translatable(line.key())
                : Text.translatable(line.key(), value);
    }
}
