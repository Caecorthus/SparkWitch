package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.List;

/**
 * Role-owned presentation: the USEC's bottom-right ammo line (S2), registered as its own {@code HudRenderCallback}
 * like the Seeker's status line, so no shared HUD dispatcher changes. It reads only the local player's own held rifle
 * stack (synced {@code CUSTOM_DATA}), its synced vanilla item cooldown and the local zoom; the server stays
 * authoritative. It hides under F1 or without Wathe's HUD, and it is never part of the witch skill inventory panel.
 * 职业自有展示：USEC 右下角弹药行（S2），与搜寻者状态行一样注册为独立的 {@code HudRenderCallback}，不改动任何共享 HUD
 * 分发。只读取本地玩家自己手持步枪的物品数据（已同步的 {@code CUSTOM_DATA}）、已同步的原版物品冷却与本地倍率；服务端保持
 * 权威。F1 或 Wathe HUD 隐藏时不显示，且从不属于魔女技能背包面板。
 */
public final class UsecAmmoHud {
    private UsecAmmoHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest exit first: anyone not holding the rifle leaves here. / 先做开销最小的判断。
        ItemStack rifle = player.getMainHandStack();
        if (!UsecRifleClient.isRifle(rifle)) {
            return;
        }
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow both.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；两者都要遵循。
        boolean watheHud = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        if (!UsecAmmoHudRules.visible(SparkWitchServerConnection.isConfirmedServer(), client.options.hudHidden,
                watheHud, game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), UsecRules.isUsec(game.getRole(player)),
                player.isSpectator(), true)) {
            return;
        }
        UsecRifleState state = UsecRifleState.read(rifle);
        UsecCooldowns.Status cooldown = UsecCooldowns.status(player);
        float progress = cooldown.coolingDown()
                ? player.getItemCooldownManager().getCooldownProgress(SparkWitchItems.usecRifle(),
                tickCounter.getTickDelta(true))
                : 0.0F;
        UsecAmmoHudRules.Snapshot snapshot = new UsecAmmoHudRules.Snapshot(state.chamber(), state.magazine(),
                state.suppressor(), cooldown, progress, UsecScopeProfile.isActive(), UsecZoomState.level());

        TextRenderer renderer = client.textRenderer;
        List<UsecAmmoHudRules.Part> parts = UsecAmmoHudRules.line(snapshot);
        Text line = compose(parts);
        Text token = text(parts.getFirst());
        UsecAmmoHudRules.Part zoom = UsecAmmoHudRules.zoomTag(snapshot);
        Text zoomText = zoom == null ? null : text(zoom);
        int lineWidth = renderer.getWidth(line);
        int rowWidth = lineWidth + (zoomText == null ? 0 : renderer.getWidth(zoomText) + UsecAmmoHudRules.ZOOM_TAG_GAP);
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null
                || witch.getSaintState().karmaCooldownTicks() > 0;
        int screenWidth = context.getScaledWindowWidth();
        int x = UsecAmmoHudRules.rowX(screenWidth, lineWidth);
        int y = UsecAmmoHudRules.rowY(screenWidth, context.getScaledWindowHeight(), rowWidth, renderer.fontHeight,
                sharedLineOccupied);
        context.drawTextWithShadow(renderer, line, x, y, UsecAmmoHudRules.TEXT_COLOR);
        if (zoomText != null) {
            context.drawTextWithShadow(renderer, zoomText,
                    x - UsecAmmoHudRules.ZOOM_TAG_GAP - renderer.getWidth(zoomText), y, UsecAmmoHudRules.TAN_COLOR);
        }
        if (UsecAmmoHudRules.showsBar(cooldown.remaining())) {
            // Directly under the chamber token, as wide as it. / 紧贴弹膛标记下方，与其等宽。
            int width = renderer.getWidth(token);
            int barY = y + renderer.fontHeight;
            context.fill(x, barY, x + width, barY + UsecAmmoHudRules.BAR_HEIGHT, UsecAmmoHudRules.BAR_TRACK_COLOR);
            int fill = UsecAmmoHudRules.barFill(progress, width);
            context.fill(x, barY, x + fill, barY + 1, UsecAmmoHudRules.BAR_FILL_COLOR);
            context.fill(x, barY + 1, x + fill, barY + 2, UsecAmmoHudRules.BAR_FILL_SHADE_COLOR);
        }
    }

    static Text compose(List<UsecAmmoHudRules.Part> parts) {
        MutableText line = Text.empty();
        for (UsecAmmoHudRules.Part part : parts) {
            line.append(text(part));
        }
        return line;
    }

    private static MutableText text(UsecAmmoHudRules.Part part) {
        Object[] args = part.args().stream().map(UsecAmmoHud::argument).toArray();
        return Text.translatable(part.key(), args).setStyle(color(part.color()));
    }

    private static Object argument(UsecAmmoHudRules.Arg arg) {
        return switch (arg) {
            case UsecAmmoHudRules.Literal literal -> literal.value();
            case UsecAmmoHudRules.Rounds rounds -> UsecMagazineItem.roundsText(rounds.rounds());
            case UsecAmmoHudRules.Empty empty -> Text.translatable(UsecAmmoHudRules.EMPTY_KEY)
                    .setStyle(color(UsecAmmoHudRules.MUTED_COLOR));
        };
    }

    private static Style color(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }
}
