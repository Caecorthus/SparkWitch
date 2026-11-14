package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * The Charon's order to its Lost Souls, pinned in the top-left corner for as long as the local player wears the role —
 * the same persistent placement the psycho order uses, instead of a flashing title that is easy to miss. Drawn from
 * {@code HudRenderCallback}, which also fires under F1, so it follows the HUD visibility option itself.
 * 卡戎给迷失灵魂的命令，固定在左上角，只要本地玩家还顶着该职业就一直显示——与疯魔命令相同的常驻位置，而不是一闪而过、容易错过的
 * 标题。由 {@code HudRenderCallback} 绘制，该回调在 F1 下也会触发，因此本行只跟随 HUD 显示选项。
 */
public final class LostSoulOrderHud {
    private static final String ORDER_KEY = "hud.sparkwitch.ferryman.soul_order";
    private static final int ORDER_COLOR = 0xFF4A4A;
    /** Wathe's mood bar sits at 8,16, so the order line takes the strip directly above it. / Wathe 的理智条在 8,16，本行占它正上方那条。 */
    private static final int MARGIN_X = 8;
    private static final int MARGIN_Y = 6;

    private LostSoulOrderHud() {
    }

    static void register() {
        HudRenderCallback.EVENT.register(LostSoulOrderHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (role == null || !FerrymanRules.LOST_SOUL_ID.equals(role.identifier())) {
            return;
        }
        context.drawTextWithShadow(client.textRenderer, Text.translatable(ORDER_KEY), MARGIN_X, MARGIN_Y, ORDER_COLOR);
    }
}
