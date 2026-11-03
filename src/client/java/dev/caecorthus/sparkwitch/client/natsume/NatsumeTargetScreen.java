package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.client.criminalreasoner.CriminalReasonerPlayerButton;
import dev.caecorthus.sparkwitch.net.ConfirmNatsumeTargetC2SPacket;
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
 * 夏目安安's hypnosis menu, built from the criminal reasoner's avatar grid so both pickers feel the same.
 * 夏目安安的催眠菜单，直接复用犯罪推理学家的头像网格，两个选择器看起来一致。
 */
public final class NatsumeTargetScreen extends Screen {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 45;

    private final List<UUID> targets = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<CriminalReasonerPlayerButton> tiles = new ArrayList<>();
    private UUID selected;

    public NatsumeTargetScreen(List<UUID> targets, List<String> names) {
        super(Text.translatable("screen.sparkwitch.natsume.title"));
        this.targets.addAll(targets);
        this.names.addAll(names);
    }

    @Override
    protected void init() {
        int rows = (targets.size() + COLUMNS - 1) / COLUMNS;
        int gridWidth = Math.min(targets.size(), COLUMNS) * CELL_WIDTH;
        int left = (this.width - gridWidth) / 2;
        int top = Math.max(34, (this.height - rows * CELL_HEIGHT) / 2 - 20);
        for (int i = 0; i < targets.size(); i++) {
            UUID uuid = targets.get(i);
            String name = i < names.size() ? names.get(i) : "?";
            int x = left + (i % COLUMNS) * CELL_WIDTH;
            int y = top + (i / COLUMNS) * CELL_HEIGHT;
            CriminalReasonerPlayerButton tile = new CriminalReasonerPlayerButton(x, y, uuid, name, button -> {
                this.selected = uuid;
                for (CriminalReasonerPlayerButton other : tiles) {
                    other.setSelected(other.playerUuid().equals(uuid));
                }
            });
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFC5B4FF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.natsume.hint").formatted(Formatting.GRAY),
                this.width / 2, 26, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        // Owner 2026-11-03: names are not drawn — the tiles alone are enough and the labels were colliding.
        // 所有者 2026-11-03：不画名字——头像本身已经够用，而且名字互相重叠。
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
        super.render(context, mouseX, mouseY, delta);
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
