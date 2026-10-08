package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * USEC per-player state ({@code sparkwitch:usec_player}, {@code NEVER_COPY}, appended last in the CCA list), kept out
 * of the shared {@code sparkwitch:player} packet whose field order is frozen. Its only field, {@code scoped}, is synced
 * to every client because others draw the scope glint (S1). The server is the authority: only its scope service writes
 * it. It is transient and never saved.
 * USEC 每玩家状态（{@code sparkwitch:usec_player}，{@code NEVER_COPY}，追加在 CCA 列表末尾），不放进字段顺序已冻结的
 * 共享 {@code sparkwitch:player} 同步包。唯一字段 {@code scoped} 同步给所有客户端，因为其他人要绘制镜头反光（S1）。
 * 服务端是权威：只有其开镜服务写入。该状态是瞬时的，从不保存。
 */
public final class UsecPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<UsecPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            UsecRules.PLAYER_COMPONENT_ID, UsecPlayerComponent.class);

    private final PlayerEntity player;
    private boolean scoped;

    public UsecPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isScoped() {
        return scoped;
    }

    /** Server only; syncs only on a change. / 仅服务端调用；只在变化时同步。 */
    public void setScoped(boolean scoped) {
        if (this.scoped != scoped) {
            this.scoped = scoped;
            sync();
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(scoped);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        scoped = buf.readBoolean();
    }

    /** Transient: a reload always starts unscoped. / 瞬时状态：重新加载后总是未开镜。 */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        scoped = false;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
