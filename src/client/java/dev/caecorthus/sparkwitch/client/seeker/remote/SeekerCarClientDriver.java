package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarPhysics;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarCorrectS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarMoveC2SPacket;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-client car simulation (L3). While the local player drives, this client is the car's logical movement side:
 * the car ignores tracker updates ({@code SeekerCarMovement} client drive hook, installed by
 * {@link SeekerRemoteViewClient#register()}), and this driver moves it once per END_CLIENT_TICK from the raw
 * movement {@code KeyBinding} states. It never ticks a second keyboard input object, because footstep hooks act on
 * every such instance. W/S and A/D become raw axes (+1 forward, +1 left, vanilla convention);
 * {@link SeekerCarPhysics#inputVelocity} turns them into CAR_SPEED motion with 0.5x strafe and gravity. No jump,
 * sprint is ignored. Mouse yaw is the heading; pitch is view-only. The target is clamped locally to the play area
 * only (no distance limit from the body since 2026-10-04), then {@code seeker_car_move} carries the result to the
 * server, which replays, validates and corrects through {@code seeker_car_correct}. The server stays authoritative for
 * the car everyone else sees.
 * 拥有者客户端的小车模拟（L3）。本地玩家驾驶期间，该客户端是小车的逻辑移动端：小车忽略追踪包（由
 * {@link SeekerRemoteViewClient#register()} 安装的 {@code SeekerCarMovement} 客户端驾驶钩子），
 * 本驾驶器在每个 END_CLIENT_TICK 根据移动 {@code KeyBinding} 的原始状态移动小车一次。它从不 tick 第二个键盘输入对象，
 * 因为脚步声钩子会作用于每个这样的实例。W/S 与 A/D 转为原始输入轴（前为 +1、左为 +1，与原版一致），
 * 由 {@link SeekerCarPhysics#inputVelocity} 换算为 CAR_SPEED 速度（平移 0.5 倍）并加入重力。不能跳，忽略疾跑。
 * 鼠标 yaw 即车头朝向，pitch 只影响画面。目标位置先在本地仅钳制进游戏区域（自 2026-10-04 起与本体之间没有距离限制），
 * 再由 {@code seeker_car_move} 发往服务端，服务端重放、校验并通过 {@code seeker_car_correct} 纠正。其他玩家看到的小车
 * 始终以服务端为准。
 */
public final class SeekerCarClientDriver {
    @Nullable
    private static SeekerCarEntity car;
    private static int sessionId;
    private static int seq;
    private static Vec3d velocity = Vec3d.ZERO;
    private static boolean onGround = true;
    @Nullable
    private static Vec3d lastSentPosition;
    private static float lastSentYaw;
    private static int ticksSinceSend;
    /** Horizontal margin covering one tick of car travel when probing loaded chunks. / 探测已加载区块时覆盖小车单刻位移的水平余量。 */
    private static final double CHUNK_PROBE_MARGIN = 1.0;

    private SeekerCarClientDriver() {
    }

    /**
     * Server correction for the car this client drives. Stale sessions and cars are ignored; the position snaps
     * (no interpolation) and the local heading is kept, because the heading is the driver's own mouse look. The fall
     * speed is kept too (there is no horizontal momentum to keep): the server's fall model never restarts on a
     * correction, so an owner that restarted from rest would fall slower than the model and be corrected again.
     * 对本客户端所驾驶小车的服务端纠正。过期会话与车辆直接忽略；位置瞬间对齐（不插值），
     * 并保留本地车头朝向，因为朝向就是驾驶者自己的鼠标视角。下落速度同样保留（没有水平惯性可保留）：服务端下落模型
     * 从不因纠正而重启，若拥有者从静止重新开始，就会比模型下落得慢而再次被纠正。
     */
    public static void onCorrection(SeekerCarCorrectS2CPacket packet) {
        SeekerCarEntity driven = car;
        if (driven == null || packet.sessionId() != sessionId || !SeekerRemoteViewClient.isDriving(driven)) {
            return;
        }
        if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) {
            return;
        }
        Vec3d corrected = new Vec3d(packet.x(), packet.y(), packet.z());
        snap(driven, corrected);
        velocity = new Vec3d(0.0, Math.min(velocity.y, 0.0), 0.0);
        lastSentPosition = corrected;
        lastSentYaw = driven.getYaw();
    }

    /**
     * Client drive hook for {@code SeekerCarMovement}: true only on the client world, for the car this client is
     * driving right now. Server-side (integrated server) cars always answer false.
     * {@code SeekerCarMovement} 的客户端驾驶钩子：仅在客户端世界、且为本客户端当前驾驶的小车时为 true。
     * 服务端（含内置服务端）的小车始终返回 false。
     */
    public static boolean isLocallyDriven(SeekerCarEntity candidate) {
        return candidate != null && candidate.getWorld().isClient() && SeekerRemoteViewClient.isDriving(candidate);
    }

    /** Start edge of a CAR session. / CAR 会话开始沿。 */
    static void start(SeekerCarEntity driven, int session) {
        car = driven;
        sessionId = session;
        seq = 0;
        velocity = Vec3d.ZERO;
        onGround = driven.isOnGround();
        lastSentPosition = null;
        lastSentYaw = driven.getYaw();
        // Send on the first END tick so the server sees the attach promptly. / 首个 END 刻即发送，便于服务端尽快确认挂接。
        ticksSinceSend = SeekerRemoteViewRules.MOVE_KEEPALIVE_TICKS;
        driven.setPitch(0.0F);
        driven.prevPitch = 0.0F;
    }

    /**
     * End edge: hands the car back to tracker updates. Its tracked position is re-based on the last sent position,
     * the best local estimate of the server's tracker base, so later relative moves land in the right place.
     * 结束沿：把小车交还给追踪包。其追踪位置重设为最后一次发送的位置（对服务端追踪基准的最佳本地估计），
     * 使之后的相对移动包落在正确位置。
     */
    static void stop() {
        SeekerCarEntity driven = car;
        car = null;
        if (driven != null && !driven.isRemoved()) {
            Vec3d base = lastSentPosition != null ? lastSentPosition : driven.getPos();
            driven.updateTrackedPosition(base.x, base.y, base.z);
            driven.setVelocity(Vec3d.ZERO);
            driven.setPitch(0.0F);
            driven.prevPitch = 0.0F;
        }
        velocity = Vec3d.ZERO;
        lastSentPosition = null;
    }

    /**
     * Same session, re-tracked car: the server streamed the car to this client again as a new instance with the same
     * id (unlimited range). Drive the new instance without restarting the move sequence, because the server admits only
     * a rising {@code seq} within a session; the first END tick re-sends its position.
     * 同一会话中重新追踪的小车：服务端把小车以同 id 的新实例再次推送给本客户端（无限距离）。驾驶新实例且不重置移动序号，
     * 因为服务端在一个会话内只接受递增的 {@code seq}；首个 END 刻会重新发送其位置。
     */
    static void retrack(SeekerCarEntity driven) {
        car = driven;
        velocity = Vec3d.ZERO;
        onGround = driven.isOnGround();
        lastSentPosition = null;
        lastSentYaw = driven.getYaw();
        ticksSinceSend = SeekerRemoteViewRules.MOVE_KEEPALIVE_TICKS;
        driven.setPitch(0.0F);
        driven.prevPitch = 0.0F;
    }

    /**
     * Mouse look while driving: yaw turns the car, pitch tilts only the view within +-60 degrees.
     * 驾驶时的鼠标视角：yaw 转动车头，pitch 只在 ±60° 内倾斜画面。
     */
    static void look(SeekerCarEntity driven, double cursorDeltaX, double cursorDeltaY) {
        float yawDelta = (float) cursorDeltaX * SeekerRemoteViewRules.LOOK_SCALE;
        float oldPitch = driven.getPitch();
        float pitch = SeekerRemoteViewRules.clampCarPitch(oldPitch + (float) cursorDeltaY * SeekerRemoteViewRules.LOOK_SCALE);
        driven.setYaw(driven.getYaw() + yawDelta);
        driven.setPitch(pitch);
        driven.prevYaw += yawDelta;
        driven.prevPitch += pitch - oldPitch;
    }

    /**
     * END_CLIENT_TICK: after the world tick settled {@code prev*}, so rendering interpolates the step smoothly.
     * END_CLIENT_TICK：在世界 tick 结算 {@code prev*} 之后执行，渲染因此能平滑插值这一步。
     */
    static void endTick(MinecraftClient client) {
        SeekerCarEntity driven = car;
        if (driven == null || !SeekerRemoteViewClient.isDriving(driven) || driven.isRemoved()
                || client.isPaused() || client.world == null || driven.getWorld() != client.world) {
            return;
        }
        // Never simulate into chunks this client does not have yet (they stream in around the car): the car holds still
        // instead of falling through an empty chunk, and the keepalive below keeps the session alive.
        // 绝不在本客户端尚未拥有的区块中模拟（它们会围绕小车推送到达）：小车原地保持而不是穿过空区块下落，下方的保活包维持会话。
        if (SeekerBodyHold.chunksLoaded(driven.getWorld(),
                driven.getBoundingBox().expand(CHUNK_PROBE_MARGIN, 0.0, CHUNK_PROBE_MARGIN))) {
            drive(client, driven);
        }
        ticksSinceSend++;
        Vec3d position = driven.getPos();
        float yaw = driven.getYaw();
        if (!SeekerRemoteViewRules.moved(lastSentPosition, lastSentYaw, position, yaw)
                && !SeekerRemoteViewRules.keepaliveDue(ticksSinceSend)) {
            return;
        }
        if (ClientPlayNetworking.canSend(SeekerCarMoveC2SPacket.ID)) {
            ClientPlayNetworking.send(new SeekerCarMoveC2SPacket(sessionId, ++seq, position.x, position.y, position.z,
                    MathHelper.wrapDegrees(yaw)));
        }
        lastSentPosition = position;
        lastSentYaw = yaw;
        ticksSinceSend = 0;
    }

    private static void drive(MinecraftClient client, SeekerCarEntity driven) {
        GameOptions options = client.options;
        float forward = SeekerRemoteViewRules.axis(options.forwardKey.isPressed(), options.backKey.isPressed());
        float sideways = SeekerRemoteViewRules.axis(options.leftKey.isPressed(), options.rightKey.isPressed());
        World world = driven.getWorld();
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        Vec3d from = driven.getPos();
        Vec3d wanted = SeekerCarPhysics.inputVelocity(forward, sideways, driven.getYaw(), velocity, onGround);
        Vec3d target = SeekerRemoteViewRules.clampHorizontalTarget(playArea, from.add(wanted));
        Vec3d bounded = new Vec3d(target.x - from.x, wanted.y, target.z - from.z);
        SeekerCarPhysics.Result result = SeekerCarPhysics.move(world, from, bounded);
        if (!SeekerRemoteViewRules.acceptsMove(playArea, from, result.position())) {
            result = SeekerCarPhysics.move(world, from, new Vec3d(0.0, wanted.y, 0.0));
        }
        velocity = result.velocity();
        onGround = result.onGround();
        driven.setPosition(result.position());
        driven.setVelocity(result.velocity());
        driven.setOnGround(result.onGround());
    }

    private static void snap(SeekerCarEntity driven, Vec3d position) {
        driven.setPosition(position);
        driven.prevX = position.x;
        driven.prevY = position.y;
        driven.prevZ = position.z;
        driven.lastRenderX = position.x;
        driven.lastRenderY = position.y;
        driven.lastRenderZ = position.z;
        driven.setVelocity(Vec3d.ZERO);
    }
}
