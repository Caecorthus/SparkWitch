package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCameraLookC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server authority for {@code seeker_camera_look}: only the owner's own open CAMERA session may turn the camera it
 * views, at most one accepted look per server tick, and {@link SeekerCameraEntity#setLook} clamps the value into that
 * camera's cone. Every rejection is silent and never counts as cheating; the look is cosmetic, so a lost packet only
 * delays the head until the owner's client re-syncs.
 * {@code seeker_camera_look} 的服务端权威：只有拥有者自己打开的 CAMERA 会话能转动其正在观看的摄像头，每个服务端刻最多接受一次，
 * 并由 {@link SeekerCameraEntity#setLook} 钳制到该摄像头的锥角内。所有拒绝都静默处理且不计为作弊；视角只是外观，
 * 丢包只会让机头延迟到拥有者客户端重新同步为止。
 */
public final class SeekerCameraLookService {
    private SeekerCameraLookService() {
    }

    public static void handleLook(ServerPlayerEntity player, SeekerCameraLookC2SPacket packet) {
        if (player == null || packet == null || !Float.isFinite(packet.yaw()) || !Float.isFinite(packet.pitch())) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return;
        }
        SeekerSessionState session = status.sessionState();
        if (session == null || status.sessionMode() != SeekerSessionMode.CAMERA
                || packet.sessionId() != status.sessionId() || session.sessionId != status.sessionId()) {
            return;
        }
        long now = SeekerRemoteSessionService.serverTick(player);
        if (session.lastLookTick == now) {
            return;
        }
        Entity focus = player.getServerWorld().getEntityById(status.state().sessionFocusEntityId());
        if (!(focus instanceof SeekerCameraEntity camera) || camera.isRemoved()
                || !camera.isOwnedBy(player.getUuid())) {
            return;
        }
        session.lastLookTick = now;
        camera.setLook(packet.yaw(), packet.pitch());
    }
}
