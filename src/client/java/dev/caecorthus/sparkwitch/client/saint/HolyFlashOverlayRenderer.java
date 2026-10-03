package dev.caecorthus.sparkwitch.client.saint;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.saint.HolyFlashOverlayMath.Anchor;
import dev.caecorthus.sparkwitch.client.saint.HolyFlashOverlayMath.Projection;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashComponent;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashRules;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Client-only Holy Flash screen mask (owner pick B), drawn by {@code HolyFlashHudMixin} at {@code InGameHud.render}
 * TAIL above the whole HUD, chat, HudRenderCallback overlays (including the Seeker CCTV frame) and the world's name
 * tags and instinct outlines. Phases: a bright spot that blooms over {@link HolyFlashRules#SPOT_TICKS}, then the
 * black mask from {@link HolyFlashRules#blackness(float, int)} with a short white residue and a faint retinal
 * afterimage. Draws only on a confirmed SparkWitch server, in an ACTIVE game, for the playing, alive local player
 * whose synced {@code sparkwitch:holy_flash} is active; F1 does not hide it. The server owns every timer.
 * Never drawn while the local player is shown the Blind view ({@link BlindView#isActive}, owner decision Q12): the
 * Blind takes only the audio side, which {@code HolyFlashAudioClient} drives from the same synced state.
 * 纯客户端圣光弹屏幕遮罩（所有者选择方案 B），由 {@code HolyFlashHudMixin} 在 {@code InGameHud.render} TAIL 绘制，
 * 位于整个 HUD、聊天、HudRenderCallback 叠加层（包括搜寻者 CCTV 边框）以及世界中名字标签与本能描边之上。
 * 阶段：在 {@link HolyFlashRules#SPOT_TICKS} 内迅速扩散的亮点，然后是 {@link HolyFlashRules#blackness(float, int)}
 * 决定的黑色遮罩，并带有短暂的白色残留和淡淡的视网膜残像。仅在确认的 SparkWitch 服务器、对局 ACTIVE、本地玩家
 * 存活参与且已同步的 {@code sparkwitch:holy_flash} 激活时绘制；F1 不会隐藏它。所有计时均由服务端负责。
 * 本地玩家正在看盲人视图时（{@link BlindView#isActive}，所有者决定 Q12）绝不绘制：盲人只受音频部分影响，
 * 该部分由 {@code HolyFlashAudioClient} 依据同一份同步状态驱动。
 */
public final class HolyFlashOverlayRenderer {
    public static final Identifier GLOW_TEXTURE = SparkWitch.id("textures/gui/holy_flash_glow.png");
    private static final int GLOW_TEXTURE_SIZE = 128;
    private static final int WHITEOUT_RGB = 0xFFF8EC;
    private static final float[] AFTERIMAGE_RGB = {1.0F, 0.9F, 0.72F};

    // Screen-fixed retinal burn for the current flash, as screen fractions (survives a resize).
    // 当前闪光在屏幕上固定的视网膜灼斑位置，以屏幕比例保存（窗口缩放后仍有效）。
    private static int burnTotalTicks;
    private static Vec3d burnBurstPos;
    private static double burnFractionX = 0.5D;
    private static double burnFractionY = 0.5D;
    private static boolean burnDim = true;

    private HolyFlashOverlayRenderer() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        HolyFlashComponent flash = HolyFlashComponent.KEY.get(player);
        if (!flash.isActive()) {
            return;
        }
        // ACTIVE only: STOPPING still counts as playing but sits under Wathe's round-end fade to black.
        // 仅 ACTIVE：STOPPING 仍算参与，但处于 Wathe 回合结束黑幕之下。
        if (GameWorldComponent.KEY.get(client.world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        // The Kidnapper's opaque control screen (drawn just before) already hides everything and its text must
        // stay readable. / 绑匪控制黑屏（在此之前绘制）已遮住一切，其提示文字必须保持可读。
        if (KidnapperControlComponent.KEY.get(player).isControlled()) {
            return;
        }
        // Q12 (owner, 2026-10-03): the Blind takes only the tinnitus. While the Blind view is on (echo line art or
        // its fail-closed black) no white or black mask is layered over it; a non-Blind leaves at the role lookup.
        // Q12（所有者 2026-10-03）：盲人只受耳鸣。盲人视图开启期间（回声线稿或失败关闭的全黑）不在其上叠加
        // 白色或黑色遮罩；非盲人在职业查询处即返回。
        if (BlindView.isActive(client)) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float elapsed = flash.elapsedTicks(tickCounter.getTickDelta(true));
        int total = flash.totalTicks();

        if (HolyFlashOverlayMath.inSpotPhase(elapsed)) {
            Anchor anchor = currentAnchor(client, player, flash, width, height);
            rememberBurn(flash, anchor, width, height);
            float progress = HolyFlashOverlayMath.spotProgress(elapsed);
            double radius = HolyFlashOverlayMath.glowRadius(progress, anchor, width, height);
            float alpha = HolyFlashOverlayMath.glowAlpha(anchor.dim());
            drawGlow(context, anchor.x(), anchor.y(), radius, 1.0F, 1.0F, 1.0F, alpha);
            return;
        }

        if (!burnMatches(flash)) {
            rememberBurn(flash, currentAnchor(client, player, flash, width, height), width, height);
        }
        float black = HolyFlashRules.blackness(elapsed, total);
        if (black > 0.0F) {
            context.fill(RenderLayer.getGuiOverlay(), 0, 0, width, height, HolyFlashOverlayMath.argb(black, 0));
        }
        float white = HolyFlashOverlayMath.whiteout(elapsed, burnDim);
        if (white > 0.0F) {
            context.fill(RenderLayer.getGuiOverlay(), 0, 0, width, height,
                    HolyFlashOverlayMath.argb(white, WHITEOUT_RGB));
        }
        float afterimage = HolyFlashOverlayMath.afterimageAlpha(elapsed, total, burnDim);
        if (afterimage > 0.0F) {
            double radius = HolyFlashOverlayMath.afterimageRadius(elapsed, total, width, height);
            drawGlow(context, burnFractionX * width, burnFractionY * height, radius,
                    AFTERIMAGE_RGB[0], AFTERIMAGE_RGB[1], AFTERIMAGE_RGB[2], afterimage);
        }
    }

    /**
     * Projects the burst through the live camera. A camera that is not the local player's eyes (Seeker remote
     * view or any spectated entity) gets a centred glow instead, since the burst direction belongs to the body.
     * 通过当前相机投影爆点。相机不是本地玩家视角时（搜寻者遥控视角或任何被观察实体），改为居中光晕，
     * 因为爆点方向属于本体。
     */
    private static Anchor currentAnchor(MinecraftClient client, ClientPlayerEntity player, HolyFlashComponent flash,
                                        int width, int height) {
        Camera camera = client.gameRenderer.getCamera();
        Projection projection = null;
        if (camera.isReady() && client.getCameraEntity() == player && !SeekerRemoteViewClient.isActive()) {
            Vec3d burst = flash.burstPos();
            Vec3d eye = camera.getPos();
            Vector3f forward = camera.getHorizontalPlane();
            Vector3f up = camera.getVerticalPlane();
            // Camera#getDiagonalPlane is the camera's left. / Camera#getDiagonalPlane 是相机左方。
            Vector3f left = camera.getDiagonalPlane();
            projection = HolyFlashOverlayMath.project(
                    burst.x - eye.x, burst.y - eye.y, burst.z - eye.z,
                    forward.x(), forward.y(), forward.z(),
                    up.x(), up.y(), up.z(),
                    -left.x(), -left.y(), -left.z(),
                    client.options.getFov().getValue(), width, height);
        }
        return HolyFlashOverlayMath.anchor(flash.facedBurst(), projection, width, height);
    }

    private static boolean burnMatches(HolyFlashComponent flash) {
        return burnTotalTicks == flash.totalTicks() && flash.burstPos().equals(burnBurstPos);
    }

    private static void rememberBurn(HolyFlashComponent flash, Anchor anchor, int width, int height) {
        burnTotalTicks = flash.totalTicks();
        burnBurstPos = flash.burstPos();
        burnFractionX = width > 0 ? anchor.x() / width : 0.5D;
        burnFractionY = height > 0 ? anchor.y() / height : 0.5D;
        burnDim = anchor.dim();
    }

    /** One scaled quad of the blurred radial glow texture. / 一个缩放后的模糊径向光晕贴图四边形。 */
    private static void drawGlow(DrawContext context, double x, double y, double radius,
                                 float red, float green, float blue, float alpha) {
        if (!(radius > 0.0D) || alpha <= 0.0F) {
            return;
        }
        int half = GLOW_TEXTURE_SIZE / 2;
        float scale = (float) (radius / half);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0D);
        matrices.scale(scale, scale, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        context.drawTexture(GLOW_TEXTURE, -half, -half, GLOW_TEXTURE_SIZE, GLOW_TEXTURE_SIZE,
                0.0F, 0.0F, GLOW_TEXTURE_SIZE, GLOW_TEXTURE_SIZE, GLOW_TEXTURE_SIZE, GLOW_TEXTURE_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
