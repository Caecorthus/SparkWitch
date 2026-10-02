package dev.caecorthus.sparkwitch.client.riftwalker.session;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Draws the in-gate HUD (plan §6.4, C2) in place of the hotbar / spectator menu: the ⬅️ ➡️ two-slot bar on Wathe's
 * hotbar sprite with the selection frame, greyed arrows when there is no other gate, the "#gate · n/m" label, the stay
 * countdown (red for the last 5 s) and a one-line key hint; plus the grey tint and vignette used when the post shader
 * cannot run. HUD pixels are drawn after the world post pass, so the bar stays in colour. Presentation only.
 * 在快捷栏/旁观者菜单的位置绘制门内 HUD（plan §6.4、C2）：基于 Wathe 快捷栏精灵的 ⬅️ ➡️ 两格栏与选中框、没有其他门时的灰色箭头、
 * 「#门号 · n/m」标签、停留倒计时（最后 5 秒变红）与一行按键提示；以及后处理着色器无法运行时使用的灰色叠层与暗角。HUD 在世界后处理
 * 之后绘制，因此箭头栏保持彩色。仅负责表现。
 */
public final class RiftSessionHud {
    private static final Identifier VIGNETTE_TEXTURE = Identifier.ofVanilla("textures/misc/vignette.png");

    private RiftSessionHud() {
    }

    /** The arrow bar and its labels; called instead of the hotbar or the spectator menu. / 箭头栏及其文字。 */
    public static void renderBar(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        RiftSessionClient.View view = RiftSessionClient.view();
        if (view == null) {
            return;
        }
        int barX = RiftSessionHudRules.barX(context.getScaledWindowWidth());
        int barY = RiftSessionHudRules.barY(context.getScaledWindowHeight());
        boolean enabled = RiftSessionHudRules.arrowsEnabled(view.ringSize());

        RenderSystem.enableBlend();
        // Left border and two slots, then the right end cap of the 182-px sprite. / 左边框与两格，再接 182 像素精灵的右端盖。
        int body = RiftSessionHudRules.BAR_WIDTH - 1;
        context.drawGuiTexture(RiftSessionHudRules.HOTBAR_SPRITE, RiftSessionHudRules.HOTBAR_SPRITE_WIDTH,
                RiftSessionHudRules.HOTBAR_SPRITE_HEIGHT, 0, 0, barX, barY, body, RiftSessionHudRules.BAR_HEIGHT);
        context.drawGuiTexture(RiftSessionHudRules.HOTBAR_SPRITE, RiftSessionHudRules.HOTBAR_SPRITE_WIDTH,
                RiftSessionHudRules.HOTBAR_SPRITE_HEIGHT, RiftSessionHudRules.HOTBAR_SPRITE_WIDTH - 1, 0, barX + body,
                barY, 1, RiftSessionHudRules.BAR_HEIGHT);
        for (int slot = 0; slot < RiftSessionInputRules.SLOT_COUNT; slot++) {
            context.drawTexture(RiftSessionHudRules.arrowTexture(slot, enabled),
                    RiftSessionHudRules.iconX(barX, slot), RiftSessionHudRules.iconY(barY), 0.0F, 0.0F,
                    RiftSessionHudRules.ICON_SIZE, RiftSessionHudRules.ICON_SIZE,
                    RiftSessionHudRules.ICON_SIZE, RiftSessionHudRules.ICON_SIZE);
        }
        context.drawGuiTexture(RiftSessionHudRules.HOTBAR_SELECTION_SPRITE,
                RiftSessionHudRules.selectionX(barX, view.selectedSlot()), RiftSessionHudRules.selectionY(barY),
                RiftSessionHudRules.SELECTION_WIDTH, RiftSessionHudRules.SELECTION_HEIGHT);
        RenderSystem.disableBlend();

        TextRenderer font = client.textRenderer;
        int labelY = RiftSessionHudRules.labelY(barY);
        Text gate = RiftSessionHudRules.showsRing(view.ringIndex(), view.ringSize())
                ? Text.translatable("hud.sparkwitch.riftwalker.gate_label", view.gateNumber(), view.ringIndex(),
                view.ringSize())
                : Text.translatable("hud.sparkwitch.riftwalker.gate_number", view.gateNumber());
        context.drawTextWithShadow(font, gate, barX - RiftSessionHudRules.LABEL_GAP - font.getWidth(gate), labelY,
                RiftSessionHudRules.TEXT_COLOR);
        if (RiftSessionHudRules.showsTimer(view.stayLimitTicks())) {
            int remaining = view.stayRemainingTicks();
            Text timer = Text.translatable("hud.sparkwitch.riftwalker.stay_seconds",
                    RiftSessionHudRules.seconds(remaining));
            context.drawTextWithShadow(font, timer,
                    barX + RiftSessionHudRules.BAR_WIDTH + RiftSessionHudRules.LABEL_GAP, labelY,
                    RiftSessionHudRules.timerColor(remaining));
        }

        GameOptions options = client.options;
        Text hint = enabled
                ? Text.translatable("hud.sparkwitch.riftwalker.hint", options.leftKey.getBoundKeyLocalizedText(),
                options.rightKey.getBoundKeyLocalizedText(), options.sneakKey.getBoundKeyLocalizedText())
                : Text.translatable("hud.sparkwitch.riftwalker.hint_alone", options.sneakKey.getBoundKeyLocalizedText());
        context.drawTextWithShadow(font, hint, context.getScaledWindowWidth() / 2 - font.getWidth(hint) / 2,
                RiftSessionHudRules.hintY(barY, font.fontHeight), RiftSessionHudRules.HINT_COLOR);
    }

    /**
     * Grey tint plus a darkening vignette over the world and under the HUD, used only when the post shader cannot run
     * (Iris shader pack, load failure), so "you are inside" stays obvious.
     * 世界之上、HUD 之下的灰色叠层与变暗暗角，仅在后处理着色器无法运行时使用（Iris 光影包、加载失败），使「身处门内」依然明显。
     */
    public static void renderFallback(DrawContext context) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float strength = RiftSessionHudRules.FALLBACK_VIGNETTE;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Vanilla vignette blend: darkens by the texture colour. / 原版暗角混合：按贴图颜色变暗。
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        context.setShaderColor(strength, strength, strength, 1.0F);
        context.drawTexture(VIGNETTE_TEXTURE, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        context.fill(0, 0, width, height, RiftSessionHudRules.FALLBACK_TINT);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
