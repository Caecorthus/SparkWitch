package dev.caecorthus.sparkwitch.client.bear.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

/**
 * Client only: the M32A1 HUD row in the bottom-right corner while the local player holds the launcher in the main hand
 * (aiming too): {@code "4/6"} and the M2A1 dial ({@code "100 m"}) on one dark plate, and the six-chamber cylinder
 * icon from the BEAR sprite sheet ({@link M32HudRules}: the next round at 12 o'clock under the pointer, rounds dimmed
 * and a bar under the count while the launcher's item cooldown runs). Visibility is {@link M32HudRules#showsHud}. It
 * reads only the local player's own synced launcher stack and item cooldown and the client-only dial, so it never
 * leaks anything. Role-owned presentation, never the witch skill inventory panel.
 * 仅客户端：本地玩家主手持发射器时（瞄准时也显示）右下角的 M32A1 HUD 行：同一块深色底板上的 {@code "4/6"} 与 M2A1
 * 刻度（{@code "100 m"}），以及来自 BEAR 精灵图的六弹膛转轮图标（{@link M32HudRules}：下一发位于指针下方的 12 点钟方向；
 * 发射器物品冷却期间榴弹变暗，弹数下方显示进度条）。显示条件为 {@link M32HudRules#showsHud}。只读取本地玩家自己已同步的
 * 发射器物品与物品冷却以及仅客户端的刻度，因此不会泄露任何信息。职业自有展示，从不使用魔女技能背包面板。
 */
public final class M32Hud {
    private M32Hud() {
    }

    /** {@code HudRenderCallback}. */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !M32ClientState.holdsLauncherInMainHand(player)) {
            return;
        }
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow Wathe's train HUD too.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；同时跟随 Wathe 列车 HUD 的显示状态。
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(client.world).getGameStatus();
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        if (!M32HudRules.showsHud(client.options.hudHidden, SparkWitchServerConnection.isConfirmedServer(), true,
                status == GameWorldComponent.GameStatus.INACTIVE, trainHudActive,
                status == GameWorldComponent.GameStatus.ACTIVE, GameFunctions.isPlayerPlayingAndAlive(player))) {
            return;
        }
        ItemStack launcher = player.getMainHandStack();
        M32Cylinder cylinder = M32ClientState.cylinder(player);
        boolean coolingDown = player.getItemCooldownManager().isCoolingDown(launcher.getItem());
        TextRenderer renderer = client.textRenderer;
        int loaded = cylinder.loadedCount();
        String count = M32HudRules.countText(loaded);
        String dial = M32HudRules.dialText(M32RangeDial.metres());
        int countWidth = renderer.getWidth(count);
        int dialWidth = renderer.getWidth(dial);
        // Same corner rules as the USEC row: one row up under the shared corner line (witch skill / Saint Karma).
        // 与 USEC 行相同的角落规则：共享角落行（魔女技能 / 圣徒业报）显示时上移一行。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null
                || witch.getSaintState().karmaCooldownTicks() > 0;
        int screenWidth = context.getScaledWindowWidth();
        int x = M32HudRules.rowX(screenWidth, M32HudRules.rowWidth(countWidth, dialWidth));
        int layoutWidth = M32HudRules.rowWidth(countWidth, renderer.getWidth(M32HudRules.WIDEST_DIAL_TEXT));
        int bottom = M32HudRules.rowBottom(screenWidth, context.getScaledWindowHeight(), layoutWidth,
                renderer.fontHeight, sharedLineOccupied);
        int textY = M32HudRules.textY(bottom, renderer.fontHeight);
        int dialX = x + countWidth + M32HudRules.COUNT_GAP;
        // One dark plate under both texts and the bar: readable over bright grass and shader packs.
        // 两段文字与进度条共用一块深色底板：在明亮草地与光影包下也清晰可读。
        M32HudRules.Plate plate = M32HudRules.plate(x, textY, dialX + dialWidth, renderer.fontHeight);
        InventoryCardPaint.roundedFill(context, plate.x(), plate.y(), plate.width(), plate.height(),
                M32HudRules.PLATE_COLOR);
        context.drawTextWithShadow(renderer, count, x, textY, M32HudRules.countColor(loaded));
        context.drawTextWithShadow(renderer, dial, dialX, textY, M32HudRules.DIAL_COLOR);
        if (M32HudRules.showsBar(coolingDown)) {
            // Directly under the count, as wide as it. / 紧贴弹数下方，与其等宽。
            float progress = player.getItemCooldownManager().getCooldownProgress(launcher.getItem(),
                    tickCounter.getTickDelta(true));
            int barY = textY + renderer.fontHeight;
            context.fill(x, barY, x + countWidth, barY + M32HudRules.BAR_HEIGHT, M32HudRules.BAR_TRACK_COLOR);
            int fill = M32HudRules.barFill(progress, countWidth);
            context.fill(x, barY, x + fill, barY + 1, M32HudRules.BAR_FILL_COLOR);
            context.fill(x, barY + 1, x + fill, barY + 2, M32HudRules.BAR_FILL_SHADE_COLOR);
        }
        drawIcon(context, cylinder, coolingDown, dialX + dialWidth + M32HudRules.ICON_GAP, M32HudRules.iconY(bottom));
    }

    private static void drawIcon(DrawContext context, M32Cylinder cylinder, boolean coolingDown, int iconX,
                                 int iconY) {
        int cylinderY = iconY + M32HudRules.ICON_CYLINDER_TOP;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        sprite(context, M32HudRules.CYLINDER, iconX, cylinderY);
        for (int chamber = 0; chamber < M32Cylinder.CHAMBERS; chamber++) {
            M32HudRules.Sprite round = M32HudRules.chamberSprite(cylinder, chamber, coolingDown);
            if (round != null) {
                int slot = M32HudRules.slotOf(chamber, cylinder.next());
                sprite(context, round, iconX + M32HudRules.slotX(slot), cylinderY + M32HudRules.slotY(slot));
            }
        }
        if (M32HudRules.showsRing(cylinder)) {
            sprite(context, M32HudRules.NEXT_RING, iconX + M32HudRules.slotX(0) + M32HudRules.RING_OFFSET,
                    cylinderY + M32HudRules.slotY(0) + M32HudRules.RING_OFFSET);
        }
        sprite(context, M32HudRules.POINTER, iconX + M32HudRules.POINTER_X, cylinderY + M32HudRules.POINTER_Y);
        RenderSystem.disableBlend();
    }

    private static void sprite(DrawContext context, M32HudRules.Sprite sprite, int x, int y) {
        context.drawTexture(M32HudRules.TEXTURE, x, y, (float) sprite.u(), (float) sprite.v(), sprite.width(),
                sprite.height(),
                M32HudRules.TEXTURE_WIDTH, M32HudRules.TEXTURE_HEIGHT);
    }
}
