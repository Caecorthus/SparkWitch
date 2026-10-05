package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCameraRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-only CCTV overlay for the Seeker's remote view ({@code HudRenderCallback}): frame corners, blinking REC, mode
 * label, session clock, the horizontal distance from the body ("12 m"; there is no range limit, so no bar), the car's
 * big battery bar (visual warnings at ≤20% and ≤10%; the single warning SOUND is server-side and never played here)
 * and exit/switch hints.
 * Decorations follow the usual SparkWitch HUD gates (confirmed server, not F1, Wathe HUD on). The opaque SIGNAL LOST
 * panel (body blind or in darkness, NoellesRoles Gin immunity respected) is a gameplay mask, so it ignores F1 and
 * the Wathe HUD toggle. It reads only the local owner's own synced state and never renders in the witch skill panel.
 * In camera mode the label carries the viewed camera's zero-padded number ("CAM 02"), and while the strafe keys can
 * reach another camera a camera-switch hint with the keys and the "2/3" position sits one line above the exit hint.
 * 搜寻者遥控视角的纯客户端 CCTV 叠加层（{@code HudRenderCallback}）：四角框、闪烁 REC、模式标签、会话计时、
 * 与本体的水平距离（“12米”；不再有距离上限，因此没有距离条）、小车的大号电量条（≤20% 与 ≤10% 时视觉警告；
 * 唯一的警告音在服务端播放，这里绝不播放）以及退出/切换提示。
 * 装饰元素遵循 SparkWitch HUD 的常规门槛（已确认服务器、未按 F1、Wathe HUD 开启）。
 * 不透明的“信号丢失”面板（本体失明或处于黑暗，尊重 NoellesRoles 金酒免疫）属于玩法遮罩，因此不受 F1 与 Wathe HUD
 * 开关影响。只读取本地拥有者自己的同步状态，从不在魔女技能面板中渲染。
 * 摄像头模式下标签带有所看摄像头的补零编号（“摄像头 02”）；左右键还能切到其他摄像头时，退出提示上方一行显示带左右
 * 移动键与“2/3”位置的摄像头切换提示。
 */
public final class SeekerCctvOverlay {
    /** NoellesRoles Gin immunity, looked up by registry id only (optional dependency). / 仅按注册 id 查找的 NR 金酒免疫（可选依赖）。 */
    static final Identifier GIN_IMMUNITY_ID = Identifier.of("noellesroles", "gin_immunity");
    private static final int INSET = 8;
    private static final int CORNER = 18;
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
        if (SeekerRemoteViewClient.isConnecting()) {
            renderConnecting(context, client.textRenderer, mode, ticks);
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
            SeekerCarUseClient.renderHint(context, text, tickCounter, width, height, frame);
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

    /** Horizontal body-to-focus distance, the same measure as the console rows. / 本体到焦点的水平距离，与控制台状态行的度量一致。 */
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
        // Three lines; the last one ends on the battery bar's bottom edge, where the old range bar used to end.
        // 共三行；最后一行的底边与电量条底边对齐（即原距离条的结束位置）。
        int y = height - HOTBAR_CLEARANCE - 6 - text.fontHeight - line * 2;
        if (SeekerCctvRules.recVisible(ticks)) {
            context.drawTextWithShadow(text, Text.translatable("hud.sparkwitch.seeker.view.rec"), x, y,
                    SeekerCctvRules.REC_COLOR);
        }
        String clock = SeekerCctvRules.clock(ticks - sessionStartAge);
        context.drawTextWithShadow(text, clock, x + text.getWidth(Text.translatable("hud.sparkwitch.seeker.view.rec")) + 6,
                y, frame);
        y += line;
        int cameraLabel = SeekerCameraRules.label(SeekerClientState.cameras(), SeekerClientState.sessionFocusEntityId());
        Text label = mode == SeekerSessionMode.CAR ? Text.translatable("hud.sparkwitch.seeker.view.car")
                : Text.translatable("hud.sparkwitch.seeker.view.camera", SeekerCctvRules.cameraNumber(cameraLabel));
        context.drawTextWithShadow(text, label, x, y, frame);
        y += line;
        Entity focus = SeekerRemoteViewClient.focus();
        double distance = focus == null ? 0.0 : horizontalDistance(player, focus);
        context.drawTextWithShadow(text, Text.translatable("hud.sparkwitch.seeker.view.distance",
                MathHelper.floor(distance)), x, y, frame);
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
        renderCameraCycleHint(context, text, client, mode, width, y, frame);
        if (SeekerCctvRules.showsSwitchHint(SeekerClientState.carState(), SeekerClientState.cameraCount())) {
            Text change = Text.translatable("hud.sparkwitch.seeker.view.switch_hint",
                    SecondaryAbilityController.secondaryKeyText());
            context.drawCenteredTextWithShadow(text, change, width / 2, y + text.fontHeight + 3, frame);
        }
    }

