package dev.caecorthus.sparkwitch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Restores vanilla attack indicator rendering for the ceremonial sword while Wathe owns the in-round crosshair.
 * 当 wathe 接管局内准星时，为仪礼剑补回原版攻击冷却指示器。
 */
public final class CeremonialSwordCrosshairRenderer {
    private static final Identifier ATTACK_INDICATOR_FULL = Identifier.ofVanilla("hud/crosshair_attack_indicator_full");
    private static final Identifier ATTACK_INDICATOR_BACKGROUND = Identifier.ofVanilla("hud/crosshair_attack_indicator_background");
    private static final Identifier ATTACK_INDICATOR_PROGRESS = Identifier.ofVanilla("hud/crosshair_attack_indicator_progress");

    private CeremonialSwordCrosshairRenderer() {
    }

    public static void render(
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        if (!player.getMainHandStack().isOf(SparkWitchItems.ceremonialSword())
                || client.options.getAttackIndicator().getValue() != AttackIndicator.CROSSHAIR) {
            return;
        }

        float progress = player.getAttackCooldownProgress(tickCounter.getTickDelta(true));
        int y = context.getScaledWindowHeight() / 2 - 7 + 16;
        int x = context.getScaledWindowWidth() / 2 - 8;

        RenderSystem.enableBlend();
        if (shouldShowFullIndicator(client, player, progress)) {
            context.drawGuiTexture(ATTACK_INDICATOR_FULL, x, y, 16, 16);
        } else if (progress < 1.0f) {
            int width = (int) (progress * 17.0f);
            context.drawGuiTexture(ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4);
            context.drawGuiTexture(ATTACK_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, width, 4);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean shouldShowFullIndicator(MinecraftClient client, ClientPlayerEntity player, float progress) {
        return client.targetedEntity instanceof LivingEntity living
                && progress >= 1.0f
                && player.getAttackCooldownProgressPerTick() > 5.0f
                && living.isAlive();
    }
}
