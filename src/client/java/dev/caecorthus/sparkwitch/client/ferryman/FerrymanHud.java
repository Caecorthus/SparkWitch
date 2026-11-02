package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * The Charon's two-key HUD, drawn above the hotbar while the local player holds the role: the pardon's remaining
 * cooldown on the shared ability key and the ferry request on the secondary key. The cooldown is the owner's local
 * countdown, so it runs even when no packet arrives. Both key names come from the actual bindings, never a literal.
 * 摆渡人的双键 HUD，本地玩家持有该职业时画在快捷栏上方：共享技能键上的免死冷却，与第二技能键上的摆渡请求。冷却取拥有者
 * 本地倒计时，因此没有数据包时也能走字。两个键名都取自真实键位，绝不写死字面量。
 */
public final class FerrymanHud {
    private static final int READY_COLOR = 0xFFD27A;
    private static final int COOLDOWN_COLOR = 0x9AA0A6;

    private FerrymanHud() {
    }

    static void register() {
        HudRenderCallback.EVENT.register(FerrymanHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || !FerrymanClient.isLocalFerryman(client)) {
            return;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(player);
        int seconds = (component.cooldownTicks() + 19) / 20;
        Text pardon = seconds > 0
                ? Text.translatable("hud.sparkwitch.ferryman.pardon.cooldown",
                        SparkWitchClient.abilityKeyText(), seconds)
                : Text.translatable("hud.sparkwitch.ferryman.pardon.ready", SparkWitchClient.abilityKeyText());
        Text ferry = Text.translatable("hud.sparkwitch.ferryman.ferry.ready",
                SecondaryAbilityController.secondaryKeyText());
        int center = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - 62;
        drawCentered(context, client, pardon, center, y, seconds > 0 ? COOLDOWN_COLOR : READY_COLOR);
        drawCentered(context, client, ferry, center, y + 11, READY_COLOR);
    }

    private static void drawCentered(DrawContext context, MinecraftClient client, Text text, int centerX, int y,
                                     int color) {
        context.drawTextWithShadow(client.textRenderer, text,
                centerX - client.textRenderer.getWidth(text) / 2, y, color);
    }
}
