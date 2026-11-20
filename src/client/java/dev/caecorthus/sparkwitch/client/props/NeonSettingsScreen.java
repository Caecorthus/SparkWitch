package dev.caecorthus.sparkwitch.client.props;

import dev.caecorthus.sparkwitch.net.props.NeonSettingsC2SPayload;
import dev.caecorthus.sparkwitch.prop.NeonSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Blink settings for one neon column. The builder types how long the lamp should stay on and how long it should stay
 * off, both in seconds; there are no presets. "Off" keeps the column steady and lit.
 * <p>
 * 一根霓虹灯柱的闪烁设置。建造者自己输入灯亮多久、灭多久（都以秒为单位），不再有预设档位。「关闭」让灯柱常亮不闪。
 */
public class NeonSettingsScreen extends Screen {
    private static final int DEFAULT_ON = 20;
    private static final int DEFAULT_OFF = 20;

    private final BlockPos pos;
    private final int initialOn;
    private final int initialOff;
    private final boolean initialEnabled;

    private boolean enabled;
    private boolean random;
    private TextFieldWidget onField;
    private TextFieldWidget offField;

    public NeonSettingsScreen(BlockPos pos, int onTicks, int offTicks, boolean enabled, boolean random) {
        super(Text.translatable("screen.sparkwitch.neon_settings"));
        this.pos = pos;
        this.initialOn = onTicks > 0 ? onTicks : DEFAULT_ON;
        this.initialOff = offTicks > 0 ? offTicks : DEFAULT_OFF;
        this.initialEnabled = enabled;
        this.enabled = enabled;
        this.random = random;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int labelX = centerX - 100;
        int y = this.height / 2 - 46;

        this.onField = new TextFieldWidget(this.textRenderer, centerX, y, 80, 20, Text.translatable("screen.sparkwitch.neon.on"));
        this.onField.setMaxLength(6);
        this.onField.setTextPredicate(text -> text.isEmpty() || text.matches("\\d{0,6}"));
        this.onField.setText(Integer.toString(Math.max(1, Math.round(this.initialOn / 20.0F))));
        addDrawableChild(this.onField);

        this.offField = new TextFieldWidget(this.textRenderer, centerX, y + 26, 80, 20, Text.translatable("screen.sparkwitch.neon.off_seconds"));
        this.offField.setMaxLength(6);
        this.offField.setTextPredicate(text -> text.isEmpty() || text.matches("\\d{0,6}"));
        this.offField.setText(Integer.toString(Math.max(1, Math.round(this.initialOff / 20.0F))));
        addDrawableChild(this.offField);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.sparkwitch.neon.random", this.random ? "●" : "○"),
                button -> {
                    this.random = !this.random;
                    button.setMessage(Text.translatable("screen.sparkwitch.neon.random", this.random ? "●" : "○"));
                }
        ).dimensions(labelX, y + 78, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.sparkwitch.neon.toggle", this.enabled ? "●" : "○"),
                button -> {
                    this.enabled = !this.enabled;
                    button.setMessage(Text.translatable("screen.sparkwitch.neon.toggle", this.enabled ? "●" : "○"));
                }
        ).dimensions(labelX, y + 52, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                button -> {
                    int on = parseSeconds(this.onField.getText(), DEFAULT_ON);
                    int off = parseSeconds(this.offField.getText(), DEFAULT_OFF);
                    ClientPlayNetworking.send(new NeonSettingsC2SPayload(
                            this.pos, on * 20, off * 20, this.enabled, this.random));
                    this.close();
                }
        ).dimensions(centerX - 100, y + 110, 96, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                button -> this.close()
        ).dimensions(centerX + 4, y + 110, 96, 20).build());
    }

    private static int parseSeconds(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(600, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 78, 0xFFFFFF);
        int labelX = this.width / 2 - 100;
        int y = this.height / 2 - 46;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.sparkwitch.neon.on"), labelX, y + 6, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.sparkwitch.neon.off_seconds"), labelX, y + 32, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.sparkwitch.neon.hint"),
                labelX, y + 134, 0x9A9A9A);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
