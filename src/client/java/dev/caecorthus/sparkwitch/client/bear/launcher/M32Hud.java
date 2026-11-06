package dev.caecorthus.sparkwitch.client.bear.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client only: the M32A1 slot badge above the launcher's hotbar slot while (and only while) the local player holds it
 * in the main hand (aiming too), like the Ceremonial Sword badge (owner, 2026-11-06; it replaced the bottom-right row,
 * which could overlap the skill lines). The six-chamber cylinder icon from the BEAR sprite sheet, then the M2A1 dial
 * ({@code "100 m"}) over {@code "4/6"} on one dark plate ({@link M32HudRules#badge}: the next round at 12 o'clock under
 * the pointer, rounds dimmed and a bar under the count while the launcher's item cooldown runs). Visibility is
 * {@link #isShown}; while it shows, {@code M32CooldownNumberMixin} skips Wathe's own cooldown number above the selected
 * slot, which would sit under the count. It reads only the local player's own synced launcher stack, item cooldown and
 * stamina flag and the client-only dial, so it never leaks anything. Role-owned presentation, never the witch skill
 * inventory panel.
 * 仅客户端：本地玩家主手持发射器时（瞄准时也显示），且仅在此时，于发射器所在快捷栏格上方绘制 M32A1 格标记，与仪礼剑标记
 * 一致（所有者 2026-11-06；取代可能与技能行重合的右下角行）。左侧为来自 BEAR 精灵图的六弹膛转轮图标，右侧同一块深色底板
 * 上为 M2A1 刻度（{@code "100 m"}）及其下方的 {@code "4/6"}（{@link M32HudRules#badge}：下一发位于指针下方的 12 点钟方向；
 * 发射器物品冷却期间榴弹变暗，弹数下方显示进度条）。显示条件为 {@link #isShown}；显示期间
 * {@code M32CooldownNumberMixin} 跳过 Wathe 自己在选中格上方的冷却数字（它会落在弹数下面）。只读取本地玩家自己已同步的
 * 发射器物品、物品冷却与体力标记以及仅客户端的刻度，因此不会泄露任何信息。职业自有展示，从不使用魔女技能背包面板。
 */
public final class M32Hud {
    private M32Hud() {
    }

    /**
     * Whether the badge draws this frame ({@link M32HudRules#showsHud} for the local player). The Wathe cooldown-number
     * mixin asks the same question, so Wathe's number is skipped exactly while the badge replaces it.
     * 本帧是否绘制标记（本地玩家的 {@link M32HudRules#showsHud}）。Wathe 冷却数字 mixin 使用同一判定，因此仅在标记取代
     * Wathe 数字时才跳过该数字。
     */
    public static boolean isShown(MinecraftClient client, @Nullable ClientPlayerEntity player) {
        if (player == null || client.world == null || !M32ClientState.holdsLauncherInMainHand(player)) {
            return false;
        }
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow Wathe's train HUD too.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；同时跟随 Wathe 列车 HUD 的显示状态。
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(client.world).getGameStatus();
        return M32HudRules.showsHud(client.options.hudHidden, SparkWitchServerConnection.isConfirmedServer(), true,
                status == GameWorldComponent.GameStatus.INACTIVE, trainHudActive(),
                status == GameWorldComponent.GameStatus.ACTIVE, GameFunctions.isPlayerPlayingAndAlive(player));
    }

    /** {@code HudRenderCallback}. */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!isShown(client, player)) {
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
        // Mirrors Wathe's StaminaRenderer gate, so the badge lifts only over a stamina row that is really drawn.
        // 与 Wathe StaminaRenderer 的条件一致，因此只在体力行确实绘制时才抬升标记。
        boolean staminaRow = M32HudRules.staminaRowShown(trainHudActive(),
                GameFunctions.isPlayerPlayingAndAlive(player),
                PlayerStaminaComponent.KEY.get(player).isInfiniteStamina());
        // The main hand is the selected slot, so the badge sits above the launcher's own slot.
        // 主手即选中格，因此标记位于发射器自己的格子上方。
        M32HudRules.Badge badge = M32HudRules.badge(context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                player.getInventory().selectedSlot,
                M32HudRules.columnWidth(countWidth, renderer.getWidth(M32HudRules.WIDEST_DIAL_TEXT)),
                renderer.fontHeight, staminaRow);
        // One dark plate under both texts and the bar: readable over bright grass and shader packs.
        // 两段文字与进度条共用一块深色底板：在明亮草地与光影包下也清晰可读。
        M32HudRules.Plate plate = badge.plate();
        InventoryCardPaint.roundedFill(context, plate.x(), plate.y(), plate.width(), plate.height(),
                M32HudRules.PLATE_COLOR);
        int x = badge.textX();
        context.drawTextWithShadow(renderer, dial, x, badge.dialY(), M32HudRules.DIAL_COLOR);
        context.drawTextWithShadow(renderer, count, x, badge.countY(), M32HudRules.countColor(loaded));
        if (M32HudRules.showsBar(coolingDown)) {
            // Directly under the count, as wide as it. / 紧贴弹数下方，与其等宽。
            float progress = player.getItemCooldownManager().getCooldownProgress(launcher.getItem(),
                    tickCounter.getTickDelta(true));
            int barY = badge.barY();
            context.fill(x, barY, x + countWidth, barY + M32HudRules.BAR_HEIGHT, M32HudRules.BAR_TRACK_COLOR);
            int fill = M32HudRules.barFill(progress, countWidth);
            context.fill(x, barY, x + fill, barY + 1, M32HudRules.BAR_FILL_COLOR);
            context.fill(x, barY + 1, x + fill, barY + 2, M32HudRules.BAR_FILL_SHADE_COLOR);
        }
        drawIcon(context, cylinder, coolingDown, badge.iconX(), badge.iconY());
    }

    private static boolean trainHudActive() {
        return WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
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
