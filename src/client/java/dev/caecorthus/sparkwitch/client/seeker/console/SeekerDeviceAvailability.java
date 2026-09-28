package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only device availability of the local Seeker, shared by the console screen and quick connect so both predict
 * the same "Control car" / "View camera" state. It reads the owner-synced {@code sparkwitch:seeker_status} and resolves
 * the device entity ids in the client world; every value is a presentation-only prediction and the server re-validates
 * each open request.
 * 本地搜寻者设备可用性的纯客户端计算，由控制台界面与快速连接共用，使两者对“操控小车”“查看摄像头”的预测一致。
 * 它读取仅同步给拥有者的 {@code sparkwitch:seeker_status}，并在客户端世界中解析设备实体 id；所有值都只是用于展示的
 * 预测，服务端会重新校验每个打开请求。
 */
public final class SeekerDeviceAvailability {
    private SeekerDeviceAvailability() {
    }

    /**
     * The owner's cameras as seen by this client: how many are placed, how many resolve alive in the client world, and
     * the squared horizontal distance to the nearest resolvable one ({@code -1} when none resolves or there is no
     * player).
     * 本客户端看到的拥有者摄像头：已放置数量、在客户端世界中可解析且存活的数量，以及到最近可解析摄像头的水平距离平方
     * （没有可解析的摄像头或没有玩家时为 {@code -1}）。
     */
    public record CameraSummary(int count, int resolvable, double nearestHorizontalDistanceSquared) {
    }

    /** "Control car" availability for the local player. / 本地玩家的“操控小车”可用性。 */
    public static SeekerConsoleRules.Availability car(@Nullable MinecraftClient client) {
        ClientPlayerEntity player = player(client);
        SeekerDeviceEntity car = resolve(client, SeekerClientState.carEntityId(), SeekerCarEntity.class);
        return SeekerConsoleRules.carAvailability(SeekerClientState.carState(), car != null,
                horizontalDistanceSquared(player, car), radius(client, SeekerSessionMode.CAR), blocked(player));
    }

    /**
     * "View camera" availability: usable while any camera is resolvable within the camera radius.
     * “查看摄像头”可用性：只要有任一摄像头可解析且位于摄像头半径内即可用。
     */
    public static SeekerConsoleRules.Availability camera(@Nullable MinecraftClient client) {
        CameraSummary summary = cameraSummary(client);
        return SeekerConsoleRules.cameraAvailability(summary.count(), summary.resolvable() > 0,
                summary.nearestHorizontalDistanceSquared(), radius(client, SeekerSessionMode.CAMERA),
                blocked(player(client)));
    }

    /** Remote recall availability (no radius, no resolution). / 远程回收可用性（无半径、无需解析）。 */
    public static SeekerConsoleRules.Availability recall(@Nullable MinecraftClient client) {
        return SeekerConsoleRules.recallAvailability(SeekerClientState.carState(), blocked(player(client)));
    }

    public static CameraSummary cameraSummary(@Nullable MinecraftClient client) {
        ClientPlayerEntity player = player(client);
        int count = 0;
        int resolvable = 0;
        double nearest = -1.0;
        for (SeekerState.Camera camera : SeekerClientState.cameras()) {
            count++;
            SeekerDeviceEntity entity = resolve(client, camera.entityId(), SeekerCameraEntity.class);
            if (entity == null) {
                continue;
            }
            resolvable++;
            double squared = horizontalDistanceSquared(player, entity);
            if (squared >= 0.0 && (nearest < 0.0 || squared < nearest)) {
                nearest = squared;
            }
        }
        return new CameraSummary(count, resolvable, nearest);
    }

    /** A stunned body cannot use the console (server: {@code remote.denied.stunned}). / 眩晕时不能使用控制台。 */
    public static boolean blocked(@Nullable ClientPlayerEntity player) {
        return player == null || SeekerControlExpertBridge.isStunned(player);
    }

    /**
     * Client estimate of the server's effective radius: the mode's maximum clamped to the negotiated view distance.
     * 服务端有效半径的客户端估计：模式上限，按协商后的视距钳制。
     */
    public static int radius(@Nullable MinecraftClient client, SeekerSessionMode mode) {
        int viewDistance = client == null ? 0 : client.options.getClampedViewDistance();
        return SeekerRules.effectiveRadius(SeekerRules.maxRadius(mode), viewDistance);
    }

    /**
     * Horizontal distance squared, the same measure as the server's radius check; {@code -1} when unknown.
     * 水平距离平方，与服务端半径判定的度量一致；未知时为 {@code -1}。
     */
    public static double horizontalDistanceSquared(@Nullable ClientPlayerEntity player, @Nullable Entity device) {
        if (player == null || device == null) {
            return -1.0;
        }
        double dx = device.getX() - player.getX();
        double dz = device.getZ() - player.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * The alive device of that type with that id in the client world, or null.
     * 客户端世界中该 id 且类型匹配的存活设备，否则为 null。
     */
    @Nullable
    public static SeekerDeviceEntity resolve(@Nullable MinecraftClient client, int entityId,
                                             Class<? extends SeekerDeviceEntity> type) {
        if (entityId < 0 || client == null || client.world == null) {
            return null;
        }
        Entity entity = client.world.getEntityById(entityId);
        return type.isInstance(entity) && entity.isAlive() ? type.cast(entity) : null;
    }

    @Nullable
    private static ClientPlayerEntity player(@Nullable MinecraftClient client) {
        return client == null ? null : client.player;
    }
}
