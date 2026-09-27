package dev.caecorthus.sparkwitch.client.timestealer;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampInventory;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Registers only Time-Stealer-owned client presentation (the owner's stamp HUD). Never touches the Witch skill
 * inventory panel, which belongs only to the three witch roles (AGENTS.md).
 * 只注册窃时者自有的客户端展示（拥有者的邮票 HUD）；从不触及仅属于三个魔女职业的魔女技能背包面板（AGENTS.md）。
 */
public final class TimeStealerClient {
    private static boolean initialized;

    private TimeStealerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                renderHud(context, tickCounter);
            }
        });
    }

    /**
     * Owner-only bottom-right {@code "⌛ N"} row. N is computed on this client from the owner's own synced inventory
     * (hotbar, hidden main slots, offhand, cursor), so no stamp counter is synced and no other viewer can see it.
     * 仅拥有者可见的右下角 {@code "⌛ N"} 行。N 由本客户端根据拥有者自身已同步的背包（快捷栏、隐藏主背包、副手、
     * 光标）计算，因此不同步任何邮票计数，其他观察者也无法看到。
     */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // HudRenderCallback fires even with F1, unlike Wathe's main HUD layer; the row follows Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 主 HUD 层不同；该行跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean playingAndAlive = GameFunctions.isPlayerPlayingAndAlive(player);
        boolean exactTimeStealer = playingAndAlive
                && TimeStealerRules.isTimeStealer(GameWorldComponent.KEY.get(client.world).getRole(player));
        int balance = exactTimeStealer ? TimeStampInventory.balance(player) : 0;
        if (!TimeStealerHudRules.showsStampRow(client.options.hudHidden, playingAndAlive, trainHudActive,
                exactTimeStealer, balance)) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        Text line = Text.translatable(TimeStealerHudRules.STAMPS_KEY, balance);
        // Defensive: the shared bottom-right line only draws for an active skill id, which a Time Stealer lacks.
        // 防御性处理：共享右下角技能行只在存在主动技能 id 时绘制，而窃时者没有。
        boolean sharedLineOccupied = WitchPlayerComponent.KEY.get(player).getActiveSkillId() != null;
        int x = TimeStealerHudRules.rowX(context.getScaledWindowWidth(), renderer.getWidth(line));
        int y = TimeStealerHudRules.rowY(context.getScaledWindowHeight(), renderer.fontHeight, sharedLineOccupied);
        context.drawTextWithShadow(renderer, line, x, y, TimeStealerHudRules.TEXT_COLOR);
    }
}
