package dev.caecorthus.sparkwitch.client.blind.gate;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The Blind's crosshair (C6): Wathe's plain 3x3 reticle, drawn exactly like Wathe's no-target branch, and nothing
 * else, so no gun/knife/bat target pip, swapped target reticle or attack glyph from any mod can flag a player in
 * line. Item cooldowns stay on the hotbar.
 * 盲人的准星（C6）：仅绘制 Wathe 的普通 3x3 准星（与 Wathe 无目标分支的绘制方式完全一致），因此任何模组的枪/刀/棒
 * 目标点、替换后的目标准星或攻击图标都无法标出视线上的玩家。物品冷却仍显示在快捷栏上。
 */
public final class BlindCrosshair {
    /** Wathe's {@code CrosshairRenderer.CROSSHAIR} sprite. / Wathe 的普通准星贴图。 */
    public static final Identifier PLAIN_CROSSHAIR = Identifier.of("wathe", "hud/crosshair");

    private BlindCrosshair() {
    }

    public static void render(MinecraftClient client, DrawContext context) {
        if (!client.options.getPerspective().isFirstPerson()) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f - 1.5f,
                context.getScaledWindowHeight() / 2f - 1.5f, 0);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO);
        context.drawGuiTexture(PLAIN_CROSSHAIR, 0, 0, 3, 3);
        context.getMatrices().pop();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
