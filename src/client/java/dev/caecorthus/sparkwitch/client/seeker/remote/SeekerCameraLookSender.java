package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraLookRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCameraLookC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Owner client: reports the camera view direction with {@code seeker_camera_look} while viewing a camera, so the server
 * can turn the head every player sees. Called once per client tick by {@link SeekerRemoteViewClient}; the cadence is
 * {@link SeekerCameraLookRules#shouldSend}. It never decides anything: the server re-validates the session and clamps
 * the look into the camera's cone.
 * 拥有者客户端：观看摄像头时用 {@code seeker_camera_look} 上报视角，使服务端能转动所有玩家都能看到的机头。
 * 由 {@link SeekerRemoteViewClient} 每个客户端刻调用一次；节奏见 {@link SeekerCameraLookRules#shouldSend}。
 * 它不做任何决定：服务端会重新校验会话，并把视角钳制到摄像头的锥角内。
 */
public final class SeekerCameraLookSender {
    // A new viewpoint means a new binding; weak so a finished session never pins the old world.
    // 新的视点即新的绑定；使用弱引用，结束的会话不会拖住旧世界。
    private static WeakReference<SeekerCameraViewpoint> bound = new WeakReference<>(null);
    private static float lastYaw;
    private static float lastPitch;
    private static int ticksSinceSend;

    private SeekerCameraLookSender() {
    }

    public static void tick(@Nullable SeekerCameraViewpoint viewpoint, int sessionId) {
        if (viewpoint == null) {
            return;
        }
        SeekerCameraEntity camera = viewpoint.mount();
        float yaw = MathHelper.wrapDegrees(viewpoint.getYaw());
        float pitch = viewpoint.getPitch();
        if (bound.get() != viewpoint) {
            // The viewpoint starts from the camera's synced look, so there is nothing to send yet.
            // 视点从摄像头同步的视角开始，因此此时无需发送。
            bound = new WeakReference<>(viewpoint);
            lastYaw = camera.lookYaw();
            lastPitch = camera.lookPitch();
            ticksSinceSend = 0;
        }
        ticksSinceSend = Math.min(ticksSinceSend + 1, SeekerCameraLookRules.RESYNC_TICKS);
        boolean moved = SeekerCameraLookRules.differs(yaw, pitch, lastYaw, lastPitch);
        boolean differsFromServer = SeekerCameraLookRules.differs(yaw, pitch, camera.lookYaw(), camera.lookPitch());
        if (!SeekerCameraLookRules.shouldSend(ticksSinceSend, moved, differsFromServer)
                || !ClientPlayNetworking.canSend(SeekerCameraLookC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new SeekerCameraLookC2SPacket(sessionId, yaw, pitch));
        lastYaw = yaw;
        lastPitch = pitch;
        ticksSinceSend = 0;
    }
}
