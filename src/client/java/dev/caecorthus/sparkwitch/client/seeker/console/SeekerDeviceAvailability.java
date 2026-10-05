package dev.caecorthus.sparkwitch.client.seeker.console;

import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only device availability of the local Seeker, shared by the console screen and quick connect so both predict
 * the same "Control car" / "View camera" state. Availability comes only from the owner-synced
 * {@code sparkwitch:seeker_status} (car DEPLOYED, camera list) and the stun gate: devices have no distance limit
 * (2026-10-04) and a far device is usually not tracked by this client, so entity presence never decides. Device
 * entities are resolved in the client world only for the optional distance shown in the status rows. Every value is a
 * presentation-only prediction and the server re-validates each open request.
 * 本地搜寻者设备可用性的纯客户端计算，由控制台界面与快速连接共用，使两者对“操控小车”“查看摄像头”的预测一致。
 * 可用性只来自仅同步给拥有者的 {@code sparkwitch:seeker_status}（小车已部署、摄像头列表）与眩晕门槛：设备没有距离限制
 * （2026-10-04），且远处的设备通常不被本客户端追踪，因此从不以实体是否存在作判断。只有状态行中可选的距离才会在客户端
 * 世界中解析设备实体。所有值都只是用于展示的预测，服务端会重新校验每个打开请求。
 */
public final class SeekerDeviceAvailability {
    private SeekerDeviceAvailability() {
    }

    /**
     * The owner's cameras as seen by this client: how many are placed (synced list) and the squared horizontal
     * distance to the nearest one this client tracks alive ({@code -1} when none is tracked or there is no player).
     * 本客户端看到的拥有者摄像头：已放置数量（同步列表），以及到本客户端追踪到的最近存活摄像头的水平距离平方
     * （一台都未追踪到或没有玩家时为 {@code -1}）。
     */
    public record CameraSummary(int count, double nearestHorizontalDistanceSquared) {
    }

    /** "Control car" availability for the local player. / 本地玩家的“操控小车”可用性。 */
    public static SeekerConsoleRules.Availability car(@Nullable MinecraftClient client) {
        return SeekerConsoleRules.carAvailability(SeekerClientState.carState(), blocked(player(client)));
    }

    /**
     * "View camera" availability: usable while any camera is placed, at any distance.
     * “查看摄像头”可用性：只要放置了任一摄像头即可用，距离不限。
     */
    public static SeekerConsoleRules.Availability camera(@Nullable MinecraftClient client) {
        return SeekerConsoleRules.cameraAvailability(SeekerClientState.cameras().size(), blocked(player(client)));
    }

    /** Remote recall availability (no entity resolution). / 远程回收可用性（无需解析实体）。 */
    public static SeekerConsoleRules.Availability recall(@Nullable MinecraftClient client) {
        return SeekerConsoleRules.recallAvailability(SeekerClientState.carState(), blocked(player(client)));
    }

    public static CameraSummary cameraSummary(@Nullable MinecraftClient client) {
        ClientPlayerEntity player = player(client);
        int count = 0;
        double nearest = -1.0;
        for (SeekerState.Camera camera : SeekerClientState.cameras()) {
            count++;
            SeekerDeviceEntity entity = resolve(client, camera.entityId(), SeekerCameraEntity.class);
            if (entity == null) {
                continue;
            }
            double squared = horizontalDistanceSquared(player, entity);
            if (squared >= 0.0 && (nearest < 0.0 || squared < nearest)) {
                nearest = squared;
            }
        }
        return new CameraSummary(count, nearest);
    }

    /** A stunned body cannot use the console (server: {@code remote.denied.stunned}). / 眩晕时不能使用控制台。 */
    public static boolean blocked(@Nullable ClientPlayerEntity player) {
        return player == null || SeekerControlExpertBridge.isStunned(player);
    }

    /**
     * Horizontal distance squared for the status rows; {@code -1} when unknown (no player, or the device is not
     * tracked by this client).
     * 状态行使用的水平距离平方；未知时为 {@code -1}（没有玩家，或本客户端未追踪该设备）。
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
