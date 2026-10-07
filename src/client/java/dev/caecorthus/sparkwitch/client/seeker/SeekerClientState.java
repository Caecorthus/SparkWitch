package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCooldownReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read helpers over the local player's own {@code sparkwitch:seeker_status}. Only the owner receives this component,
 * so every value here is about the local player; nothing is predicted.
 * 读取本地玩家自己的 {@code sparkwitch:seeker_status} 的辅助方法。该组件只同步给拥有者，
 * 因此这里的每个值都属于本地玩家；不做任何预测。
 */
public final class SeekerClientState {
    private SeekerClientState() {
    }

    @Nullable
    public static ClientPlayerEntity player() {
        return MinecraftClient.getInstance().player;
    }

    @Nullable
    public static SeekerStatusComponent own() {
        ClientPlayerEntity player = player();
        return player == null ? null : SeekerStatusComponent.KEY.getNullable(player);
    }

    /** The local player's current Wathe role is exactly the Seeker. / 本地玩家当前的 Wathe 职业恰为搜寻者。 */
    public static boolean isSeeker() {
        ClientPlayerEntity player = player();
        return player != null && SeekerRules.isSeeker(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static SeekerCarState carState() {
        SeekerStatusComponent status = own();
        return status == null ? SeekerCarState.NONE : status.carState();
    }

    public static SeekerSessionMode sessionMode() {
        SeekerStatusComponent status = own();
        return status == null ? SeekerSessionMode.NONE : status.sessionMode();
    }

    public static boolean isInSession() {
        return sessionMode() != SeekerSessionMode.NONE;
    }

    public static int sessionId() {
        SeekerStatusComponent status = own();
        return status == null ? 0 : status.sessionId();
    }

    public static int carEntityId() {
        SeekerStatusComponent status = own();
        return status == null ? -1 : status.carEntityId();
    }

    /** The local owner's cameras in label order (synced). / 本地拥有者按编号排列的摄像头（同步值）。 */
    public static List<SeekerState.Camera> cameras() {
        SeekerStatusComponent status = own();
        return status == null ? List.of() : status.cameras();
    }

    public static int cameraCount() {
        SeekerStatusComponent status = own();
        return status == null ? 0 : status.cameraCount();
    }

    public static boolean hasCamera(int entityId) {
        SeekerStatusComponent status = own();
        return status != null && status.hasCamera(entityId);
    }

    /**
     * The synced session focus: the car or the exact camera being viewed; -1 without a session.
     * 同步的会话焦点：小车或正在观看的那台摄像头；无会话时为 -1。
     */
    public static int sessionFocusEntityId() {
        SeekerStatusComponent status = own();
        return status == null ? -1 : status.state().sessionFocusEntityId();
    }

    public static int carBattery() {
        SeekerStatusComponent status = own();
        return status == null ? 0 : SeekerRules.clampBattery(status.carBattery());
    }

    public static SeekerCooldownReason cooldownReason() {
        SeekerStatusComponent status = own();
        return status == null ? SeekerCooldownReason.NONE : status.cooldownReason();
    }

    @Nullable
    public static UUID markTarget() {
        SeekerStatusComponent status = own();
        return status == null || status.markRemainingTicks() <= 0 ? null : status.markTarget();
    }

    public static int markRemainingTicks() {
        SeekerStatusComponent status = own();
        return status == null ? 0 : Math.max(0, status.markRemainingTicks());
    }
}
