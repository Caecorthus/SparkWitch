package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

import static dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchSwordHudRules.*;

/**
 * Draws the Ceremonial Sword's cooldowns (owner pick A2 + B2, 2026-10-05). The crosshair marks need the sword in the
 * main hand; the slot badge follows the sword to any hotbar slot or the off-hand (owner, 2026-10-05). The mixins own
 * the confirmed-server and Grand Witch gates; this class only reads the same timers as the inventory card and paints
 * them. Presentation only.
 * 绘制仪礼剑冷却（所有者 2026-10-05 选定 A2 + B2）。准星标记需要主手持剑；剑槽标记跟随剑所在的任意物品栏格或副手格
 * （所有者 2026-10-05）。已确认服务端与大魔女的资格由混入判断；本类只读取与背包卡片相同的计时并绘制。仅用于展示。
 */
public final class GrandWitchSwordHud {
    private static final ReadyFlash KILL_FLASH = new ReadyFlash();
    private static final ReadyFlash DASH_FLASH = new ReadyFlash();

    private GrandWitchSwordHud() {
    }

    /** B2: attack recharge under the crosshair, kill glyph on the left, dash chevrons on the right. / B2：准星左右图标。 */
    public static void renderCrosshair(DrawContext context, ClientPlayerEntity player, float tickDelta) {
        Timers timers = Timers.read(player, tickDelta);
        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;

        int barX = cx + ATTACK_BAR_X;
        int barY = cy + ATTACK_BAR_Y;
        float attack = MathHelper.clamp(player.getAttackCooldownProgress(tickDelta), 0, 1);
        context.fill(barX, barY, barX + ATTACK_BAR_WIDTH, barY + ATTACK_BAR_HEIGHT, ATTACK_TRACK);
        context.fill(barX, barY, barX + (int) (attack * ATTACK_BAR_WIDTH), barY + ATTACK_BAR_HEIGHT, ATTACK_FILL);

        drawGlyph(context, KILL_GLYPH, cx + KILL_GLYPH_X, cy + KILL_GLYPH_Y, timers.killProgress(), true,
                timers.killColor(), GLYPH_TRACK);
        drawGlyph(context, DASH_GLYPH, cx + DASH_GLYPH_X, cy + DASH_GLYPH_Y, timers.dashProgress(), false,
                timers.dashColor(), GLYPH_TRACK);
    }

    /**
     * A2: drawn on Wathe's cooldown-number line above the slot that holds the sword (selected slot first, then any
     * hotbar slot, then the off-hand); nothing when the sword is only in the hidden inventory. Ready shows the kill
     * glyph alone; cooling adds Wathe-format seconds. Five pips under it recover one per fifth of the dash cooldown.
     * A2：画在持剑格上方、Wathe 冷却数字所在行（先选中格，再任意物品栏格，最后副手格）；剑只在背包隐藏格时不绘制。
     * 就绪时只显示剑形，冷却时附 Wathe 格式秒数；下方 5 个点表示冲刺冷却。
     */
    public static void renderSlotBadge(DrawContext context, TextRenderer renderer, ClientPlayerEntity player,
                                       float tickDelta) {
        PlayerInventory inventory = player.getInventory();
        int slot = swordHotbarSlot(inventory.selectedSlot,
                i -> inventory.getStack(i).isOf(SparkWitchItems.ceremonialSword()));
        int centre;
        if (slot >= 0) {
            centre = slotCentreX(context.getScaledWindowWidth(), slot);
        } else if (player.getOffHandStack().isOf(SparkWitchItems.ceremonialSword())) {
            // Vanilla draws the off-hand slot on the side opposite the main arm. / 原版副手格位于主手的另一侧。
            centre = offhandCentreX(context.getScaledWindowWidth(), player.getMainArm().getOpposite() == Arm.LEFT);
        } else {
            return;
        }
        Timers timers = Timers.read(player, tickDelta);
        int textY = badgeTextY(context.getScaledWindowHeight());
        int glyphWidth = BADGE_GLYPH[0].length();

        context.getMatrices().push();
        // Same depth as Wathe's number, above hotbar items. / 与 Wathe 数字同一深度，高于物品栏物品。
        context.getMatrices().translate(0.0F, 0.0F, 200.0F);
        if (timers.killReady()) {
            int x = centre - glyphWidth / 2;
            drawShadow(context, BADGE_GLYPH, x, textY);
            drawGlyph(context, BADGE_GLYPH, x, textY, 1.0F, true, timers.killColor(), GLYPH_TRACK);
        } else {
            String seconds = timer(timers.killRemaining());
            int width = glyphWidth + BADGE_GLYPH_GAP + renderer.getWidth(seconds);
            int x = centre - width / 2;
            drawShadow(context, BADGE_GLYPH, x, textY);
            drawGlyph(context, BADGE_GLYPH, x, textY, timers.killProgress(), true, KILL_COOLING, GLYPH_TRACK);
            context.drawTextWithShadow(renderer, seconds, x + glyphWidth + BADGE_GLYPH_GAP, textY, WHITE);
        }

        int pipsX = pipsX(centre);
        int pipsY = pipsY(context.getScaledWindowHeight());
        context.fill(pipsX - 1, pipsY - 1, pipsX + DASH_PIPS * PIP_STEP, pipsY + PIP_SIZE + 1, PIP_PLATE);
        int lit = litPips(timers.dashProgress());
        float charge = recoveringPipCharge(timers.dashProgress());
        for (int pip = 0; pip < DASH_PIPS; pip++) {
            int color = pip < lit ? timers.dashColor()
                    : pip == lit ? lerpArgb(PIP_EMPTY, DASH_COOLING, charge * 0.7F) : PIP_EMPTY;
            int x = pipsX + pip * PIP_STEP;
            context.fill(x, pipsY, x + PIP_SIZE, pipsY + PIP_SIZE, color);
        }
        context.getMatrices().pop();
    }

