package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.api.client.SecondarySkillKeyApi;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumBlessing;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.agmas.noellesroles.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Medium's two bottom-right rows (owner pick 2026-11-13): 通灵 on the shared ability key above, 幽灵的加护 on the
 * SparkWitch skill key below, with the active-blessing chips left of the lower row. The rows follow
 * {@link MediumHudRules}; both sit in the shared SparkWitch corner slot, which {@code SaintHudLayoutRules} reserves two
 * rows high for the Medium, and lift together above the hotbar column at narrow GUI widths. Reads only the local
 * player's raw synced role, the owner-synced cooldowns and corpse-sight set, the world vote window, the synced shop
 * balance and the local player's own status effects; the server re-validates every press. 招魂 has no row: it lives in
 * the inventory drawer.
 * 通灵师右下角两行（所有者 2026-11-13 选定）：上行为共享技能键上的通灵，下行为 SparkWitch 技能键上的幽灵的加护，生效中的
 * 加护以小图标显示在下行左侧。两行遵循 {@link MediumHudRules}，都位于 SparkWitch 共享右下角位置（{@code SaintHudLayoutRules}
 * 为通灵师预留两行高度），GUI 较窄时一同抬升到快捷栏列之上。只读取本地玩家真实同步的身份、仅同步给本人的冷却与尸体透视集合、
 * 世界投票窗口、同步的金币余额以及本地玩家自己的状态效果；每次按键都由服务端重新校验。招魂没有角落行：它位于背包抽屉中。
 */
public final class MediumHudRenderer {
    /**
     * A Speed effect counts as the blessing's only while it started within this long of the granted-Speed result
     * (40 s effect plus slack for the result's network delay); any other Speed source draws no chip.
     * 只有在收到「获得速度」结果后这段时间内的速度效果才算作加护（40 秒效果加网络延迟余量）；其他来源的速度不显示图标。
     */
    private static final long SPEED_CHIP_WINDOW_MS = TickTime.ceilSeconds(MediumRules.BLESSING_SPEED_TICKS) * 1000L + 3000L;

    private static long speedGrantedAtMs = Long.MIN_VALUE;
    @Nullable
    private static ClientWorld speedWorld;

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
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(player);
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(player.getWorld());
        int tier = MediumRules.currentTier(GameWorldComponent.KEY.get(player.getWorld()));
        int balance = PlayerShopComponent.KEY.get(player).getBalance();
        MediumHudRules.Line upper = MediumHudRules.row(MediumHudRules.Skill.SEANCE,
                component.getSeanceCooldownTicks(), seance.isActive(), seance.isRevealed(), seance.getKind(),
                seance.getTicksLeft(), balance, MediumRules.seancePrice(tier));
        MediumHudRules.Line lower = MediumHudRules.row(MediumHudRules.Skill.BLESSING,
                component.getBlessingCooldownTicks(), seance.isActive(), seance.isRevealed(), seance.getKind(),
                seance.getTicksLeft(), balance, MediumRules.blessingPrice(tier));

        List<Chip> chips = chips(client, player, component);
        int[] labelWidths = new int[chips.size()];
        for (int i = 0; i < chips.size(); i++) {
            labelWidths[i] = chips.get(i).label() == null ? 0 : renderer.getWidth(chips.get(i).label());
        }
        int chipStrip = MediumHudRules.chipStripWidth(labelWidths);
        int chipReserve = chips.isEmpty() ? 0 : chipStrip + MediumHudRules.CHIPS_TO_TEXT;

        Text seanceKey = SparkWitchClient.abilityKeyText();
        Text blessingKey = SecondarySkillKeyApi.boundKeyText();
        Text upperText = text(upper, false, seanceKey);
        Text lowerText = text(lower, false, blessingKey);
        int measured = Math.max(renderer.getWidth(text(upper, true, seanceKey)),
                renderer.getWidth(text(lower, true, blessingKey)) + chipReserve);
        int screenWidth = context.getScaledWindowWidth();
        int lowerY = FiendDashHudRules.rowY(screenWidth, context.getScaledWindowHeight(), measured,
                renderer.fontHeight, false);
        int upperY = lowerY - renderer.fontHeight - FiendDashHudRules.ROW_GAP;
        int upperX = FiendDashHudRules.rowX(screenWidth, renderer.getWidth(upperText));
        int lowerX = FiendDashHudRules.rowX(screenWidth, renderer.getWidth(lowerText));
        // iconText keeps the coin glyph untinted and unshadowed, like every price elsewhere.
        // iconText 让金币字形保持原色且无阴影，与其他所有价格一致。
        InventoryCardPaint.iconText(context, renderer, upperText, upperX, upperY, upper.color(), true);
        InventoryCardPaint.iconText(context, renderer, lowerText, lowerX, lowerY, lower.color(), true);
        drawChips(context, renderer, chips, lowerX - MediumHudRules.CHIPS_TO_TEXT - chipStrip, lowerY);
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

