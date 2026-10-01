package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCameraLookC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server authority for {@code seeker_camera_look}: only the owner's own open CAMERA session may turn the camera it
 * views, and {@link SeekerCameraEntity#setLook} clamps the value into that camera's cone. There is no per-tick gate:
 * packets that arrive together (network jitter) all apply and the latest wins, while the DataTracker still sends the
 * look to other clients at most once per tick. Every rejection is silent and never counts as cheating; the look is
 * cosmetic.
 * {@code seeker_camera_look} 的服务端权威：只有拥有者自己打开的 CAMERA 会话能转动其正在观看的摄像头，并由
 * {@link SeekerCameraEntity#setLook} 钳制到该摄像头的锥角内。没有逐刻闸门：因网络抖动同时到达的包全部应用、以最新的为准，
 * 而 DataTracker 向其他客户端发送视角仍然每刻最多一次。所有拒绝都静默处理且不计为作弊；视角只是外观。
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
        Entity focus = player.getServerWorld().getEntityById(status.state().sessionFocusEntityId());
        if (!(focus instanceof SeekerCameraEntity camera) || camera.isRemoved()
                || !camera.isOwnedBy(player.getUuid())) {
            return;
        }
        camera.setLook(packet.yaw(), packet.pitch());
    }
}
