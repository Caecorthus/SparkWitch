package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseSyncCodec;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Black-Raven-owned row immediately above the shared primary-skill line. / 黑羽鸦自有的第二行提示，位于共享主技能行正上方。 */
public final class BlackRavenHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int ROW_GAP = 2;
    private static final int DISABLED_COLOR = 0x777777;

    private BlackRavenHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameWorldComponent.KEY.get(player.getWorld()).isRunning()
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        BlackRavenDisguiseSyncCodec.View view = BlackRavenDisguiseClientState.view(player);
        if (view.bound() && view.disguised() && BlackRavenDisguiseClientState.isDisguised(player)) {
            renderDisguise(context, view);
            return;
        }

        Text line = Text.translatable(
                BlackRavenClientState.isSensedMode()
                        ? "hud.sparkwitch.black_raven.instinct.sensed"
                        : "hud.sparkwitch.black_raven.instinct.normal",
                SecondaryAbilityController.secondaryKeyText()
        );
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight()
                - BOTTOM_PADDING
                - renderer.fontHeight * 2
                - ROW_GAP;
        int color = BlackRavenClientState.isPerceptionActive(player) ? DISABLED_COLOR : BlackRavenRules.COLOR;
        context.drawTextWithShadow(renderer, line, x, y, color);
    }

    /**
     * While disguised the instinct mode is fixed, so the mode row becomes one compact disguise line in the same
     * right-aligned slot, lifted to clear the disguise role's own bottom-right HUD lines.
     * 伪装期间本能模式固定，模式行改为一行紧凑的伪装提示，保持右对齐位置，并上移以避开伪装职业自己的右下角 HUD。
     */
    private static void renderDisguise(DrawContext context, BlackRavenDisguiseSyncCodec.View view) {
        Text role = BlackRavenDisguiseClientState.roleName(view.acting());
        int seconds = BlackRavenDisguiseClientRules.statusSeconds(view);
        Text line = switch (BlackRavenDisguiseClientRules.status(view)) {
            case LOCKED -> Text.translatable("hud.sparkwitch.black_raven.disguise.locked", role, seconds);
            case COOLDOWN -> Text.translatable("hud.sparkwitch.black_raven.disguise.cooldown", role, seconds);
            case READY -> Text.translatable("hud.sparkwitch.black_raven.disguise.ready", role);
        };
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = BlackRavenDisguiseClientRules.disguiseLineY(
                context.getScaledWindowHeight(),
                renderer.fontHeight,
                BOTTOM_PADDING,
                ROW_GAP
        );
        context.drawTextWithShadow(renderer, line, x, y, BlackRavenRules.COLOR);
    }
}
