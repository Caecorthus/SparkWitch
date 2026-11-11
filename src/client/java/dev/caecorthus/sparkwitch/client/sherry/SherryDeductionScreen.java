package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.ConfirmSherryDeductionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 推演 candidate panel: the living players the server found inside reach, as avatar tiles in the Medium 招魂 style.
 * Pick one, then confirm; the confirm is the single paid submit and nothing is predicted locally (the result arrives
 * as a server message and, on a killer, as her private outline). The panel auto-closes once the player is no longer an
 * active 橘雪莉. Everything is re-checked server-side. The background is drawn once, by vanilla {@code Screen.render}.
 * 推演候选面板：服务端在射程内找到的存活玩家，以通灵师招魂同款头像格子列出。选中后确认；确认才是唯一的付费提交，本地不做任何
 * 预判（结果以服务端消息返回，选中杀手时还会出现她私有的描边）。玩家不再是活动中的橘雪莉时界面自动关闭。一切由服务端重新校验。
 * 背景只由原版 {@code Screen.render} 绘制一次。
 */
public final class SherryDeductionScreen extends Screen {
    private static final int BUTTON = 30;
    private static final int APART = 36;
    private static final int MAX_PER_ROW = 6;

    private final List<UUID> targets;
    private final List<String> names;
    private final int price;
    private final List<SherryDeductionPlayerButton> tiles = new ArrayList<>();
    private int selected = -1;
    private int selectedY;
    private boolean submitted;
    private ButtonWidget confirmButton;

    public SherryDeductionScreen(List<UUID> targets, List<String> names, int price) {
        super(Text.translatable("screen.sparkwitch.sherry.deduction.title"));
        // The server bounds the roster; clamp again so a malformed list can never flood the panel.
        // 服务端已限制名单长度；此处再次截断，畸形名单也无法撑爆面板。
        this.targets = List.copyOf(targets.subList(0, Math.min(targets.size(), SherryRules.MAX_CANDIDATES)));
        this.names = List.copyOf(names);
        this.price = price;
    }

    @Override
    protected void init() {
        tiles.clear();
        int count = targets.size();
        int top = 66;
        // The "selected" line and the confirm row sit below the grid; widen the rows when the window is short so the
        // grid never runs into them.
        // 「已选择」行与确认行在网格下方；窗口较矮时增加每行人数，保证网格不会压到它们。
        int bottomReserve = 8 + 12 + 20 + 6;
        int rowsAllowed = Math.max(1, (this.height - top - bottomReserve) / APART);
        int minPerRow = (count + rowsAllowed - 1) / Math.max(1, rowsAllowed);
        int widthCap = Math.max(1, (this.width - 16) / APART);
        int perRow = Math.min(widthCap, Math.max(Math.min(MAX_PER_ROW, Math.max(1, count)), minPerRow));
        int rows = (count + perRow - 1) / perRow;
        int gridWidth = (perRow - 1) * APART + BUTTON;
        int left = (this.width - gridWidth) / 2;
        int gridBottom = top + Math.max(0, rows - 1) * APART + BUTTON;
        this.selectedY = gridBottom + 8;
        for (int i = 0; i < count; i++) {
            int index = i;
            UUID uuid = targets.get(i);
            SherryDeductionPlayerButton tile = new SherryDeductionPlayerButton(
                    left + (i % perRow) * APART,
                    top + (i / perRow) * APART,
                    uuid,
                    nameOf(i),
                    trackedTarget(uuid),
                    button -> select(index)
            );
            tile.setSelected(i == selected);
            tiles.add(tile);
            addDrawableChild(tile);
        }
        int buttonY = Math.min(this.height - 28, this.selectedY + 12);
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.sherry.deduction.cancel"), button -> close())
                .dimensions(this.width / 2 - 154, buttonY, 150, 20)
                .build());
        // Greyed out until a tile is picked, so the paid button never looks pressable with nothing selected.
        // 选中头像之前置灰，没有选择时付费按钮不会看起来可按。
        confirmButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.sherry.deduction.confirm"), button -> submit())
                .dimensions(this.width / 2 + 4, buttonY, 150, 20)
                .build());
        confirmButton.active = selected >= 0 && selected < targets.size();
    }

    @Override
    public void tick() {
        // The HUD's gate: confirmed server, raw role, ACTIVE round, alive, not spectating and not swallowed, so the
        // panel closes in every state in which the server would refuse the paid confirm anyway.
        // 与 HUD 相同的门槛：已确认服务器、真实职业、ACTIVE 回合、存活、非旁观、未被吞下；凡是服务端会拒绝付费确认的状态，
        // 界面都会自动关闭。
        if (this.client == null || !SherryClient.isActiveSherry(this.client.player)) {
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (submitted) {
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                // Enter on a keyboard-focused Cancel/Confirm button presses that button; only otherwise is it the
                // paid confirm. / 键盘焦点在「取消/确认」按钮上时回车按下该按钮；否则才是付费确认。
                if (getFocused() instanceof ButtonWidget focused
                        && !(focused instanceof SherryDeductionPlayerButton)) {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                submit();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(int index) {
        if (index < 0 || index >= targets.size()) {
            return;
        }
        selected = index;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setSelected(i == index);
        }
        if (confirmButton != null) {
            confirmButton.active = true;
        }
    }

    private void submit() {
        if (submitted || selected < 0 || selected >= targets.size()
                || !ClientPlayNetworking.canSend(ConfirmSherryDeductionC2SPacket.ID)) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new ConfirmSherryDeductionC2SPacket(targets.get(selected)));
        close();
    }

    private String nameOf(int index) {
        return index < names.size() ? names.get(index) : "?";
    }

    private boolean trackedTarget(UUID uuid) {
        if (this.client == null || this.client.player == null) {
            return false;
        }
        return SherryComponent.KEY.get(this.client.player).isTracked(uuid);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14,
                0xFF000000 | SherryRules.COLOR);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.sherry.deduction.hint").formatted(Formatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.sherry.deduction.price", price),
                this.width / 2, 38, 0xFFFFFF);
        String chosen = selected >= 0 && selected < targets.size()
                ? nameOf(selected)
                : Text.translatable("screen.sparkwitch.sherry.deduction.none").getString();
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.sherry.deduction.selected", chosen),
                this.width / 2, this.selectedY, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
