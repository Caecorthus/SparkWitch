package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarUseC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerCarUseRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-client side of the car's own right-click ({@code seeker_car_use}), CAR mode only. The possession lock keeps
 * every body interaction off (the use key still reads as released, {@code doItemUse} and {@code interactBlock} stay
 * cancelled and the crosshair stays a forced MISS), so this class takes the use key's real queued presses itself and
 * turns at most one per {@link SeekerRules#CAR_USE_INTERVAL_TICKS} into a request. The ray starts at the car's eye with
 * the car's heading and the view pitch, reaches {@link SeekerRules#CAR_USE_REACH} blocks against block outlines (no
 * fluids), and only whitelisted blocks inside the server's forward cone count. It also draws a vanilla-style outline and a CCTV hint for that block.
 * Everything here is a prediction: the server re-validates every request.
 * 小车自身右键交互（{@code seeker_car_use}）的拥有者客户端部分，仅 CAR 模式。附身锁让本体的一切交互保持关闭（使用键
 * 仍视为未按下，{@code doItemUse} 与 {@code interactBlock} 仍被取消，准星仍被强制为 MISS），因此本类自行取走使用键
 * 真实的积压按键，每 {@link SeekerRules#CAR_USE_INTERVAL_TICKS} 刻最多转换为一次请求。射线从小车眼睛出发，
 * 使用车头朝向与画面俯仰，对方块轮廓检测 {@link SeekerRules#CAR_USE_REACH} 格（忽略流体），只认位于服务端前方锥角内的白名单方块。
 * 它还为该方块绘制原版风格的描边与 CCTV 提示。这里的一切都只是预测：服务端会重新校验每个请求。
 */
public final class SeekerCarUseClient {
    private static final int HINT_OFFSET_Y = 12;
    private static final float OUTLINE_ALPHA = 0.4F;
    private static boolean registered;
    private static int ticksSinceUse = SeekerRules.CAR_USE_INTERVAL_TICKS;

    private SeekerCarUseClient() {
    }

    /**
     * Called once from {@code SeekerRemoteViewClient.register()} right after it registers its own START_CLIENT_TICK
     * listener, so this tick runs after the session state of the same tick is settled and still before
     * {@code handleInputEvents}.
     * 由 {@code SeekerRemoteViewClient.register()} 在注册其 START_CLIENT_TICK 监听器之后立即调用一次，
     * 因此本 tick 在同一刻的会话状态确定之后运行，且仍早于 {@code handleInputEvents}。
     */
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.START_CLIENT_TICK.register(SeekerCarUseClient::tick);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(SeekerCarUseClient::renderOutline);
    }

    private static void tick(MinecraftClient client) {
        SeekerCarEntity car = drivenCar();
        if (car == null || client.options == null) {
            ticksSinceUse = SeekerRules.CAR_USE_INTERVAL_TICKS;
            return;
        }
        if (ticksSinceUse < SeekerRules.CAR_USE_INTERVAL_TICKS) {
            ticksSinceUse++;
        }
        // Presses beyond the throttle are dropped, never replayed. / 超出节流的按键直接丢弃，绝不重放。
        int presses = ((SeekerRemoteKeyDrain) client.options.useKey).sparkwitch$takePresses();
        ClientPlayerEntity player = client.player;
        if (presses <= 0 || ticksSinceUse < SeekerRules.CAR_USE_INTERVAL_TICKS || player == null
                || SeekerCctvOverlay.isSignalLost(player)) {
            return;
        }
        BlockHitResult aim = aim(car, 1.0F);
        if (aim == null || !ClientPlayNetworking.canSend(SeekerCarUseC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new SeekerCarUseC2SPacket(SeekerClientState.sessionId(), aim));
        ticksSinceUse = 0;
    }

    /**
     * CCTV hint under the reticle, drawn by {@code SeekerCctvOverlay} in its CAR branch after the HUD gates.
     * 准星下方的 CCTV 提示，由 {@code SeekerCctvOverlay} 在 HUD 门槛之后的 CAR 分支中绘制。
     */
    static void renderHint(DrawContext context, TextRenderer text, RenderTickCounter tickCounter, int width,
                           int height, int color) {
        SeekerCarEntity car = drivenCar();
        BlockHitResult aim = car == null ? null : aim(car, tickCounter.getTickDelta(true));
        if (aim == null) {
            return;
        }
        BlockState state = car.getWorld().getBlockState(aim.getBlockPos());
        SeekerCarUseRules.Target target = SeekerCarUseRules.target(state);
        if (target == null) {
            return;
        }
        Text hint = Text.translatable(SeekerCarUseRules.hintKey(target, SeekerCarUseRules.isActive(state)),
                MinecraftClient.getInstance().options.useKey.getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(text, hint, width / 2, height / 2 + HINT_OFFSET_Y, color);
    }

    /**
     * Fabric fires BEFORE_BLOCK_OUTLINE every frame, even for the forced MISS; vanilla then draws nothing because the
     * camera is not a player, so the car's target gets vanilla's own outline here. Always answers true.
     * Fabric 每帧都会触发 BEFORE_BLOCK_OUTLINE（即使准星被强制为 MISS）；由于相机不是玩家，原版此时不会绘制，
     * 因此在这里为小车的目标绘制原版描边。始终返回 true。
     */
    private static boolean renderOutline(WorldRenderContext context, @Nullable HitResult vanillaTarget) {
        MinecraftClient client = MinecraftClient.getInstance();
        SeekerCarEntity car = drivenCar();
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (car == null || consumers == null || matrices == null || client.options.hudHidden
                || client.player == null || SeekerCctvOverlay.isSignalLost(client.player)) {
            return true;
        }
        BlockHitResult aim = aim(car, context.tickCounter().getTickDelta(true));
        if (aim == null) {
            return true;
        }
        World world = car.getWorld();
        BlockPos pos = aim.getBlockPos();
        Vec3d camera = context.camera().getPos();
        WorldRenderer.drawShapeOutline(matrices, consumers.getBuffer(RenderLayer.getLines()),
                world.getBlockState(pos).getOutlineShape(world, pos, ShapeContext.of(car)),
                pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                0.0F, 0.0F, 0.0F, OUTLINE_ALPHA, false);
        return true;
    }

    @Nullable
    private static SeekerCarEntity drivenCar() {
        if (!SeekerRemoteViewClient.isActive() || SeekerRemoteViewClient.mode() != SeekerSessionMode.CAR) {
            return null;
        }
        return SeekerRemoteViewClient.focus() instanceof SeekerCarEntity car && SeekerRemoteViewClient.isDriving(car)
                && !car.isRemoved() ? car : null;
    }

    /** The whitelisted block the car aims at within reach, or null. / 小车在触及范围内瞄准的白名单方块，否则为 null。 */
    @Nullable
    private static BlockHitResult aim(SeekerCarEntity car, float tickDelta) {
        World world = car.getWorld();
        Vec3d eye = car.getCameraPosVec(tickDelta);
        Vec3d end = eye.add(car.getRotationVec(tickDelta).multiply(SeekerRules.CAR_USE_REACH));
        BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, car));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult attributed = SeekerCarUseRules.attributeHit(world, hit);
        // Mirror the server's forward cone, so no hint or outline promises a use the server would refuse.
        // 与服务端的前方锥角一致，避免提示或描边承诺一个会被服务端拒绝的交互。
        if (attributed == null || SeekerCarUseRules.target(world.getBlockState(attributed.getBlockPos())) == null
                || !SeekerCarUseRules.withinForwardCone(eye, car.getYaw(tickDelta), attributed.getPos())) {
            return null;
        }
        return attributed;
    }
}
