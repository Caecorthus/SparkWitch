package dev.caecorthus.sparkwitch.client.magician;

import com.mojang.authlib.GameProfile;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owner decision D6 (2026-10-07): each viewer sees a Magician puppet exactly as they would see the player it copies.
 * Presentation queries (skin, name label, instinct colour) therefore run against a stand-in player instead of the
 * puppet: the copied player itself when this client has it loaded (the local player included), otherwise a detached
 * client-only copy built from the copied UUID. The copy is never added to the world, never ticked and never sent
 * anywhere; it follows the puppet's position and reads the live player-list entry (Wathe's cached entry once the
 * player left), so skin, team-decorated name and game mode match. Its own components are defaults, so per-entity state
 * of an unloaded player (psycho, Wraith, morph) is not mirrored. The puppet owner is server-only and never used here.
 * 所有者决定 D6（2026-10-07）：每位观察者看到的魔术师皮套与其复制的玩家完全一致。因此外观类查询（皮肤、名牌、本能颜色）
 * 改为对“替身玩家”执行：本客户端已加载被复制玩家时（含本地玩家）直接用该玩家，否则用按被复制 UUID 构造的仅客户端
 * 分离副本。副本从不加入世界、从不 tick、从不发送；它跟随皮套位置，并读取实时玩家列表条目（玩家离线后改读 Wathe
 * 缓存），因此皮肤、带队伍修饰的名字与游戏模式一致。其组件均为默认值，未加载玩家的实体级状态（疯魔、冤魂、变形）
 * 不会被镜像。皮套主人仅存在于服务端，此处从不使用。
 */
public final class MagicianPuppetStandIn {
    // Detached copies by puppet entity id, for one client world at a time. / 按皮套实体 id 缓存的分离副本，仅限当前客户端世界。
    private static final Map<Integer, DetachedCopy> COPIES = new HashMap<>();
    private static WeakReference<ClientWorld> copiesWorld = new WeakReference<>(null);

    private MagicianPuppetStandIn() {
    }

    /**
     * The player a puppet stands for on this client, or null for anything that is not a client-side puppet with a
     * copied UUID. / 皮套在本客户端代表的玩家；非客户端皮套或没有复制 UUID 时返回 null。
     */
    public static @Nullable AbstractClientPlayerEntity of(@Nullable Entity entity) {
        if (!(entity instanceof MagicianPlaybackEntity puppet)
                || !(puppet.getWorld() instanceof ClientWorld world)) {
            return null;
        }
        UUID copied = puppet.disguise();
        if (copied == null) {
            return null;
        }
        if (world.getPlayerByUuid(copied) instanceof AbstractClientPlayerEntity loaded && !loaded.isRemoved()) {
            return loaded;
        }
        return detachedCopy(world, puppet, copied);
    }

    /** Whether {@code player} is a detached copy rather than a world player. / 是否为分离副本而非世界中的玩家。 */
    public static boolean isDetachedCopy(@Nullable PlayerEntity player) {
        return player instanceof DetachedCopy;
    }

    private static DetachedCopy detachedCopy(ClientWorld world, MagicianPlaybackEntity puppet, UUID copied) {
        if (copiesWorld.get() != world) {
            COPIES.clear();
            copiesWorld = new WeakReference<>(world);
        }
        DetachedCopy copy = COPIES.get(puppet.getId());
        if (copy == null || !copied.equals(copy.getUuid())) {
            // Drop copies of puppets that left the world before caching a new one. / 缓存新副本前清掉已离开世界的皮套副本。
            COPIES.keySet().removeIf(id -> !(world.getEntityById(id) instanceof MagicianPlaybackEntity));
            copy = new DetachedCopy(world, new GameProfile(copied, profileName(copied, puppet)));
            COPIES.put(puppet.getId(), copy);
        }
        copy.follow(puppet);
        return copy;
    }

    private static String profileName(UUID copied, MagicianPlaybackEntity puppet) {
        PlayerListEntry entry = playerListEntry(copied);
        if (entry != null) {
            return entry.getProfile().getName();
        }
        String synced = puppet.disguiseName();
        return synced == null ? "" : synced;
    }

    /** Live entry first, then Wathe's cache of departed players. / 先取实时条目，再取 Wathe 对离线玩家的缓存。 */
    private static @Nullable PlayerListEntry playerListEntry(UUID uuid) {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry live = handler == null ? null : handler.getPlayerListEntry(uuid);
        return live != null ? live : WatheClient.PLAYER_ENTRIES_CACHE.get(uuid);
    }

    /**
     * Client-only, never spawned copy of the copied player. / 被复制玩家的仅客户端、从不生成的副本。
     */
    private static final class DetachedCopy extends OtherClientPlayerEntity {
        private DetachedCopy(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        private void follow(MagicianPlaybackEntity puppet) {
            refreshPositionAndAngles(puppet.getX(), puppet.getY(), puppet.getZ(), puppet.getYaw(), puppet.getPitch());
            setHeadYaw(puppet.getHeadYaw());
            setBodyYaw(puppet.getBodyYaw());
        }

        @Override
        protected @Nullable PlayerListEntry getPlayerListEntry() {
            return playerListEntry(getUuid());
        }
    }
}
