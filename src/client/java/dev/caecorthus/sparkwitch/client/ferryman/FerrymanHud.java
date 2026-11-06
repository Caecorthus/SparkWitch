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
 * The Charon's two-key readout, pinned to the bottom-right corner while the local player holds the role: the pardon's
 * remaining cooldown on the shared ability key and the ferry's 5 s cooldown on the secondary key. Both cooldowns are
 * the owner's local countdowns, so they run even when no packet arrives, and both key names come from the real
 * bindings instead of a literal.
 * 摆渡人的双键读秒，本地玩家持有该职业时固定在右下角：共享技能键上的免死冷却，与第二技能键上摆渡的 5 秒冷却。
 * 两个冷却都取拥有者本地倒计时，因此没有数据包时也能走字；两个键名都取自真实键位，绝不写死字面量。
 */
public final class FerrymanHud {
    private static final int READY_COLOR = 0xFFD27A;
    private static final int COOLDOWN_COLOR = 0x9AA0A6;
    private static final int MARGIN = 8;
    private static final int LINE_GAP = 11;
    private static final int BOTTOM_OFFSET = 30;

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
        int pardonSeconds = (component.cooldownTicks() + 19) / 20;
        int ferrySeconds = (component.ferryCooldownTicks() + 19) / 20;
        Text pardon = pardonSeconds > 0
                ? Text.translatable("hud.sparkwitch.ferryman.pardon.cooldown",
                        SparkWitchClient.abilityKeyText(), pardonSeconds)
                : Text.translatable("hud.sparkwitch.ferryman.pardon.ready", SparkWitchClient.abilityKeyText());
        Text ferry = ferrySeconds > 0
                ? Text.translatable("hud.sparkwitch.ferryman.ferry.cooldown",
                        SecondaryAbilityController.secondaryKeyText(), ferrySeconds)
                : Text.translatable("hud.sparkwitch.ferryman.ferry.ready",
                        SecondaryAbilityController.secondaryKeyText());
        int bottom = context.getScaledWindowHeight() - BOTTOM_OFFSET;
        drawRight(context, client, pardon, bottom - LINE_GAP, pardonSeconds > 0 ? COOLDOWN_COLOR : READY_COLOR);
        drawRight(context, client, ferry, bottom, ferrySeconds > 0 ? COOLDOWN_COLOR : READY_COLOR);
    }

    private static void drawRight(DrawContext context, MinecraftClient client, Text text, int y, int color) {
        int x = context.getScaledWindowWidth() - MARGIN - client.textRenderer.getWidth(text);
        context.drawTextWithShadow(client.textRenderer, text, x, y, color);
    }
}
