package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Client-only CCTV overlay for the Seeker's remote view ({@code HudRenderCallback}): frame corners, blinking REC, mode
 * label, session clock, distance bar "12 / 32 m", the car's big battery bar (visual warnings at ≤20% and ≤10%; the
 * single warning SOUND is server-side and never played here), exit/switch hints and a low-render-distance warning.
 * Decorations follow the usual SparkWitch HUD gates (confirmed server, not F1, Wathe HUD on). The opaque SIGNAL LOST
 * panel (body blind or in darkness, NoellesRoles Gin immunity respected) is a gameplay mask, so it ignores F1 and
 * the Wathe HUD toggle. It reads only the local owner's own synced state and never renders in the witch skill panel.
 * 搜寻者遥控视角的纯客户端 CCTV 叠加层（{@code HudRenderCallback}）：四角框、闪烁 REC、模式标签、会话计时、
 * 距离条“12 / 32 米”、小车的大号电量条（≤20% 与 ≤10% 时视觉警告；唯一的警告音在服务端播放，这里绝不播放）、
 * 退出/切换提示与渲染距离过低警告。装饰元素遵循 SparkWitch HUD 的常规门槛（已确认服务器、未按 F1、Wathe HUD 开启）。
 * 不透明的“信号丢失”面板（本体失明或处于黑暗，尊重 NoellesRoles 金酒免疫）属于玩法遮罩，因此不受 F1 与 Wathe HUD
 * 开关影响。只读取本地拥有者自己的同步状态，从不在魔女技能面板中渲染。
 */
public final class SeekerCctvOverlay {
    /** NoellesRoles Gin immunity, looked up by registry id only (optional dependency). / 仅按注册 id 查找的 NR 金酒免疫（可选依赖）。 */
    static final Identifier GIN_IMMUNITY_ID = Identifier.of("noellesroles", "gin_immunity");
    private static final int INSET = 8;
    private static final int CORNER = 18;
    private static final int RANGE_BAR_WIDTH = 72;
    private static final int BATTERY_BAR_WIDTH = 92;
    private static final int BATTERY_BAR_HEIGHT = 9;
    private static final int HOTBAR_CLEARANCE = 44;
    private static boolean registered;
    private static int sessionId = Integer.MIN_VALUE;
    private static int sessionStartAge;