    /**
     * Client thread, from the result card: the server granted Speed now, so a Speed effect in the next
     * {@link #SPEED_CHIP_WINDOW_MS} is the blessing's.
     * 客户端线程，由结果卡片调用：服务端此刻给予了速度，因此接下来 {@link #SPEED_CHIP_WINDOW_MS} 内的速度效果属于加护。
     */
    static void noteSpeedGranted(ClientWorld world, long nowMs) {
        speedWorld = world;
        speedGrantedAtMs = nowMs;
    }

    /** One chip: the blessing icon and an optional seconds label. / 一个图标：加护图标与可选的秒数标签。 */
    private record Chip(MediumBlessing icon, @Nullable String label) {
    }

    /**
     * The active blessings, left to right: corpse sight (the round-long owner-synced set is not empty), the round-long
     * shield (the blessing rebuilds NoellesRoles' whiskey stack with an infinite duration, which no other source does)
     * and Speed with its remaining seconds (only right after a granted-Speed result).
     * 生效中的加护，从左到右：尸体透视（整局有效、仅同步给本人的集合非空）、整局护盾（加护会把 NoellesRoles 威士忌护盾重建为
     * 无限时长，其他来源不会这样做）以及带剩余秒数的速度（仅在收到「获得速度」结果之后）。
     */
    private static List<Chip> chips(MinecraftClient client, ClientPlayerEntity player,
                                    MediumPlayerComponent component) {
        List<Chip> chips = new ArrayList<>(3);
        if (!component.getCorpseSightBodies().isEmpty()) {
            chips.add(new Chip(MediumBlessing.CORPSE_SIGHT, null));
        }
        StatusEffectInstance shield = player.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        if (shield != null && shield.isInfinite()) {
            chips.add(new Chip(MediumBlessing.SHIELD, null));
        }
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && !speed.isInfinite() && speedWorld == client.world
                && Util.getMeasuringTimeMs() - speedGrantedAtMs <= SPEED_CHIP_WINDOW_MS) {
            chips.add(new Chip(MediumBlessing.SPEED, String.valueOf(TickTime.ceilSeconds(speed.getDuration()))));
        }
        return chips;
    }

    private static void drawChips(DrawContext context, TextRenderer renderer, List<Chip> chips, int x, int y) {
        int cursor = x;
        for (int i = 0; i < chips.size(); i++) {
            Chip chip = chips.get(i);
            if (i > 0) {
                cursor += MediumHudRules.CHIP_GAP;
            }
            int iconX = cursor;
            InventoryCardPaint.batch(context, () -> MediumPaint.blessingIconShadowed(context, iconX, y - 1,
                    chip.icon(), MediumHudRules.READY_COLOR, 1));
            cursor += MediumHudRules.CHIP_ICON;
            if (chip.label() != null) {
                cursor += MediumHudRules.CHIP_LABEL_GAP;
                context.drawTextWithShadow(renderer, chip.label(), cursor, y, MediumHudRules.READY_COLOR);
                cursor += renderer.getWidth(chip.label());
            }
        }
    }

    /**
     * The row's text: seconds, the price with the Wathe coin glyph, or the row's own key name; {@code measure} swaps
     * the seconds for {@link MediumHudRules.Line#measureValue()}.
     * 行文字：秒数、带 Wathe 金币字形的价格，或本行自己的按键名；{@code measure} 为 true 时秒数改用
     * {@link MediumHudRules.Line#measureValue()}。
     */
    private static Text text(MediumHudRules.Line line, boolean measure, Text keyName) {
        return switch (line.arg()) {
            case SECONDS -> Text.translatable(line.key(), measure ? line.measureValue() : line.value());
            case PRICE -> Text.translatable(line.key(), line.value() + "");
            case KEY -> Text.translatable(line.key(), keyName);
        };
    }
}
