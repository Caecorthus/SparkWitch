package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Side-split index of living disguised Black Ravens, read by the acting overlay on
 * {@code GameWorldComponent#isRole(UUID, Role)} and by SparkWitch identity questions.
 * Server: a concurrent map written only by BlackRavenDisguiseService on the server thread and read from
 * any thread (the NoellesRoles voice plugin calls isRole off-thread). Client: the local player only,
 * written by the owner-only disguise component sync. The index never answers for getRole, faction,
 * win, legality, or serialization.
 * 存活伪装黑羽鸦的分端索引，供 isRole 扮演覆盖层与 SparkWitch 身份判断读取。
 * 服务端：并发表，仅由 BlackRavenDisguiseService 在服务端线程写入，任何线程均可读取（诺艾尔语音插件会在
 * 其他线程调用 isRole）。客户端：仅本地玩家，由仅拥有者可见的伪装组件同步写入。索引从不影响 getRole、
 * 阵营、胜负、合法性或序列化。
 */
public final class BlackRavenActingRole {
    private static final ConcurrentHashMap<UUID, Role> SERVER = new ConcurrentHashMap<>();
    private static final ClientChangeListener NO_LISTENER = (previous, current) -> { };
    private static volatile @Nullable ClientActing client;
    private static volatile ClientChangeListener clientListener = NO_LISTENER;

    private BlackRavenActingRole() {
    }

    /** The local player's acting role on the client. / 客户端本地玩家的扮演职业。 */
    private record ClientActing(UUID localUuid, Role acting) {
        public ClientActing {
            Objects.requireNonNull(localUuid, "localUuid");
            Objects.requireNonNull(acting, "acting");
        }
    }

    /** Installed by the client module; fires on enter, exit, and switch. / 客户端模块安装；进入、退出与切换时触发。 */
    @FunctionalInterface
    public interface ClientChangeListener {
        void onActingRoleChanged(@Nullable Role previous, @Nullable Role current);
    }

    /**
     * Overlay predicate: true only for the exact acting role of an indexed UUID on that side.
     * Callers still check the raw role and death state (see the M1 mixin).
     * 覆盖层判定：仅在该端已索引 UUID 的精确扮演职业时为 true；调用方仍需检查真实职业与死亡状态。
     */
    public static boolean matches(boolean isClient, @Nullable UUID uuid, @Nullable Role role) {
        if (uuid == null || role == null) {
            return false;
        }
        if (isClient) {
            ClientActing current = client;
            return current != null && current.acting() == role && current.localUuid().equals(uuid);
        }
        return !SERVER.isEmpty() && SERVER.get(uuid) == role;
    }

    /**
     * Full M1 overlay predicate over the raw state Wathe already holds: the index matches, the raw role is still
     * Black Raven, and the player is not dead (death-time readers therefore see the raw Black Raven).
     * M1 的完整覆盖层判定：索引匹配、真实职业仍为黑羽鸦且玩家未死亡（因此死亡时的读取看到的是真实黑羽鸦）。
     */
    public static boolean widens(
            boolean isClient,
            @Nullable UUID uuid,
            @Nullable Role role,
            @Nullable Role rawRole,
            boolean dead
    ) {
        return !dead && BlackRavenRules.isBlackRaven(rawRole) && matches(isClient, uuid, role);
    }

    /** Acting role of this player on its own side, or null. / 该玩家在其所在端的扮演职业，或 null。 */
    public static @Nullable Role actingRole(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        if (player.getWorld().isClient) {
            ClientActing current = client;
            return current != null && current.localUuid().equals(player.getUuid()) ? current.acting() : null;
        }
        return SERVER.get(player.getUuid());
    }

    public static @Nullable Identifier actingRoleId(@Nullable PlayerEntity player) {
        Role role = actingRole(player);
        return role == null ? null : role.identifier();
    }

    public static boolean isDisguised(@Nullable PlayerEntity player) {
        return actingRole(player) != null;
    }

    /** Resolves a registered Wathe role by id; null when unknown. / 按 id 解析已注册的 Wathe 职业；未知时为 null。 */
    public static @Nullable Role resolveRole(@Nullable Identifier roleId) {
        if (roleId == null) {
            return null;
        }
        for (Role role : WatheRoles.ROLES) {
            if (roleId.equals(role.identifier())) {
                return role;
            }
        }
        return null;
    }

    // Server writes: BlackRavenDisguiseService only, on the server thread. / 服务端写入：仅限服务端线程上的 BlackRavenDisguiseService。

    public static void putServer(UUID uuid, Role acting) {
        SERVER.put(Objects.requireNonNull(uuid, "uuid"), Objects.requireNonNull(acting, "acting"));
    }

    public static void removeServer(@Nullable UUID uuid) {
        if (uuid != null) {
            SERVER.remove(uuid);
        }
    }

    public static void clearServer() {
        SERVER.clear();
    }

    public static @Nullable Role serverActing(@Nullable UUID uuid) {
        return uuid == null ? null : SERVER.get(uuid);
    }

    // Client writes: the owner-only component sync and client disconnect. / 客户端写入：仅拥有者组件同步与断开连接。

    public static void updateClient(@Nullable UUID localUuid, @Nullable Role acting) {
        ClientActing previous = client;
        ClientActing next = localUuid == null || acting == null ? null : new ClientActing(localUuid, acting);
        client = next;
        Role before = previous == null ? null : previous.acting();
        Role after = next == null ? null : next.acting();
        if (before != after) {
            clientListener.onActingRoleChanged(before, after);
        }
    }

    public static void clearClient() {
        updateClient(null, null);
    }

    public static void setClientChangeListener(@Nullable ClientChangeListener listener) {
        clientListener = listener == null ? NO_LISTENER : listener;
    }
}
