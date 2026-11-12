package dev.caecorthus.sparkwitch.client.koko;

import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoComponent;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Koko's traffic readout, centered one line above the action bar. 可可的流量读数，居中显示在动作栏上方一行。 */
public final class KokoTrafficHud {
    private static final int COLOR = 0xFFFFA000;

    private KokoTrafficHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        var role = GameWorldComponent.KEY.get(client.world).getRole(player);
        if (!KokoRules.ROLE_ID.equals(role == null ? null : role.identifier())) {
            return;
        }
        KokoComponent comp = KokoComponent.KEY.get(player);
        int traffic = comp.traffic();
        int stream = comp.streamSeconds();
        String line = stream > 0
                ? Text.translatable("hud.sparkwitch.koko.traffic_live", traffic, stream).getString()
                : Text.translatable("hud.sparkwitch.koko.traffic", traffic).getString();
        int y = context.getScaledWindowHeight() - 72;
        int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(line)) / 2;
        context.drawTextWithShadow(client.textRenderer, line, x, y, COLOR);
    }
}
