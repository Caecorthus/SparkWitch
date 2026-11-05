package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.net.ConfirmNatsumeTargetC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 夏目安安's hypnosis menu: a grid of {@link NatsumeTargetButton} tiles (the Knight duel picker's look), select then
 * confirm. The server sends the candidates and re-validates the pick.
 * 夏目安安的催眠菜单：{@link NatsumeTargetButton} 头像网格（骑士决斗选择器的外观），先选择再确认。候选由服务端下发，
 * 所选目标由服务端重新校验。
 */
public final class NatsumeTargetScreen extends Screen {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 45;
    private static final int TITLE_COLOR = 0xFF000000 | NatsumeRules.COLOR;

    private final List<UUID> targets = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<NatsumeTargetButton> tiles = new ArrayList<>();
    private UUID selected;

    public NatsumeTargetScreen(List<UUID> targets, List<String> names) {
        super(Text.translatable("screen.sparkwitch.natsume.title"));
        this.targets.addAll(targets);
        this.names.addAll(names);
    }

    @Override
    protected void init() {
        // init() runs again on resize: rebuild the tile list with the widgets, keeping the selection.
        // 窗口缩放会再次调用 init()：与控件一起重建头像列表，并保留当前选择。
        tiles.clear();
        int rows = (targets.size() + COLUMNS - 1) / COLUMNS;
        int gridWidth = Math.min(targets.size(), COLUMNS) * CELL_WIDTH;
        int left = (this.width - gridWidth) / 2;
        int top = Math.max(34, (this.height - rows * CELL_HEIGHT) / 2 - 20);
        for (int i = 0; i < targets.size(); i++) {
            UUID uuid = targets.get(i);
            String name = i < names.size() ? names.get(i) : "?";
            int x = left + (i % COLUMNS) * CELL_WIDTH;
            int y = top + (i / COLUMNS) * CELL_HEIGHT;
            NatsumeTargetButton tile = new NatsumeTargetButton(x, y, uuid, name, button -> select(uuid));
            tile.setSelected(uuid.equals(selected));
            tiles.add(tile);
            this.addDrawableChild(tile);
        }
        int buttonY = Math.min(this.height - 40, top + rows * CELL_HEIGHT + 26);
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.sparkwitch.natsume.back"), b -> this.close())
                .dimensions(this.width / 2 - 154, buttonY, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.sparkwitch.natsume.confirm"), b -> {
            if (this.selected != null) {
                ClientPlayNetworking.send(new ConfirmNatsumeTargetC2SPacket(this.selected));
                this.close();
            }
        }).dimensions(this.width / 2 + 4, buttonY, 150, 20).build());
    }

    private void select(UUID uuid) {
        this.selected = uuid;
        for (NatsumeTargetButton tile : tiles) {
            tile.setSelected(tile.playerUuid().equals(uuid));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // One super.render: it draws the blurred background once, then the widgets; all text goes on top, unblurred.
        // 只调用一次 super.render：先绘制一次模糊背景，再绘制控件；所有文字画在最上层，不被模糊。
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, TITLE_COLOR);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.natsume.hint").formatted(Formatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
        // Owner 2026-11-03: names are not drawn under the tiles (tooltip only); the pick is named once here.
        // 所有者 2026-11-03：格子下方不画名字（只有 tooltip）；所选目标只在这里显示一次。
        String chosen = "—";
        if (selected != null) {
            int index = targets.indexOf(selected);
            if (index >= 0 && index < names.size()) {
                chosen = names.get(index);
            }
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.natsume.current", chosen), this.width / 2,
                this.height - 62, 0xFFFFFF);
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