    /**
     * One line above the exit hint while viewing a camera and the strafe keys can reach another one: the keys and the
     * "2/3" position by label among the cameras the client cycler could select (every synced camera this client does
     * not see dead, at any distance, plus the viewed one), from the synced cameras and session focus. The keys only
     * mirror the client cycler; the server validates every switch.
     * 观看摄像头且左右键还能切到其他摄像头时，在退出提示上方一行显示：左右移动键与按编号的“2/3”位置，只在客户端切换器
     * 可选的摄像头（本客户端未看到失效的每台同步摄像头，距离不限，加上正在观看的那台）中计算，数据来自同步的摄像头列表与
     * 会话焦点。按键提示只对应客户端切换器；每次切换都由服务端校验。
     */
    private static void renderCameraCycleHint(DrawContext context, TextRenderer text, MinecraftClient client,
                                              SeekerSessionMode mode, int width, int exitY, int frame) {
        int focusId = SeekerClientState.sessionFocusEntityId();
        List<SeekerState.Camera> ring = SeekerCameraRules.cycleRing(SeekerClientState.cameras(), focusId,
                SeekerCameraCycler.selectable(client.world));
        int count = ring.size();
        if (!SeekerCctvRules.showsCameraCycleHint(mode, count)) {
            return;
        }
        int position = SeekerCameraRules.position(ring, focusId);
        Text cycle = Text.translatable("hud.sparkwitch.seeker.view.camera_cycle_hint",
                client.options.leftKey.getBoundKeyLocalizedText(), client.options.rightKey.getBoundKeyLocalizedText(),
                position > 0 ? Integer.toString(position) : "?", count);
        context.drawCenteredTextWithShadow(text, cycle, width / 2, exitY - text.fontHeight - 3, frame);
    }

    private static void renderSignalLost(DrawContext context, TextRenderer text, long ticks) {
        renderPanel(context, text, ticks, Text.translatable("hud.sparkwitch.seeker.view.signal_lost"),
                SeekerCctvRules.CRITICAL_COLOR, null, true);
    }

    /**
     * CONNECTING mask while the session's focus or its surrounding terrain has not streamed in yet (unlimited range:
     * session start, an atomic switch to a far device, or a briefly missing focus). Opaque like SIGNAL LOST and also
     * shown under F1, because underneath sits the last view or the body whose chunks the server is unloading; the line
     * below names the device being reached, and the exit hint stays, since Shift still leaves.
     * 会话焦点或其周围地形尚未推送到达时的“正在连接”遮罩（无限距离：会话开始、原子切换到远处设备、或焦点短暂缺失）。与“信号丢失”一样不透明，
     * F1 下也显示，因为其下是上一画面或服务端正在卸载其区块的本体；下一行注明正在连接的设备，退出提示保留，因为 Shift 仍可退出。
     */
    private static void renderConnecting(DrawContext context, TextRenderer text, SeekerSessionMode mode, long ticks) {
        int frame = SeekerCctvRules.frameColor(mode);
        int cameraLabel = SeekerCameraRules.label(SeekerClientState.cameras(),
                SeekerClientState.sessionFocusEntityId());
        Text target = mode == SeekerSessionMode.CAR ? Text.translatable("hud.sparkwitch.seeker.view.car")
                : Text.translatable("hud.sparkwitch.seeker.view.camera", SeekerCctvRules.cameraNumber(cameraLabel));
        renderPanel(context, text, ticks, Text.translatable("hud.sparkwitch.seeker.view.connecting"), frame, target,
                true);
    }

    /**
     * Shared opaque CCTV card: dark fill, per-tick static, a blinking double-size title, an optional device line and
     * an optional exit hint. Also drawn by {@link SeekerBodyHold} (RETURNING) after the view ends.
     * 共用的不透明 CCTV 卡片：深色底、逐刻雪花、闪烁的双倍大小标题、可选的设备行与可选的退出提示。视角结束后
     * {@link SeekerBodyHold}（正在返回本体）也使用它。
     */
    static void renderPanel(DrawContext context, TextRenderer text, long ticks, Text title, int titleColor,
                            @Nullable Text subtitle, boolean exitHint) {
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
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0F, height / 2.0F - 12.0F, 0.0F);
        context.getMatrices().scale(2.0F, 2.0F, 1.0F);
        if (SeekerCctvRules.recVisible(ticks)) {
            context.drawCenteredTextWithShadow(text, title, 0, 0, titleColor);
        }
        context.getMatrices().pop();
        int y = height / 2 + 16;
        if (subtitle != null) {
            context.drawCenteredTextWithShadow(text, subtitle, width / 2, y, titleColor);
            y += text.fontHeight + 3;
        }
        if (exitHint) {
            Text exit = Text.translatable("hud.sparkwitch.seeker.view.exit_hint",
                    MinecraftClient.getInstance().options.sneakKey.getBoundKeyLocalizedText());
            context.drawCenteredTextWithShadow(text, exit, width / 2, y, SeekerCctvRules.CAMERA_FRAME_COLOR);
        }
    }
}
