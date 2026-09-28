package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.client.seeker.console.SeekerConsoleRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCameraRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteOpenC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

/**
 * Camera cycling while viewing a camera, called once per tick by {@link SeekerRemoteViewClient} while a session
 * continues. The left/right movement keys (A/D by default) stay allowed while viewing because they steer the car; in
 * CAMERA mode their presses are read here instead and ask the server for the previous/next camera by label, wrapping
 * around and skipping cameras this client cannot resolve or already knows are beyond the session radius. The body
 * never moves (it runs {@link SeekerFrozenInput}), and queued presses are drained in every mode so a press made while
 * driving never cycles after a switch to a camera. Client prediction only: it sends {@code seeker_remote_open} with an
 * explicit target, spaced like the quick-connect key; the server re-validates ownership, range and its open throttle,
 * and a granted switch arrives as a new session id that {@link SeekerRemoteViewClient} retargets in place.
 * 观看摄像头时的摄像头切换，由 {@link SeekerRemoteViewClient} 在会话持续期间每刻调用一次。左右移动键（默认 A/D）在观看期间
 * 仍被放行，因为它们用于驾驶小车；在摄像头模式下由这里读取按下事件，并按编号向服务端请求上一台/下一台摄像头，循环切换，
 * 跳过本客户端无法解析或已知超出会话半径的摄像头。本体从不移动（使用 {@link SeekerFrozenInput}），且在任何模式下都会清空
 * 积压的按下次数，避免驾驶时的按键在切到摄像头后触发切换。仅为客户端预测：它发送带明确目标的 {@code seeker_remote_open}，
 * 发送间隔与快速连接键相同；服务端会重新校验归属、范围与打开节流，获准的切换以新的会话 id 到达，由
 * {@link SeekerRemoteViewClient} 原地切换视角。
 */
public final class SeekerCameraCycler {
    /** Body age at the last cycle request; -1 = never. / 上次发送切换请求时的本体 age；-1 表示从未发送。 */
    private static long lastSendAge = -1L;

    private SeekerCameraCycler() {
    }

    static void tick(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode activeMode) {
        KeyBinding left = client.options.leftKey;
        KeyBinding right = client.options.rightKey;
        boolean previous = left.wasPressed();
        boolean next = right.wasPressed();
        // One step per tick at most; never replay queued presses. / 每刻最多切换一步；绝不重放积压的按键。
        ((SeekerRemoteKeyDrain) left).sparkwitch$drainPresses();
        ((SeekerRemoteKeyDrain) right).sparkwitch$drainPresses();
        int direction = direction(previous, next);
        if (direction == 0 || activeMode != SeekerSessionMode.CAMERA
                || SeekerClientState.sessionMode() != SeekerSessionMode.CAMERA
                || !SeekerConsoleRules.throttleElapsed(player.age, lastSendAge,
                SeekerConsoleRules.QUICK_CONNECT_THROTTLE_TICKS)
                || !ClientPlayNetworking.canSend(SeekerRemoteOpenC2SPacket.ID)) {
            return;
        }
        int radius = SeekerClientState.effectiveRadius();
        int target = SeekerCameraRules.cycle(SeekerClientState.cameras(), SeekerClientState.sessionFocusEntityId(),
                direction, id -> selectable(client.world, player, id, radius));
        if (target < 0) {
            return;
        }
        lastSendAge = player.age;
        ClientPlayNetworking.send(SeekerRemoteOpenC2SPacket.of(SeekerSessionMode.CAMERA, target));
    }

    /** -1 previous (left), +1 next (right), 0 for none or both. / -1 上一台（左），+1 下一台（右），无或同时按下为 0。 */
    static int direction(boolean previous, boolean next) {
        return (int) SeekerRemoteViewRules.axis(next, previous);
    }

    /**
     * Resolvable on this client and within the session radius of the body (the server's own horizontal check).
     * 可在本客户端解析，且位于本体的会话半径内（与服务端相同的水平判定）。
     */
    private static boolean selectable(ClientWorld world, ClientPlayerEntity player, int entityId, int radius) {
        Entity entity = world == null ? null : world.getEntityById(entityId);
        return entity instanceof SeekerCameraEntity camera && camera.isAlive()
                && SeekerRemoteRules.withinEffectiveRadius(player.getPos(), camera.getPos(), radius);
    }
}
