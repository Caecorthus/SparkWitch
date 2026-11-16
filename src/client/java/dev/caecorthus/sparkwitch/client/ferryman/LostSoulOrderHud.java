package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.client.fiend.FiendMomentHudRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * The Charon's order to its Lost Souls, pinned on the left under Wathe's mood block for as long as the local player is
 * a living Lost Soul ({@link FerrymanClient#isActiveLocalLostSoul}: a fallen soul keeps the role until the round is
 * finalized, but not the order), a persistent line instead of a flashing title that is easy to miss. Drawn from
 * {@code HudRenderCallback}, which also fires under F1, so it follows the same view gates as the Charon's own rows
 * ({@link FerrymanHudRules#showsHud}: F1 off, round-start fade over, Wathe's train HUD shown).
 * 卡戎给迷失灵魂的命令，固定在左侧 Wathe 情绪区块的下方，只要本地玩家是存活的迷失灵魂就一直显示
 * （{@link FerrymanClient#isActiveLocalLostSoul}：倒下的灵魂直到对局结算仍保留职业，但不再显示命令），是一行常驻文本，
 * 而不是一闪而过、容易错过的标题。由 {@code HudRenderCallback} 绘制，该回调在 F1 下也会触发，因此本行与卡戎自己的两行
 * 使用同一组视图门槛（{@link FerrymanHudRules#showsHud}：未按 F1、开局淡入已结束、Wathe 列车 HUD 显示）。
 */
public final class LostSoulOrderHud {
    private static final String ORDER_KEY = "hud.sparkwitch.ferryman.soul_order";
    private static final int ORDER_COLOR = 0xFFFF4A4A;
    /**
     * A Lost Soul has a FAKE mood, so Wathe's {@code MoodRenderer} fills the corner itself: the flag at (5, 6), the
     * task rows from (22, 6) down and the bar under them. The line therefore sits under the deepest pixel that block
     * can reach ({@link FiendMomentHudRules#MOOD_BLOCK_MAX_BOTTOM}), aligned with the flag; the Fiend Moment countdown
     * on that row is centred on the screen.
     * 迷失的灵魂是 FAKE 情绪，Wathe 的 {@code MoodRenderer} 自己占用了这个角落：旗帜在 (5, 6)，任务行自 (22, 6) 向下，其下是
     * 情绪条。因此本行位于该区块可能到达的最低像素（{@link FiendMomentHudRules#MOOD_BLOCK_MAX_BOTTOM}）之下，并与旗帜
     * 左对齐；同一行上的魔人时刻倒计时位于屏幕水平中央。
     */
    static final int LINE_X = 5;
    static final int LINE_Y = FiendMomentHudRules.MOOD_BLOCK_MAX_BOTTOM + 2;

    private LostSoulOrderHud() {
    }

    static void register() {
        HudRenderCallback.EVENT.register(LostSoulOrderHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !FerrymanClient.isActiveLocalLostSoul(player)) {
            return;
        }
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        if (!FerrymanHudRules.showsHud(client.options.hudHidden,
                GameWorldComponent.KEY.get(client.world).getFade(), trainHudActive)) {
            return;
        }
        context.drawTextWithShadow(client.textRenderer, Text.translatable(ORDER_KEY), LINE_X, LINE_Y, ORDER_COLOR);
    }
}
