package dev.caecorthus.sparkwitch.client.bear.helmet;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.bear.helmet.RysTVisorOverlayLayout.Window;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisor;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.VisorCrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * D6: the local wearer's view through the lowered Rys-T visor, drawn at {@code InGameHud.render} HEAD
 * ({@code client/mixin/bear/RysTVisorHudMixin}): under every HUD layer, and still drawn under F1 (only the layers check
 * {@code hudHidden}), so F1 cannot clear the view; the Holy Flash mask (render TAIL) stays on top. Shown only while the
 * local player wears a Rys-T with the visor down, looks through its own eyes in first person and is not a spectator;
 * hidden in the Blind view, the Seeker remote view and any foreign camera (spectated entity, the Black Raven crow,
 * the Bomber drone). Placeholder art until the art package delivers; texture paths are stable. Presentation only: the
 * visor and its cracks come from the server-synced head stack.
 * D6：本地佩戴者透过放下的 Rys-T 面罩看到的画面，在 {@code InGameHud.render} HEAD 绘制
 * （{@code client/mixin/bear/RysTVisorHudMixin}）：位于所有 HUD 层之下，F1 下仍会绘制（只有各层检查 {@code hudHidden}），因此
 * F1 无法去掉遮挡；圣光弹遮罩（render TAIL）保持在其上。仅当本地玩家戴着面罩放下的 Rys-T、以自身第一人称视角观看且不是旁观者时
 * 显示；在盲人视图、搜寻者遥控视角及任何外部镜头（被观察实体、黑羽鸦乌鸦、轰炸机无人机）中隐藏。美术包交付前使用占位美术；贴图
 * 路径保持稳定。仅为展示：面罩与裂纹来自服务端同步的头部物品。
 */
public final class RysTVisorOverlay {
    public static final Identifier OVERLAY_TEXTURE = SparkWitch.id("textures/gui/bear/visor_overlay.png");
    public static final Identifier CRACKS_TEXTURE = SparkWitch.id("textures/gui/bear/visor_cracks.png");
    /** Vanilla's misc-overlay depth (pumpkin blur). / 原版杂项遮罩深度（南瓜模糊）。 */
    private static final int OVERLAY_Z = -90;

    private RysTVisorOverlay() {
    }

    /**
     * Pure gate behind {@link #render}. / {@link #render} 背后的纯门槛。
     */
    public static boolean shows(boolean wearsRysT, boolean visorDown, boolean spectator, boolean cameraIsPlayer,
                                boolean firstPerson, boolean seekerView, boolean blindView) {
        return wearsRysT && visorDown && !spectator && cameraIsPlayer && firstPerson && !seekerView && !blindView;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // Cheapest first: almost nobody wears the helmet. / 先做最便宜的判断：几乎没人戴着头盔。
        RysTVisor visor = RysTHelmetItem.wornVisor(player);
        if (visor == null || !visor.down()) {
            return;
        }
        if (!shows(true, true, player.isSpectator(), client.getCameraEntity() == player,
                client.options.getPerspective().isFirstPerson(), SeekerRemoteViewClient.isActive(),
                BlindView.isActive(client))) {
            return;
        }
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        // Immediate-mode textured draws like vanilla's pumpkin overlay (never batched fills, which would flush above
        // later layers), so every HUD layer after this lands on top. / 与原版南瓜遮罩一样使用立即模式贴图绘制（不用批处理
        // 填充，否则会在之后的层之上才刷新），因此之后的所有 HUD 层都画在其上。
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // Cracks first, then the overlay over them: the frame's soft edge fades a crack out where it leaves the glass.
        // 先画裂纹再盖上遮罩：裂纹离开玻璃处由面框的柔和边缘渐隐。
        if (!visor.cracks().isEmpty()) {
            drawCracks(context, visor, RysTVisorOverlayLayout.window(width, height),
                    RysTVisorOverlayLayout.decalSize(height), width, height);
        }
        context.drawTexture(OVERLAY_TEXTURE, 0, 0, OVERLAY_Z, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void drawCracks(DrawContext context, RysTVisor visor, Window window, int size, int width,
                                   int height) {
        if (window.width() <= 0 || window.height() <= 0) {
            return;
        }
        int half = size / 2;
        int cell = RysTVisorOverlayLayout.ATLAS_CELL;
        int atlasWidth = cell * RysTVisorOverlayLayout.ATLAS_COLUMNS;
        int atlasHeight = cell * RysTVisorOverlayLayout.ATLAS_ROWS;
        Window clip = RysTVisorOverlayLayout.crackClip(window, width, height);
        context.enableScissor(clip.x0(), clip.y0(), clip.x1(), clip.y1());
        MatrixStack matrices = context.getMatrices();
        for (VisorCrack crack : visor.cracks()) {
            matrices.push();
            matrices.translate(RysTVisorOverlayLayout.crackX(window, crack.u()),
                    RysTVisorOverlayLayout.crackY(window, crack.v()), 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(crack.angle()));
            context.drawTexture(CRACKS_TEXTURE, -half, -half, size, size,
                    RysTVisorOverlayLayout.cellU(crack.style()), RysTVisorOverlayLayout.cellV(crack.style()),
                    cell, cell, atlasWidth, atlasHeight);
            matrices.pop();
        }
        context.disableScissor();
    }
}
