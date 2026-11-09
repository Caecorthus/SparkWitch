package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Cowardice (怯懦), granted by the ghost trigger. While cowardly, the player counts as sanity below 25 for every
 * Specter mechanic: the Specter can kill them in the Otherworld even if their real bar is full, they receive the
 * peachwood sword / Specter x-ray during the window, and the Specter sees a blue-purple outline.
 * <p>Sync contract: only the owner (their own low-sanity viewer rule) and players whose raw role is the Specter (the
 * outline) receive the flag, so nobody else learns who stepped on a trigger. CCA's start-tracking sync covers a
 * Specter who joins or comes into range later; a player who becomes the Specter mid-tracking gets every current flag
 * through {@link #resyncTo} on role assignment, which also overwrites flags their client kept from an earlier stint.
 * 鬼魂触发器给予的怯懦。怯懦期间对所有幽魂机制视为理智低于 25：即使真实理智条是满的，幽魂也能在里世界击杀；
 * 里世界期间能拿到桃木剑并透视幽魂；幽魂视角显示蓝紫色描边。
 * 同步契约：只有本人（其自身的低理智观察者规则）与真实职业为幽魂的玩家（描边）会收到该标记，其他人无法得知谁踩中了触发器。
 * 之后加入或进入范围的幽魂由 CCA 开始追踪时的同步覆盖；追踪期间才成为幽魂的玩家会在职业分配时经 {@link #resyncTo} 收到当前所有
 * 标记，同时覆盖其客户端在上一段幽魂身份期间残留的标记。
 */
public final class SpecterCowardiceComponent implements AutoSyncedComponent {
    public static final ComponentKey<SpecterCowardiceComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("specter_cowardice"),
            SpecterCowardiceComponent.class
    );
    private static final String COWARD = "Coward";

    private final PlayerEntity player;
    private boolean coward;

    public SpecterCowardiceComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isCoward() {
        return coward;
    }

    /** Server only: grants cowardice and syncs. / 仅服务端：获得怯懦并同步。 */
    public void grant() {
        if (coward) {
            return;
        }
        coward = true;
        sync();
    }

    /** Death, reset or round end clears cowardice. / 死亡、重置或回合结束清空怯懦。 */
    public void clear() {
        if (!coward) {
            return;
        }
        coward = false;
        sync();
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.sync(owner);
        }
    }

    /**
     * Server only: sends every player's current flag in the viewer's world to a newly assigned Specter (filtered by
     * {@link #shouldSyncWith}, so it is a no-op for any other role).
     * 仅服务端：把观察者所在世界每名玩家的当前标记发给新分配的幽魂（经 {@link #shouldSyncWith} 过滤，对其他职业为空操作）。
     */
    public static void resyncTo(ServerPlayerEntity viewer) {
        for (ServerPlayerEntity target : viewer.getServerWorld().getPlayers()) {
            if (target != viewer) {
                KEY.syncWith(viewer, (ComponentProvider) target);
            }
        }
    }

    /** Owner, or a player whose raw role is the Specter. / 本人，或真实职业为幽魂的玩家。 */
    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player
                || SpecterRules.isSpecter(GameWorldComponent.KEY.get(recipient.getWorld()).getRole(recipient));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (coward) {
            tag.putBoolean(COWARD, true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        coward = tag.getBoolean(COWARD);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(coward);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        coward = buf.readBoolean();
    }
}