    /** Paints a glyph row by row, merging same-coloured runs. / 逐行绘制图标，合并同色像素段。 */
    private static void drawGlyph(DrawContext context, String[] glyph, int x, int y, float progress, boolean vertical,
                                  int fill, int track) {
        for (int row = 0; row < glyph.length; row++) {
            String line = glyph[row];
            int column = 0;
            while (column < line.length()) {
                if (line.charAt(column) != '#') {
                    column++;
                    continue;
                }
                int color = isFilled(glyph, row, column, progress, vertical) ? fill : track;
                int end = column + 1;
                while (end < line.length() && line.charAt(end) == '#'
                        && (isFilled(glyph, row, end, progress, vertical) ? fill : track) == color) {
                    end++;
                }
                context.fill(x + column, y + row, x + end, y + row + 1, color);
                column = end;
            }
        }
    }

    private static void drawShadow(DrawContext context, String[] glyph, int x, int y) {
        drawGlyph(context, glyph, x + 1, y + 1, 1.0F, true, GLYPH_SHADOW, GLYPH_SHADOW);
    }

    /** One frame's view of both sword timers plus their ready flashes. / 单帧的两个计时与就绪闪白。 */
    private record Timers(float killRemaining, float killProgress, int killColor,
                          float dashProgress, int dashColor) {
        static Timers read(ClientPlayerEntity player, float tickDelta) {
            int kill = GrandWitchClientPresentation.swordKillTicks(player);
            int dash = GrandWitchClientPresentation.swordDashTicks(player);
            int dashTotal = GrandWitchClientPresentation.swordDashTotalTicks(player);
            long time = player.getWorld().getTime();
            KILL_FLASH.observe(kill, time);
            DASH_FLASH.observe(dash, time);
            float now = time + tickDelta;
            // Never show 0.0s while the server still counts the last tick. / 服务端仍在计最后一刻时不显示 0.0s。
            float killRemaining = kill > 0 ? Math.max(1.0F, kill - tickDelta) : 0.0F;
            float dashRemaining = dash > 0 ? Math.max(1.0F, dash - tickDelta) : 0.0F;
            int killColor = kill > 0 ? KILL_COOLING : lerpArgb(KILL_READY, WHITE, KILL_FLASH.strength(now));
            int dashColor = dash > 0 ? DASH_COOLING : lerpArgb(DASH_READY, WHITE, DASH_FLASH.strength(now));
            return new Timers(killRemaining,
                    progress(killRemaining, GrandWitchRules.CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS), killColor,
                    progress(dashRemaining, dashTotal > 0 ? dashTotal : CeremonialSwordItem.DASH_COOLDOWN_TICKS),
                    dashColor);
        }

        boolean killReady() {
            return killRemaining <= 0.0F;
        }
    }
}
