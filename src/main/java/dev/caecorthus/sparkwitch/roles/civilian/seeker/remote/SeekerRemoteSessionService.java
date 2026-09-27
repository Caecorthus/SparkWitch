package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteCloseC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerRemoteOpenC2SPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Frozen contract: the server-owned remote-view session (open validation, per-tick exit checks, sprint clear, end).
 * The camera is switched only on the owner's client; this service never switches the server-side camera entity.
 * TODO(WP-09): implement. / 待 WP-09 实现。
 * 冻结契约：服务端持有的遥控视角会话（打开校验、逐刻退出检查、清除疾跑、结束）。
 * 相机只在拥有者客户端切换；本服务从不切换服务端相机实体。
 */
public final class SeekerRemoteSessionService {
    private SeekerRemoteSessionService() {
    }

    /**
     * Also registers the Q17 body push exemption ({@code SparkFactionApi.registerEntityCollisionExemption}: a player
     * in a Seeker session is not pushed).
     * 同时注册 Q17 本体推挤豁免（处于搜寻者会话中的玩家不被推挤）。
     */
    public static void register() {
        // TODO(WP-09) / 待 WP-09 实现
    }

    public static void handleOpen(ServerPlayerEntity player, SeekerRemoteOpenC2SPacket packet) {
    }

    /** Always accepted, even while stunned or feared; stale ids are ignored. / 始终接受，即使眩晕或恐惧；过期 id 忽略。 */
    public static void handleClose(ServerPlayerEntity player, SeekerRemoteCloseC2SPacket packet) {
    }

    /**
     * The one way any service ends a session, called BEFORE its own state transition: clears WP-09's
     * {@code SeekerSessionState}, applies {@code state.closeSession(reason)}, returns the car to idle, syncs, and sends
     * {@code reason.translationKey()} to the owner's action bar only when {@code reason.notifiesOwner()}. Idempotent; a
     * no-op when no session is open.
     * 任何服务结束会话的唯一途径，须在其自身状态转移之前调用：清除 WP-09 的会话状态、执行 closeSession、
     * 让小车回到空闲、同步，并仅在 {@code notifiesOwner()} 为真时向拥有者动作栏发送提示。幂等；无会话时为空操作。
     */
    public static void end(ServerPlayerEntity player, SeekerExitReason reason) {
    }

    /** Side-neutral: reads the (owner-synced) component and {@code SeekerRules.isLocked}. / 两端通用。 */
    public static boolean isLocked(PlayerEntity player) {
        return false;
    }

    /** Called from the component's server tick (step 3). / 由组件服务端刻调用（第 3 步）。 */
    public static void tick(ServerPlayerEntity player, SeekerStatusComponent component) {
    }
}
