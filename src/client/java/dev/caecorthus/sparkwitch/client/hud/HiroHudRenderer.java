package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnComponent;
import dev.caecorthus.sparkwitch.roles.killer.hiro.witchification.HiroWitchificationComponent;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * 二阶堂希罗's role-owned HUD, drawn from the {@code HudRenderCallback} that {@code HiroClientModule} registers: two
 * right-aligned rows in the shared bottom-right corner, like the Specter's. Bottom row: 死亡回归 (key hint when
 * castable, the mana shortfall, the cooldown seconds, or the running window's seconds). Upper row: 魔女化 progress
 * ({@code kills/required}) or the witchified state. {@code SaintHudLayoutRules} reserves both rows for the role so the
 * Karma line stacks above. Hidden unless the local player's raw synced role is Hiro, alive and playing, on a confirmed
 * SparkWitch server. Reads only owner-synced state; the server re-validates every press. Never the witch skill panel.
 * Mana itself is not drawn here: the shared top-right {@code WitchManaHudRenderer} row shows it for every mana role.
 * 二阶堂希罗的职业自有 HUD，由 {@code HiroClientModule} 注册的 {@code HudRenderCallback} 绘制：与幽魂一样，在共享右下角绘制两行
 * 右对齐提示。下行为死亡回归（可施放时的按键提示、魔力不足、冷却秒数或窗口剩余秒数），上行为魔女化进度（{@code 击杀/所需}）或
 * 已魔女化状态。{@code SaintHudLayoutRules} 为本职业预留这两行，使业障行叠放在其上方。仅当本地玩家真实同步身份为希罗、存活且参与
 * 对局，并处于已确认的 SparkWitch 服务器时显示。只读取仅同步给本人的状态；每次按键都由服务端重新校验。绝不使用魔女技能面板。
 * 魔力数值不在此绘制：右上角共享的 {@code WitchManaHudRenderer} 已为所有魔力职业显示。
 */
public final class HiroHudRenderer {
    private static final int COLOR = 0xFF000000 | HiroRules.COLOR;

    private HiroHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isActiveHiro(player)) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int height = context.getScaledWindowHeight();
        drawRight(context, renderer, returnText(player), HiroHudRules.rowY(height, renderer.fontHeight, 0));
        drawRight(context, renderer, witchificationText(HiroWitchificationComponent.KEY.get(player)),
                HiroHudRules.rowY(height, renderer.fontHeight, 1));
    }

    private static Text returnText(ClientPlayerEntity player) {
        HiroReturnComponent component = HiroReturnComponent.KEY.get(player);
        long now = player.getWorld().getTime();
        // Mana as the shared top-right row reads it: the owner-synced WitchPlayerComponent.
        // 魔力与右上角共享行的读取方式相同：仅同步给本人的 WitchPlayerComponent。
        int mana = WitchPlayerComponent.KEY.get(player).getMana();
        return switch (HiroHudRules.returnLine(component.isWindowActive(now), component.isOnCooldown(), mana,
                HiroRules.RETURN_MANA_COST)) {
            case WINDOW -> Text.translatable("hud.sparkwitch.nikaido_hiro.hud.return.window",
                    TickTime.ceilSeconds(component.windowRemainingTicks(now)));
            case COOLDOWN -> Text.translatable("hud.sparkwitch.nikaido_hiro.hud.return.cooldown",
                    TickTime.ceilSeconds(component.getCooldownTicks()));
            case NO_MANA -> Text.translatable("hud.sparkwitch.nikaido_hiro.hud.return.no_mana",
                    mana, HiroRules.RETURN_MANA_COST);
            case READY -> Text.translatable("hud.sparkwitch.nikaido_hiro.return.ready",
                    SparkWitchClient.abilityKeyText());
        };
    }

    private static Text witchificationText(HiroWitchificationComponent component) {
        return component.isWitchified()
                ? Text.translatable("hud.sparkwitch.nikaido_hiro.witchification.done")
                : Text.translatable("hud.sparkwitch.nikaido_hiro.witchification.progress",
                        component.kills(), component.required());
    }

    private static void drawRight(DrawContext context, TextRenderer renderer, Text text, int y) {
        context.drawTextWithShadow(renderer, text,
                HiroHudRules.rightAlignedX(context.getScaledWindowWidth(), renderer.getWidth(text)), y, COLOR);
    }

    /**
     * Local player is Hiro (raw synced role) on a confirmed SparkWitch server, alive, playing, not swallowed.
     * 本地玩家在已确认的 SparkWitch 服务器上是希罗（真实同步身份），存活、参与对局、未被吞下。
     */
    static boolean isActiveHiro(@Nullable ClientPlayerEntity player) {
        return player != null
                && SparkWitchServerConnection.isConfirmedServer()
                && HiroRules.isLivingHiro(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }
}