    private SeekerCctvOverlay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register(SeekerCctvOverlay::render);
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        SeekerSessionMode mode = SeekerRemoteViewClient.mode();
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null || client.world == null
                || mode == SeekerSessionMode.NONE) {
            return;
        }
        trackSession(player);
        long ticks = player.age;
        if (isSignalLost(player)) {
            renderSignalLost(context, client.textRenderer, ticks);
            return;
        }
        if (client.options.hudHidden || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        TextRenderer text = client.textRenderer;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int frame = SeekerCctvRules.frameColor(mode);
        renderCorners(context, width, height, frame);
        renderStatusBlock(context, text, player, mode, height, frame, ticks);
        if (mode == SeekerSessionMode.CAR) {
            renderBattery(context, text, width, height, ticks);
        }
        renderHints(context, text, client, mode, width, height, frame);
    }

    /**
     * Body blind or in darkness and not Gin-immune; shared with {@link SeekerViewFilter} so the shader blanks too.
     * 本体失明或处于黑暗且没有金酒免疫；与 {@link SeekerViewFilter} 共用，使着色器同样黑屏。
     */
    public static boolean isSignalLost(ClientPlayerEntity player) {
        boolean ginImmune = Registries.STATUS_EFFECT.getEntry(GIN_IMMUNITY_ID).map(player::hasStatusEffect).orElse(false);
        return SeekerCctvRules.signalLost(player.hasStatusEffect(StatusEffects.BLINDNESS),
                player.hasStatusEffect(StatusEffects.DARKNESS), ginImmune);
    }

    /** Horizontal body-to-focus distance, matching the server's horizontal radius clamp. / 本体到焦点的水平距离，与服务端水平半径钳制一致。 */
    public static double horizontalDistance(Entity body, Entity focus) {
        double dx = focus.getX() - body.getX();
        double dz = focus.getZ() - body.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * CCTV reticle drawn by {@code SeekerRemoteInGameHudMixin} instead of Wathe's crosshair while viewing.
     * 观看期间由 {@code SeekerRemoteInGameHudMixin} 绘制的 CCTV 准星，替代 Wathe 准星。
     */
    public static void renderCrosshair(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && isSignalLost(client.player)) {
            return;
        }
        int color = (SeekerCctvRules.frameColor(SeekerRemoteViewClient.mode()) & 0x00FFFFFF) | 0xC0000000;
        int x = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() / 2;
        context.fill(x - 7, y, x - 3, y + 1, color);
        context.fill(x + 4, y, x + 8, y + 1, color);
        context.fill(x, y - 7, x + 1, y - 3, color);
        context.fill(x, y + 4, x + 1, y + 8, color);
        context.fill(x, y, x + 1, y + 1, color);
    }

    private static void trackSession(ClientPlayerEntity player) {
        int current = SeekerClientState.sessionId();
        // A new session id, or a respawned/reconnected player whose age restarted, restarts the clock (no player
        // reference is kept, so an old world is never retained).
        // 新的会话 id，或重生/重连后 age 重新计数，都会重置计时（不持有玩家引用，避免旧世界无法释放）。
        if (current != sessionId || player.age < sessionStartAge) {
            sessionId = current;
            sessionStartAge = player.age;
        }
    }

    private static void renderCorners(DrawContext context, int width, int height, int color) {
        int left = INSET;
        int top = INSET;
        int right = width - INSET;
        int bottom = height - INSET;
        context.fill(left, top, left + CORNER, top + 1, color);
        context.fill(left, top, left + 1, top + CORNER, color);
        context.fill(right - CORNER, top, right, top + 1, color);
        context.fill(right - 1, top, right, top + CORNER, color);
        context.fill(left, bottom - 1, left + CORNER, bottom, color);
        context.fill(left, bottom - CORNER, left + 1, bottom, color);
        context.fill(right - CORNER, bottom - 1, right, bottom, color);
        context.fill(right - 1, bottom - CORNER, right, bottom, color);
    }

    private static void renderStatusBlock(DrawContext context, TextRenderer text, ClientPlayerEntity player,
                                          SeekerSessionMode mode, int height, int frame, long ticks) {
        int x = INSET + 6;
        int line = text.fontHeight + 3;
        int y = height - HOTBAR_CLEARANCE - line * 3 - 6;
        if (SeekerCctvRules.recVisible(ticks)) {
            context.drawTextWithShadow(text, Text.translatable("hud.sparkwitch.seeker.view.rec"), x, y,
                    SeekerCctvRules.REC_COLOR);
        }
        String clock = SeekerCctvRules.clock(ticks - sessionStartAge);
        context.drawTextWithShadow(text, clock, x + text.getWidth(Text.translatable("hud.sparkwitch.seeker.view.rec")) + 6,
                y, frame);
        y += line;
        Text label = Text.translatable(mode == SeekerSessionMode.CAR
                ? "hud.sparkwitch.seeker.view.car" : "hud.sparkwitch.seeker.view.camera");
        context.drawTextWithShadow(text, label, x, y, frame);
        y += line;
        int radius = SeekerClientState.effectiveRadius();
        Entity focus = SeekerRemoteViewClient.focus();
        double distance = focus == null ? 0.0 : horizontalDistance(player, focus);
        double fraction = SeekerCctvRules.rangeFraction(distance, radius);
        Text range = Text.translatable("hud.sparkwitch.seeker.view.range", MathHelper.floor(distance), radius);
        context.drawTextWithShadow(text, range, x, y, frame);
        int barY = y + text.fontHeight + 1;
        context.fill(x, barY, x + RANGE_BAR_WIDTH, barY + 3, 0x80000000);
        context.fill(x, barY, x + (int) Math.round(RANGE_BAR_WIDTH * fraction), barY + 3,
                SeekerCctvRules.rangeBarColor(fraction));
    }

    private static void renderBattery(DrawContext context, TextRenderer text, int width, int height, long ticks) {
        int battery = SeekerClientState.carBattery();
        int color = SeekerCctvRules.batteryColor(battery);
        int x = width - INSET - 6 - BATTERY_BAR_WIDTH;
        int y = height - HOTBAR_CLEARANCE - BATTERY_BAR_HEIGHT - 6;
        context.drawTextWithShadow(text, Text.translatable("hud.sparkwitch.seeker.view.battery", battery), x,
                y - text.fontHeight - 2, color);
        // Frame + terminal nub. / 外框与电池正极凸起。
        context.fill(x - 1, y - 1, x + BATTERY_BAR_WIDTH + 1, y + BATTERY_BAR_HEIGHT + 1, 0xC0000000);
        context.fill(x + BATTERY_BAR_WIDTH + 1, y + 2, x + BATTERY_BAR_WIDTH + 3, y + BATTERY_BAR_HEIGHT - 2,
                0xC0000000);
        if (SeekerCctvRules.batteryVisible(battery, ticks)) {
            int segments = SeekerCctvRules.batterySegments(battery);
            int segmentWidth = BATTERY_BAR_WIDTH / SeekerCctvRules.BATTERY_SEGMENTS;
            for (int i = 0; i < segments; i++) {
                int left = x + i * segmentWidth + 1;
                context.fill(left, y + 1, left + segmentWidth - 2, y + BATTERY_BAR_HEIGHT - 1, color);
            }
        }
        if (SeekerRules.isBatteryWarning(battery)) {
            Text warning = Text.translatable(SeekerRules.isBatteryCritical(battery)
                    ? "hud.sparkwitch.seeker.battery.critical" : "hud.sparkwitch.seeker.battery.low");
            context.drawTextWithShadow(text, warning, x + BATTERY_BAR_WIDTH - text.getWidth(warning),
                    y - text.fontHeight * 2 - 4, color);
        }
    }

    private static void renderHints(DrawContext context, TextRenderer text, MinecraftClient client,
                                    SeekerSessionMode mode, int width, int height, int frame) {
        int y = height - HOTBAR_CLEARANCE - text.fontHeight - 14;
        Text exit = Text.translatable("hud.sparkwitch.seeker.view.exit_hint", client.options.sneakKey.getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(text, exit, width / 2, y, frame);
        if (SeekerCctvRules.showsSwitchHint(SeekerClientState.carState(), SeekerClientState.cameraEntityId())) {
            Text change = Text.translatable("hud.sparkwitch.seeker.view.switch_hint",
                    SecondaryAbilityController.secondaryKeyText());
            context.drawCenteredTextWithShadow(text, change, width / 2, y + text.fontHeight + 3, frame);
        }
        int radius = SeekerClientState.effectiveRadius();
        if (SeekerCctvRules.showsLowRenderDistance(mode, radius)) {
            context.drawCenteredTextWithShadow(text,
                    Text.translatable("hud.sparkwitch.seeker.view.low_render_distance", radius), width / 2,
                    INSET + 22, SeekerCctvRules.WARNING_COLOR);
        }
    }

    private static void renderSignalLost(DrawContext context, TextRenderer text, long ticks) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        context.fill(0, 0, width, height, SeekerCctvRules.SIGNAL_LOST_BACKGROUND);
        // Deterministic static per tick: no per-frame allocation-heavy noise. / 每 tick 确定性的雪花噪声。
        Random random = Random.create(ticks * 31L + 7L);
        for (int i = 0; i < 48; i++) {
            int y = random.nextInt(Math.max(1, height));
            int x = random.nextInt(Math.max(1, width));
            int length = 8 + random.nextInt(Math.max(1, width / 3));
            int grey = 0x30 + random.nextInt(0x50);
            context.fill(x, y, Math.min(width, x + length), y + 1, 0x60000000 | grey << 16 | grey << 8 | grey);
        }
        Text lost = Text.translatable("hud.sparkwitch.seeker.view.signal_lost");
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0F, height / 2.0F - 12.0F, 0.0F);
        context.getMatrices().scale(2.0F, 2.0F, 1.0F);
        if (SeekerCctvRules.recVisible(ticks)) {
            context.drawCenteredTextWithShadow(text, lost, 0, 0, SeekerCctvRules.CRITICAL_COLOR);
        }
        context.getMatrices().pop();
        Text exit = Text.translatable("hud.sparkwitch.seeker.view.exit_hint",
                MinecraftClient.getInstance().options.sneakKey.getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(text, exit, width / 2, height / 2 + 16, SeekerCctvRules.CAMERA_FRAME_COLOR);
    }
}
