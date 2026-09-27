package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.console.SeekerConsoleDevices;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarRecallC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteOpenC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Role-owned Seeker Console (keys {@code screen.sparkwitch.seeker_console.*}; never the witch skill panel). Client
 * presentation only: status rows (car, battery, camera, mark), "Control car" and "View camera" (send
 * {@code seeker_remote_open} and close; the server opens the possession session and the remote view takes over),
 * "Recall car" (remote, after a confirm step; {@code seeker_car_recall}, 180 s), and "Police network" (only for the
 * SparkStrength tablet: one-shot bypass and vanilla re-use so SparkStrength opens its own tablet). Every enablement is a
 * client prediction; the server validates each packet. The console never pauses and closes itself when the device
 * leaves the inventory, the role changes, the player dies, or a session starts.
 * 职业自有的搜寻者控制台（键名 {@code screen.sparkwitch.seeker_console.*}；绝不使用魔女技能面板）。仅客户端展示：
 * 状态行（小车、电量、摄像头、标记）；“操控小车”“查看摄像头”（发送 {@code seeker_remote_open} 并关闭，
 * 由服务端开启附身会话、遥控视角接管）；“回收小车”（远程，二次确认后发送 {@code seeker_car_recall}，180 秒）；
 * “警察网络”（仅限 SparkStrength 平板：一次性旁路后重发原版使用，由 SparkStrength 打开自己的平板）。
 * 所有启用状态都只是客户端预测，服务端校验每个数据包。控制台不暂停游戏，并在设备离开背包、职业变化、
 * 死亡或会话开始时自动关闭。
 */
