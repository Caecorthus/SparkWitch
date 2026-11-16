package dev.caecorthus.sparkwitch.client.bear.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
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
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Client only: the M32A1 readout badge while (and only while) the local player holds the launcher in the main hand
 * (aiming too). Since the owner's 2026-11-06 option B ("跟随枪身") it rides the launcher drawn in first person: the
 * frame's screen anchor comes from {@link M32BadgeAnchor} (captured in the hand pass, valid for this frame only), the
 * badge follows it with a time-based exponential smoothing ({@link M32HudRules#follow}, snapping on its first
 * appearance or a jump over a third of the screen), sits centred above the anchor at the hip and to its right at full
 * aim ({@link M32HudRules#followTargetX} / {@link M32HudRules#followTargetY}, lerped by the aim blend), is clamped
 * inside the screen and above the hotbar band ({@link M32HudRules#followBadge}), and a thin leader line ends in a
 * hollow ring on the anchor ({@link M32HudRules#leader}). With no anchor this frame (third person, hand hidden, the
 * launcher not drawn) it falls back to the slot badge above the launcher's hotbar slot ({@link M32HudRules#badge},
 * like the Ceremonial Sword badge). Plate, count, bar and icon are the same in both placements: the six-chamber
 * cylinder icon from the BEAR sprite sheet, then the loaded count {@code "4/6"} on a dark plate (the next round at 12
 * o'clock under the pointer, rounds dimmed and a bar under the count while the launcher's item cooldown runs). No
 * range readout (owner, 2026-11-11: the range is read off the M2A1's fixed scale in the sight). Visibility is
 * {@link #isShown}; while it shows, {@code M32CooldownNumberMixin} skips Wathe's own cooldown number above the
 * selected slot. It reads only the local player's own synced launcher stack, item cooldown and stamina flag and the
 * client-only anchor, so it never leaks anything. Role-owned presentation, never the witch skill inventory panel; the
 * server decides every shot.
 * 仅客户端：本地玩家主手持发射器时（瞄准时也显示），且仅在此时，绘制 M32A1 读数标记。自所有者 2026-11-06 的方案 B（"跟随枪身"）起，
 * 它随第一人称绘制的发射器移动：本帧的屏幕锚点来自 {@link M32BadgeAnchor}（在手部渲染中捕获，仅本帧有效），标记以基于时间的指数
 * 平滑跟随它（{@link M32HudRules#follow}，首次出现或跳动超过屏幕三分之一时直接跳转），腰射时居中于锚点上方、完全瞄准时位于其
 * 右侧（{@link M32HudRules#followTargetX} / {@link M32HudRules#followTargetY}，按瞄准混合插值），钳制在屏幕内且位于快捷栏区域之上
 * （{@link M32HudRules#followBadge}），并有一条细引线终止于锚点上的空心圆环（{@link M32HudRules#leader}）。本帧没有锚点时（第三
 * 人称、隐藏手部、未绘制发射器）回退到发射器所在快捷栏格上方的格标记（{@link M32HudRules#badge}，与仪礼剑标记一致）。两种布局
 * 的底板、弹数、进度条与图标完全相同：来自 BEAR 精灵图的六弹膛转轮图标，其右深色底板上为装填数 {@code "4/6"}（下一发位于
 * 指针下方的 12 点钟方向；发射器物品冷却期间榴弹变暗，弹数下方显示进度条）。不显示射程读数（所有者 2026-11-11：射程由
 * 瞄具内 M2A1 的固定刻度读取）。显示条件为 {@link #isShown}；显示期间 {@code M32CooldownNumberMixin} 跳过 Wathe
 * 自己在选中格上方的冷却数字。只读取本地玩家自己已同步的发射器物品、物品冷却与体力标记以及仅客户端的锚点，因此不会泄露任何
 * 信息。职业自有展示，从不使用魔女技能背包面板；每次发射都由服务端决定。
 */
public final class M32Hud {
    // Smoothing state of the following badge: the last smoothed ideal top-left, its target and its time.
    // 跟随标记的平滑状态：上一帧平滑后的理想左上角、其目标与时刻。
    private static boolean following;
    private static float followX;
    private static float followY;
    private static float targetX;
    private static float targetY;
    private static long followNanos;

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
            following = false;
            return;
        }
        ItemStack launcher = player.getMainHandStack();
        M32Cylinder cylinder = M32ClientState.cylinder(player);
        boolean coolingDown = player.getItemCooldownManager().isCoolingDown(launcher.getItem());
        TextRenderer renderer = client.textRenderer;
        int loaded = cylinder.loadedCount();
        String count = M32HudRules.countText(loaded);
        // The text column is the count alone. / 文字列只有弹数。
        int countWidth = renderer.getWidth(count);
        int width = M32HudRules.badgeWidth(countWidth);
        int scaledWidth = context.getScaledWindowWidth();
        int scaledHeight = context.getScaledWindowHeight();
        // Option B: ride the launcher the hand pass drew this frame; otherwise the slot badge.
        // 方案 B：跟随本帧手部渲染绘制的发射器；否则为格标记。
        M32BadgeAnchor.Anchor anchor = M32BadgeAnchor.current();
        M32HudRules.Badge badge;
        M32HudRules.Leader leader = null;
        if (anchor == null) {
            following = false;
            // Mirrors Wathe's StaminaRenderer gate, so the badge lifts only over a stamina row that is really drawn.
            // 与 Wathe StaminaRenderer 的条件一致，因此只在体力行确实绘制时才抬升标记。
            boolean staminaRow = M32HudRules.staminaRowShown(trainHudActive(),
                    GameFunctions.isPlayerPlayingAndAlive(player),
                    PlayerStaminaComponent.KEY.get(player).isInfiniteStamina());
            // The main hand is the selected slot, so the badge sits above the launcher's own slot.
            // 主手即选中格，因此标记位于发射器自己的格子上方。
            badge = M32HudRules.badge(scaledWidth, scaledHeight, player.getInventory().selectedSlot, countWidth,
                    renderer.fontHeight, staminaRow);
        } else {
            badge = followBadge(anchor, scaledWidth, scaledHeight, width, countWidth, renderer.fontHeight);
            leader = M32HudRules.leader(badge, width, Math.round(anchor.x()), Math.round(anchor.y()));
        }
        if (leader != null) {
            // Under the plate in buffer order, so the plate covers it where they meet. / 缓冲顺序在底板之下，相接处由底板覆盖。
            drawLeader(context, leader);
        }
        // One dark plate under the count and the bar: readable over bright grass and shader packs.
        // 弹数与进度条共用一块深色底板：在明亮草地与光影包下也清晰可读。
        M32HudRules.Plate plate = badge.plate();
        InventoryCardPaint.roundedFill(context, plate.x(), plate.y(), plate.width(), plate.height(),
                M32HudRules.PLATE_COLOR);
        int x = badge.textX();
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

    /**
     * The following badge: the ideal top-left for this anchor and blend, smoothed over real frame time from the last
     * frame's (snapping on the first frame or a big jump), rounded, then clamped and laid out.
     * 跟随标记：由本帧锚点与混合值得到理想左上角，按真实帧时间自上一帧平滑（首帧或大跳动时直接跳转），取整后钳制并布局。
     */
    private static M32HudRules.Badge followBadge(M32BadgeAnchor.Anchor anchor, int scaledWidth, int scaledHeight,
                                                 int width, int column, int fontHeight) {
        float idealX = M32HudRules.followTargetX(anchor.x(), width, anchor.aimBlend());
        float idealY = M32HudRules.followTargetY(anchor.y(), M32HudRules.BADGE_HEIGHT, anchor.aimBlend());
        long now = Util.getMeasuringTimeNano();
        boolean snap = M32HudRules.followSnaps(following, targetX, targetY, idealX, idealY, scaledWidth);
        double seconds = following ? (now - followNanos) / 1.0E9 : 0.0;
        followX = M32HudRules.follow(followX, idealX, seconds, snap);
        followY = M32HudRules.follow(followY, idealY, seconds, snap);
        targetX = idealX;
        targetY = idealY;
        followNanos = now;
        following = true;
        return M32HudRules.followBadge(scaledWidth, scaledHeight, Math.round(followX), Math.round(followY), column,
                fontHeight);
    }

    /**
     * The leader: a 1 px line as a quad on the GUI layer (the layer {@code DrawContext#fill} uses, same winding), then
     * the hollow ring on the anchor. / 引线：GUI 渲染层（{@code DrawContext#fill} 所用，相同绕序）上作为四边形的 1 像素线，
     * 然后是锚点上的空心圆环。
     */
    private static void drawLeader(DrawContext context, M32HudRules.Leader leader) {
        if (leader.line()) {
            float dx = leader.endX() - leader.startX();
            float dy = leader.endY() - leader.startY();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0.0F) {
                float nx = -dy / length * 0.5F;
                float ny = dx / length * 0.5F;
                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
                buffer.vertex(matrix, leader.startX() - nx, leader.startY() - ny, 0.0F).color(M32HudRules.LEADER_COLOR);
                buffer.vertex(matrix, leader.startX() + nx, leader.startY() + ny, 0.0F).color(M32HudRules.LEADER_COLOR);
                buffer.vertex(matrix, leader.endX() + nx, leader.endY() + ny, 0.0F).color(M32HudRules.LEADER_COLOR);
                buffer.vertex(matrix, leader.endX() - nx, leader.endY() - ny, 0.0F).color(M32HudRules.LEADER_COLOR);
            }
        }
        context.drawBorder(leader.ringX(), leader.ringY(), M32HudRules.LEADER_RING_SIZE, M32HudRules.LEADER_RING_SIZE,
                M32HudRules.LEADER_COLOR);
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
