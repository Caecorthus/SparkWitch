package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.hud.CachedHudText;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoComponent;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Draws Koko's two owner-only rows ({@link KokoStreamHudRules}): her traffic and, under it, her stream line. Display
 * only: it reads the local player's real synced role, her owner-synced {@link KokoComponent} (traffic, and the stream
 * and ready ticks counted down against the client world clock) and her shared mana, and never predicts a stream; the
 * server alone decides one. Called by {@link KokoClient} only on a confirmed SparkWitch server. Role-owned
 * presentation: never the action bar and never the {@code gui.sparkwitch.skills} panel.
 * 绘制可可仅本人可见的两行（{@link KokoStreamHudRules}）：流量，以及其下方的直播行。仅用于展示：读取本地玩家同步的真实职业、
 * 只同步给本人的 {@link KokoComponent}（流量，以及依据客户端世界时钟倒数的直播刻与就绪刻）与共享魔力，从不预测直播；直播是否
 * 生效只由服务端决定。仅在已确认的 SparkWitch 服务器上由 {@link KokoClient} 调用。属于职业自有展示：绝不使用动作栏，也绝不
 * 进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class KokoStreamHud {
    // Render-thread caches, one per row (see CachedHudText). / 渲染线程缓存，每行一个（见 CachedHudText）。
    private static final CachedHudText<KokoStreamHudRules.Line> STREAM = new CachedHudText<>(KokoStreamHud::text,
            KokoStreamHudRules.Line::value, KokoStreamHudRules::measuredValue);
    private static final CachedHudText<KokoStreamHudRules.Line> TRAFFIC = new CachedHudText<>(KokoStreamHud::text,
            KokoStreamHudRules.Line::value, KokoStreamHudRules::measuredValue);

    private KokoStreamHud() {
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
        boolean koko = KokoRules.isKoko(game.getRole(player));
        if (!koko) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!KokoStreamHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, koko,
                GameFunctions.isPlayerPlayingAndAlive(player), NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        // Fail closed: no synced component, no rows. / 失败即关闭：没有同步组件就不画。
        KokoComponent state = KokoComponent.KEY.getNullable(player);
        if (state == null) {
            return;
        }
        long now = client.world.getTime();
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        KokoStreamHudRules.Line stream = KokoStreamHudRules.streamLine(state.streamRemaining(now),
                state.cooldownRemaining(now), witch.hasManaSystem(), witch.getMana());
        KokoStreamHudRules.Line traffic = KokoStreamHudRules.trafficLine(state.traffic());
        // The corner row may be taken by the shared witch skill line (defensive: Koko has no witch skill id) or by the
        // Saint's Karma line, which any killer of a Hellfire Saint can carry.
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：可可没有魔女技能 id），或被圣徒业报行占用（任何在地狱火期间击杀
        // 圣徒的人都可能带有）。
        boolean sharedLineOccupied = witch.getActiveSkillId() != null || witch.getSaintState().karmaCooldownTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        STREAM.update(stream, stream.keyHint() ? SparkWitchClient.abilityKeyText() : null, renderer);
        TRAFFIC.update(traffic, null, renderer);
        int width = context.getScaledWindowWidth();
        int streamY = KokoStreamHudRules.streamRowY(width, context.getScaledWindowHeight(),
                Math.max(STREAM.measuredWidth(), TRAFFIC.measuredWidth()), renderer.fontHeight, sharedLineOccupied);
        context.drawTextWithShadow(renderer, TRAFFIC.text(), KokoStreamHudRules.rowX(width, TRAFFIC.width()),
                KokoStreamHudRules.trafficRowY(streamY, renderer.fontHeight), traffic.color());
        context.drawTextWithShadow(renderer, STREAM.text(), KokoStreamHudRules.rowX(width, STREAM.width()), streamY,
                stream.color());
    }

    private static Text text(KokoStreamHudRules.Line line, int value, Text keyText) {
        return switch (line.argument()) {
            case KEY_HINT -> Text.translatable(line.key(), keyText);
            case TRAFFIC -> Text.translatable(line.key(), value, KokoRules.WIN_TRAFFIC);
            default -> Text.translatable(line.key(), value);
        };
    }
}
