package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.api.client.SecondarySkillKeyApi;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Two bottom-right lines for the Medium: 通灵 (cooldown / running / ready / price) on top and 招魂 on the bottom, in
 * the ghost-fire teal role colour. Both rows sit in the shared SparkWitch corner slot, which
 * {@code SaintHudLayoutRules} reserves two rows high for the Medium. Reads only the local player's raw synced role,
 * the owner-synced 通灵 cooldown, the world seance window and the synced shop balance; the server re-validates every
 * press. 招魂 has no cooldown, so its row only shows price or readiness.
 * 通灵师右下角两行提示：上行为通灵（冷却 / 进行中 / 可用 / 价格），下行为招魂，使用鬼火青绿职业色。两行都位于 SparkWitch
 * 共享右下角位置，{@code SaintHudLayoutRules} 为通灵师预留两行高度。只读取本地玩家真实同步的身份、仅同步给本人的冷却、
 * 世界通灵窗口与同步的金币余额；每次按键都由服务端重新校验。
 */
public final class MediumHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int LINE_GAP = 2;
    private static final int COLOR = 0xFF000000 | MediumRules.COLOR;

    private MediumHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isActiveMedium(player)) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int bottomY = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        drawRight(context, renderer, hauntText(player), bottomY);
        drawRight(context, renderer, seanceText(player), bottomY - renderer.fontHeight - LINE_GAP);
    }

    /**
     * Local player is the Medium (raw synced role) on a confirmed SparkWitch server, alive, playing, not swallowed.
     * 本地玩家在已确认的 SparkWitch 服务器上是通灵师（真实同步身份），存活、参与对局、未被吞下。
     */
    static boolean isActiveMedium(@Nullable ClientPlayerEntity player) {
        return player != null
                && MediumRules.isMedium(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && SparkWitchServerConnection.isConfirmedServer()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    private static Text seanceText(ClientPlayerEntity player) {
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(player);
        if (component.getSeanceCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.medium.seance.cooldown",
                    TickTime.ceilSeconds(component.getSeanceCooldownTicks())
            );
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(player.getWorld());
        if (seance.isActive()) {
            return Text.translatable(
                    "hud.sparkwitch.medium.seance.active",
                    TickTime.ceilSeconds(seance.getTicksLeft())
            );
        }
        int price = MediumRules.seancePrice(tier(player));
        if (PlayerShopComponent.KEY.get(player).getBalance() < price) {
            return Text.translatable("hud.sparkwitch.medium.seance.price", price);
        }
        return Text.translatable("hud.sparkwitch.medium.seance.ready", SparkWitchClient.abilityKeyText());
    }

    private static Text hauntText(ClientPlayerEntity player) {
        int price = MediumRules.hauntPrice();
        if (PlayerShopComponent.KEY.get(player).getBalance() < price) {
            return Text.translatable("hud.sparkwitch.medium.haunt.price", price);
        }
        return Text.translatable("hud.sparkwitch.medium.haunt.ready", SecondarySkillKeyApi.boundKeyText());
    }

    private static int tier(ClientPlayerEntity player) {
        return MediumRules.currentTier(GameWorldComponent.KEY.get(player.getWorld()));
    }

    private static void drawRight(DrawContext context, TextRenderer renderer, Text text, int y) {
        context.drawTextWithShadow(
                renderer,
                text,
                Math.max(0, context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(text)),
                y,
                COLOR
        );
    }
}
