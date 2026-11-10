package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.SubmitMediumHauntTargetC2SPacket;
import dev.doctor4t.wathe.game.GameFunctions;
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
 * The 招魂 target menu: every online round member, alive or dead, as avatar tiles in the Knight duel style (Wathe shop
 * slot frame + player head); already-bound targets stay visible but dimmed and inert. Pick one, then confirm — the
 * confirm is the single paid submit. The menu auto-closes once the player is no longer a live Medium. Everything is
 * re-checked server-side.
 * 招魂目标菜单：所有在线本局成员（无论生死）以骑士决斗同款头像格子列出（Wathe 商店边框 + 玩家头颅）；已绑定目标仍然
 * 显示但压暗且不可选。选中后确认——确认才是唯一的付费提交。玩家不再是存活的通灵师时界面自动关闭。一切由服务端重新校验。
 */
public final class MediumHauntScreen extends Screen {
    private static final int BUTTON = 30;
    private static final int APART = 36;
    private static final int MAX_PER_ROW = 6;

    private final List<UUID> targets;
    private final List<String> names;
    private final int price;
    private final List<MediumHauntPlayerButton> tiles = new ArrayList<>();
    private int selected = -1;
    private int selectedY;
    private boolean submitted;

    public MediumHauntScreen(List<UUID> targets, List<String> names, int price) {
        super(Text.translatable("screen.sparkwitch.medium.haunt.title"));
        this.targets = List.copyOf(targets);
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
        int gridBottom = top + (rows - 1) * APART + BUTTON;
        this.selectedY = gridBottom + 8;
        for (int i = 0; i < count; i++) {
            int index = i;
            UUID uuid = targets.get(i);
            String name = i < names.size() ? names.get(i) : "?";
            MediumHauntPlayerButton tile = new MediumHauntPlayerButton(
                    left + (i % perRow) * APART,
                    top + (i / perRow) * APART,
                    uuid,
                    name,
                    boundTarget(uuid),
                    button -> select(index)
            );
            tile.setSelected(i == selected);
            tiles.add(tile);
            addDrawableChild(tile);
        }
        int buttonY = Math.min(this.height - 28, this.selectedY + 12);
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.medium.haunt.cancel"), button -> close())
                .dimensions(this.width / 2 - 154, buttonY, 150, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.medium.haunt.confirm"), button -> submit())
                .dimensions(this.width / 2 + 4, buttonY, 150, 20)
                .build());
    }

    @Override
    public void tick() {
        if (this.client == null || this.client.player == null
                || !SparkWitchServerConnection.isConfirmedServer()
                || !MediumRules.isMedium(this.client.player)
                || !GameFunctions.isPlayerPlayingAndAlive(this.client.player)) {
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
                submit();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(int index) {
        if (index < 0 || index >= targets.size() || boundTarget(targets.get(index))) {
            return;
        }
        selected = index;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setSelected(i == index);
        }
    }

    private void submit() {
        if (submitted || selected < 0 || selected >= targets.size()) {
            return;
        }
        UUID target = targets.get(selected);
        if (boundTarget(target)) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new SubmitMediumHauntTargetC2SPacket(target));
        close();
    }

    private boolean boundTarget(UUID uuid) {
        if (this.client == null || this.client.player == null) {
            return false;
        }
        return MediumPlayerComponent.KEY.get(this.client.player).isBound(uuid);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14,
                0xFF000000 | MediumRules.COLOR);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.haunt.hint").formatted(Formatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.haunt.price", price),
                this.width / 2, 38, 0xFFFFFF);
        String chosen = selected >= 0 && selected < names.size()
                ? names.get(selected)
                : Text.translatable("screen.sparkwitch.medium.haunt.none").getString();
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.haunt.selected", chosen),
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
