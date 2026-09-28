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
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Camera cycling while viewing a camera, called once per tick by {@link SeekerRemoteViewClient} while a session
 * continues. The left/right movement keys (A/D by default) stay allowed while viewing because they steer the car; in
 * CAMERA mode a fresh key-down here ({@link SeekerRemoteViewRules#freshPress}) asks the server for the previous/next
 * camera by label, wrapping around and skipping cameras this client cannot resolve or already knows are beyond the
 * session radius. OS key repeat never cycles again: a held key cycles once, and every bind of a viewpoint (session
 * start, car to camera, camera to camera) drains the queued left/right presses and treats both keys as held until they
 * are seen released, so a key held while driving or through a switch never cycles. Queued presses are also drained on
 * every tick in every mode (car steering reads only the held state). The body never moves (it runs
 * {@link SeekerFrozenInput}). Client prediction only: it sends {@code seeker_remote_open} with an explicit target,
 * spaced {@link SeekerRemoteViewRules#CAMERA_CYCLE_THROTTLE_TICKS} after the last request or bind, which stays ahead of
 * the server's shared open throttle; the server re-validates ownership, range and that throttle, and a granted switch
 * arrives as a new session id that {@link SeekerRemoteViewClient} retargets in place.
 * 观看摄像头时的摄像头切换，由 {@link SeekerRemoteViewClient} 在会话持续期间每刻调用一次。左右移动键（默认 A/D）在观看期间
 * 仍被放行，因为它们用于驾驶小车；在摄像头模式下，这里检测到新的按下沿（{@link SeekerRemoteViewRules#freshPress}）时，
 * 按编号向服务端请求上一台/下一台摄像头，循环切换，跳过本客户端无法解析或已知超出会话半径的摄像头。系统按键重复不会再次
 * 触发切换：按住只切换一次；每次绑定视点（会话开始、小车切到摄像头、摄像头切到摄像头）都会清空左右键积压的按下次数，
 * 并把两个键视为按住，直到观察到松开，因此驾驶时或切换过程中一直按住的键不会触发切换。任何模式下每刻也都会清空积压的
 * 按下次数（小车转向只读取按住状态）。本体从不移动（使用 {@link SeekerFrozenInput}）。仅为客户端预测：它发送带明确目标的
 * {@code seeker_remote_open}，与上次请求或绑定间隔 {@link SeekerRemoteViewRules#CAMERA_CYCLE_THROTTLE_TICKS} 刻，
 * 始终晚于服务端共用的打开节流；服务端会重新校验归属、范围与该节流，获准的切换以新的会话 id 到达，由
 * {@link SeekerRemoteViewClient} 原地切换视角。
 */
public final class SeekerCameraCycler {
    /**
     * Body age at the last cycle request or viewpoint bind; -1 = never.
     * 上次发送切换请求或绑定视点时的本体 age；-1 表示从未发生。
     */
    private static long lastSendAge = -1L;
    /**
     * Last tick's held state of the left/right keys; true after a bind until the key is seen released.
     * 左右键上一刻的按住状态；绑定后保持为 true，直到观察到该键松开。
     */
    private static boolean leftWasDown = true;
    private static boolean rightWasDown = true;

    private SeekerCameraCycler() {
    }

    /**
     * Called by {@link SeekerRemoteViewClient} whenever it binds a viewpoint (session start or atomic switch): drain
     * the queued left/right presses, require both keys to be released before they cycle, and restart the send spacing
     * from now, because the server's open throttle restarted when it opened this session.
     * 每当 {@link SeekerRemoteViewClient} 绑定视点（会话开始或原子切换）时调用：清空左右键积压的按下次数，要求两个键先松开
     * 才能切换，并从现在起重新计算发送间隔，因为服务端打开本会话时其打开节流也重新开始计时。
     */
    static void rebind(MinecraftClient client, @Nullable ClientPlayerEntity player) {
        if (client.options != null) {
            ((SeekerRemoteKeyDrain) client.options.leftKey).sparkwitch$drainPresses();
            ((SeekerRemoteKeyDrain) client.options.rightKey).sparkwitch$drainPresses();
        }
        leftWasDown = true;
        rightWasDown = true;
        lastSendAge = player == null ? -1L : player.age;
    }

    static void tick(MinecraftClient client, ClientPlayerEntity player, SeekerSessionMode activeMode) {
        KeyBinding left = client.options.leftKey;
        KeyBinding right = client.options.rightKey;
        boolean leftQueued = left.wasPressed();
        boolean rightQueued = right.wasPressed();
        // One step per tick at most; never replay queued presses. / 每刻最多切换一步；绝不重放积压的按键。
        ((SeekerRemoteKeyDrain) left).sparkwitch$drainPresses();
        ((SeekerRemoteKeyDrain) right).sparkwitch$drainPresses();
        boolean leftDown = left.isPressed();
        boolean rightDown = right.isPressed();
        boolean previous = SeekerRemoteViewRules.freshPress(leftWasDown, leftDown, leftQueued);
        boolean next = SeekerRemoteViewRules.freshPress(rightWasDown, rightDown, rightQueued);
        leftWasDown = leftDown;
        rightWasDown = rightDown;
        int direction = direction(previous, next);
        if (direction == 0 || activeMode != SeekerSessionMode.CAMERA
                || SeekerClientState.sessionMode() != SeekerSessionMode.CAMERA
                || !SeekerConsoleRules.throttleElapsed(player.age, lastSendAge,
                SeekerRemoteViewRules.CAMERA_CYCLE_THROTTLE_TICKS)
                || !ClientPlayNetworking.canSend(SeekerRemoteOpenC2SPacket.ID)) {
            return;
        }
        int target = SeekerCameraRules.cycle(SeekerClientState.cameras(), SeekerClientState.sessionFocusEntityId(),
                direction, selectable(client.world, player));
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
     * The cameras the strafe keys may land on: resolvable on this client and within the session radius of the body
     * (the server's own horizontal check). Also used by the CCTV overlay's "2/3" hint.
     * 左右键可切换到的摄像头：可在本客户端解析，且位于本体的会话半径内（与服务端相同的水平判定）。CCTV 叠加层的“2/3”提示也使用它。
     */
    static IntPredicate selectable(@Nullable ClientWorld world, ClientPlayerEntity player) {
        int radius = SeekerClientState.effectiveRadius();
        return entityId -> {
            Entity entity = world == null ? null : world.getEntityById(entityId);
            return entity instanceof SeekerCameraEntity camera && camera.isAlive()
                    && SeekerRemoteRules.withinEffectiveRadius(player.getPos(), camera.getPos(), radius);
        };
    }
}