public class SeekerConsoleScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 260;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BORDER_COLOR = 0xFF000000 | SeekerRules.COLOR;
    private static final int PANEL_COLOR = 0xF0181818;
    private static final int TEXT_COLOR = 0xDDDDDD;
    private static final int HINT_COLOR = 0xAAAAAA;
    private static final int WARNING_COLOR = 0xFFB02E;
    private static final int CRITICAL_COLOR = 0xFF4D4D;

    private final Hand hand;
    private boolean confirmingRecall;
    private boolean sent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int hintY;
    /** Layout-affecting state captured at the last init. / 上次 init 时记录的影响布局的状态。 */
    private int layoutKey;
    private List<OrderedText> confirmLines = List.of();
    @Nullable
    private ButtonWidget driveButton;
    @Nullable
    private ButtonWidget cameraButton;
    @Nullable
    private ButtonWidget recallButton;
    @Nullable
    private ButtonWidget networkButton;
    @Nullable
    private ButtonWidget confirmButton;

    public SeekerConsoleScreen(Hand hand) {
        super(Text.translatable("screen.sparkwitch.seeker_console.title"));
        this.hand = hand;
    }

    public Hand hand() {
        return hand;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        driveButton = null;
        cameraButton = null;
        recallButton = null;
        networkButton = null;
        confirmButton = null;
        layoutKey = currentLayoutKey();
        panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, width - 16));
        int innerWidth = Math.max(1, panelWidth - PADDING * 2);
        int statusRows = statusRowCount();
        int statusHeight = statusRows * ROW_HEIGHT;
        int contentHeight;
        if (confirmingRecall) {
            confirmLines = textRenderer.wrapLines(
                    Text.translatable("screen.sparkwitch.seeker_console.recall_confirm"), innerWidth);
            contentHeight = confirmLines.size() * (textRenderer.fontHeight + 2) + BUTTON_GAP + BUTTON_HEIGHT;
        } else {
            int buttons = 4 + (networkVisible() ? 1 : 0);
            contentHeight = buttons * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP
                    + (hotbarHintVisible() ? ROW_HEIGHT : 0);
        }
        panelHeight = PADDING + ROW_HEIGHT + 6 + statusHeight + 6 + contentHeight + PADDING;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(0, (height - panelHeight) / 2);
        int x = panelX + PADDING;
        int y = panelY + PADDING + ROW_HEIGHT + 6 + statusHeight + 6;
        if (confirmingRecall) {
            int buttonY = y + confirmLines.size() * (textRenderer.fontHeight + 2) + BUTTON_GAP;
            int half = Math.max(1, (innerWidth - BUTTON_GAP) / 2);
            confirmButton = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("screen.sparkwitch.seeker_console.confirm"), button -> confirmRecall())
                    .dimensions(x, buttonY, half, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("screen.sparkwitch.seeker_console.cancel"), button -> setConfirming(false))
                    .dimensions(x + innerWidth - half, buttonY, half, BUTTON_HEIGHT).build());
        } else {
            driveButton = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("screen.sparkwitch.seeker_console.drive_car"),
                    button -> openRemote(SeekerSessionMode.CAR))
                    .dimensions(x, y, innerWidth, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
            cameraButton = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("screen.sparkwitch.seeker_console.view_camera"),
                    button -> openRemote(SeekerSessionMode.CAMERA))
                    .dimensions(x, y, innerWidth, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
            recallButton = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("screen.sparkwitch.seeker_console.recall_car"), button -> setConfirming(true))
                    .dimensions(x, y, innerWidth, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + BUTTON_GAP;
            if (networkVisible()) {
                networkButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.seeker_console.network"), button -> openPoliceNetwork())
                        .dimensions(x, y, innerWidth, BUTTON_HEIGHT).build());
                y += BUTTON_HEIGHT + BUTTON_GAP;
            }
            if (hotbarHintVisible()) {
                hintY = y;
                y += ROW_HEIGHT;
            }
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.sparkwitch.seeker_console.close"),
                    button -> close()).dimensions(x, y, innerWidth, BUTTON_HEIGHT).build());
        }
        refreshButtons();
    }

    @Override
    public void tick() {
        if (client == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        boolean live = SparkWitchServerConnection.isConfirmedServer() && SeekerConsoleOpener.isLiveSeeker(player);
        if (SeekerConsoleRules.shouldAutoClose(live, SeekerConsoleDevices.hasConsoleDevice(player),
                SeekerConsoleOpener.isInSession())) {
            close();
            return;
        }
        if (confirmingRecall && SeekerClientState.carState() != SeekerCarState.DEPLOYED) {
            confirmingRecall = false;
        }
        if (currentLayoutKey() != layoutKey) {
            clearAndInit();
            return;
        }
        refreshButtons();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        int right = panelX + panelWidth;
        context.fill(panelX - 1, panelY - 1, right + 1, panelY + panelHeight + 1, BORDER_COLOR);
        context.fill(panelX, panelY, right, panelY + panelHeight, PANEL_COLOR);
        int innerWidth = Math.max(1, panelWidth - PADDING * 2);
        context.drawCenteredTextWithShadow(textRenderer, fit(title, innerWidth),
                panelX + panelWidth / 2, panelY + PADDING, SeekerRules.COLOR);
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return;
        }
        int y = panelY + PADDING + ROW_HEIGHT + 6;
        for (StatusRow row : statusRows(player)) {
            context.drawTextWithShadow(textRenderer, fit(row.text(), innerWidth),
                    panelX + PADDING, y, row.color());
            y += ROW_HEIGHT;
        }
        y += 6;
        if (confirmingRecall) {
            for (OrderedText line : confirmLines) {
                context.drawTextWithShadow(textRenderer, line, panelX + PADDING, y, WARNING_COLOR);
                y += textRenderer.fontHeight + 2;
            }
        } else if (hotbarHintVisible()) {
            context.drawTextWithShadow(textRenderer,
                    fit(Text.translatable("screen.sparkwitch.seeker_console.hint.hotbar"), innerWidth),
                    panelX + PADDING, hintY + 1, HINT_COLOR);
        }
    }

    // ---- Actions / 动作 ----

    private void openRemote(SeekerSessionMode mode) {
        if (sent || !ClientPlayNetworking.canSend(SeekerRemoteOpenC2SPacket.ID)) {
            return;
        }
        sent = true;
        ClientPlayNetworking.send(SeekerRemoteOpenC2SPacket.of(mode));
        close();
    }

    private void confirmRecall() {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (sent || player == null || recallAvailability() != SeekerConsoleRules.Availability.AVAILABLE
                || !ClientPlayNetworking.canSend(SeekerCarRecallC2SPacket.ID)) {
            return;
        }
        sent = true;
        ClientPlayNetworking.send(new SeekerCarRecallC2SPacket());
        close();
    }

    /**
     * Re-issues the vanilla use once so SparkStrength opens its own tablet; our opener passes because of the bypass.
     * 重发一次原版使用，由 SparkStrength 打开自己的平板；因旁路标记，我们的拦截器会放行。
     */
    private void openPoliceNetwork() {
        if (client == null || client.player == null || client.interactionManager == null || !networkVisible()) {
            return;
        }
        ClientPlayerEntity player = client.player;
        close();
        SeekerConsoleOpener.bypassOnce(hand);
        client.interactionManager.interactItem(player, hand);
    }

    private void setConfirming(boolean confirming) {
        if (confirming && recallAvailability() != SeekerConsoleRules.Availability.AVAILABLE) {
            return;
        }
        confirmingRecall = confirming;
        clearAndInit();
    }

    // ---- State / 状态 ----

    private void refreshButtons() {
        if (driveButton != null) {
            applyAvailability(driveButton, "screen.sparkwitch.seeker_console.drive_car", carAvailability());
        }
        if (cameraButton != null) {
            applyAvailability(cameraButton, "screen.sparkwitch.seeker_console.view_camera", cameraAvailability());
        }
        if (recallButton != null) {
            applyAvailability(recallButton, "screen.sparkwitch.seeker_console.recall_car", recallAvailability());
        }
        if (confirmButton != null) {
            confirmButton.active = !sent && recallAvailability() == SeekerConsoleRules.Availability.AVAILABLE;
        }
        if (networkButton != null) {
            networkButton.active = networkVisible();
        }
    }

    private void applyAvailability(ButtonWidget button, String key, SeekerConsoleRules.Availability availability) {
        button.active = !sent && availability == SeekerConsoleRules.Availability.AVAILABLE;
        MutableText label = Text.translatable(key);
        if (availability == SeekerConsoleRules.Availability.OUT_OF_RANGE) {
            label.append(" (").append(Text.translatable("screen.sparkwitch.seeker_console.out_of_range")).append(")");
        } else if (availability == SeekerConsoleRules.Availability.UNAVAILABLE) {
            label.append(" (").append(Text.translatable("screen.sparkwitch.seeker_console.unavailable")).append(")");
        }
        button.setMessage(label);
    }

    private SeekerConsoleRules.Availability carAvailability() {
        ClientPlayerEntity player = client == null ? null : client.player;
        SeekerDeviceEntity car = resolve(SeekerClientState.carEntityId(), SeekerCarEntity.class);
        return SeekerConsoleRules.carAvailability(SeekerClientState.carState(), car != null,
                horizontalDistanceSquared(player, car), radius(SeekerSessionMode.CAR), blocked(player));
    }

    private SeekerConsoleRules.Availability cameraAvailability() {
        ClientPlayerEntity player = client == null ? null : client.player;
        SeekerDeviceEntity camera = resolve(SeekerClientState.cameraEntityId(), SeekerCameraEntity.class);
        return SeekerConsoleRules.cameraAvailability(SeekerClientState.cameraEntityId() >= 0, camera != null,
                horizontalDistanceSquared(player, camera), radius(SeekerSessionMode.CAMERA), blocked(player));
    }

    private SeekerConsoleRules.Availability recallAvailability() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return SeekerConsoleRules.recallAvailability(SeekerClientState.carState(), blocked(player));
    }

    /** A stunned body cannot use the console (server: {@code remote.denied.stunned}). / 眩晕时不能使用控制台。 */
    private static boolean blocked(@Nullable ClientPlayerEntity player) {
        return player == null || SeekerControlExpertBridge.isStunned(player);
    }

    /**
     * Client estimate of the server's effective radius: the mode's maximum clamped to the negotiated view distance.
     * 服务端有效半径的客户端估计：模式上限，按协商后的视距钳制。
     */
    private int radius(SeekerSessionMode mode) {
        int viewDistance = client == null ? 0 : client.options.getClampedViewDistance();
        return SeekerRules.effectiveRadius(SeekerRules.maxRadius(mode), viewDistance);
    }

    private boolean networkVisible() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player != null && SeekerConsoleRules.networkVisible(
                SparkStrengthTabletCompat.isTablet(player.getStackInHand(hand)));
    }

    private boolean hotbarHintVisible() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player != null && SeekerConsoleRules.hotbarHintVisible(
                SparkStrengthTabletCompat.isTablet(player.getStackInHand(hand)), hand == Hand.OFF_HAND);
    }

    private int statusRowCount() {
        return 3 + (SeekerConsoleRules.batteryVisible(SeekerClientState.carState()) ? 1 : 0);
    }

    private int currentLayoutKey() {
        return (confirmingRecall ? 1 : 0) | (networkVisible() ? 2 : 0) | (hotbarHintVisible() ? 4 : 0)
                | (statusRowCount() << 3);
    }

    private List<StatusRow> statusRows(ClientPlayerEntity player) {
        List<StatusRow> rows = new ArrayList<>(4);
        SeekerCarState carState = SeekerClientState.carState();
        int cooldown = SeekerCooldowns.remainingTicks(player);
        SeekerDeviceEntity car = resolve(SeekerClientState.carEntityId(), SeekerCarEntity.class);
        Text carValue = switch (SeekerConsoleRules.carStatus(carState, cooldown)) {
            case NONE -> Text.translatable("screen.sparkwitch.seeker_console.car.none");
            case READY -> Text.translatable("screen.sparkwitch.seeker_console.car.ready");
            case COOLDOWN -> Text.translatable("screen.sparkwitch.seeker_console.car.cooldown",
                    SeekerConsoleRules.secondsCeil(cooldown),
                    Text.translatable(SeekerClientState.cooldownReason().translationKey()));
            case DEPLOYED -> Text.translatable("screen.sparkwitch.seeker_console.car.deployed",
                    distanceText(player, car));
            case SWALLOWED -> Text.translatable("screen.sparkwitch.seeker_console.car.swallowed");
        };
        rows.add(new StatusRow(labelled("screen.sparkwitch.seeker_console.car", carValue), TEXT_COLOR));
        if (SeekerConsoleRules.batteryVisible(carState)) {
            int battery = SeekerClientState.carBattery();
            int color = SeekerRules.isBatteryCritical(battery) ? CRITICAL_COLOR
                    : SeekerRules.isBatteryWarning(battery) ? WARNING_COLOR : TEXT_COLOR;
            rows.add(new StatusRow(Text.translatable("screen.sparkwitch.seeker_console.battery", battery), color));
        }
        boolean cameraPlaced = SeekerClientState.cameraEntityId() >= 0;
        Text cameraValue = cameraPlaced
                ? Text.translatable("screen.sparkwitch.seeker_console.camera.placed", distanceText(player,
                        resolve(SeekerClientState.cameraEntityId(), SeekerCameraEntity.class)))
                : Text.translatable("screen.sparkwitch.seeker_console.camera.none");
        rows.add(new StatusRow(labelled("screen.sparkwitch.seeker_console.camera", cameraValue), TEXT_COLOR));
        int markTicks = SeekerClientState.markRemainingTicks();
        rows.add(SeekerClientState.markTarget() != null && markTicks > 0
                ? new StatusRow(Text.translatable("screen.sparkwitch.seeker_console.mark",
                        SeekerConsoleRules.secondsCeil(markTicks)), CRITICAL_COLOR)
                : new StatusRow(Text.translatable("screen.sparkwitch.seeker_console.mark.none"), HINT_COLOR));
        return rows;
    }

    private OrderedText fit(Text text, int maxWidth) {
        return Language.getInstance().reorder(textRenderer.trimToWidth(text, maxWidth));
    }

    private static Text labelled(String labelKey, Text value) {
        return Text.translatable(labelKey).append(": ").append(value);
    }

    /**
     * Horizontal distance, the same measure as the range check, so the row never contradicts the button suffix.
     * 水平距离，与范围判定一致，状态行不会与按钮后缀矛盾。
     */
    private static String distanceText(ClientPlayerEntity player, @Nullable Entity device) {
        double squared = horizontalDistanceSquared(player, device);
        return squared < 0.0 ? "?" : Long.toString(Math.round(Math.sqrt(squared)));
    }

    private static double horizontalDistanceSquared(@Nullable ClientPlayerEntity player, @Nullable Entity device) {
        if (player == null || device == null) {
            return -1.0;
        }
        double dx = device.getX() - player.getX();
        double dz = device.getZ() - player.getZ();
        return dx * dx + dz * dz;
    }

    @Nullable
    private SeekerDeviceEntity resolve(int entityId, Class<? extends SeekerDeviceEntity> type) {
        if (entityId < 0 || client == null || client.world == null) {
            return null;
        }
        Entity entity = client.world.getEntityById(entityId);
        return type.isInstance(entity) && entity.isAlive() ? type.cast(entity) : null;
    }

    private record StatusRow(Text text, int color) {
    }
}
