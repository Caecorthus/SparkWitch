package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stable CCA contract {@code sparkwitch:abyss_zone_exposure} (NEVER_COPY): how many more ticks a non-ally standing
 * on the Deep Dark Zone counts as exposed. Server-authoritative; synced only to the exposed player as remaining ticks
 * (see {@link AbyssZoneExposureState} for the sync rule), so no other client learns who stands in the zone. The
 * client reads it to draw the display-only pseudo task and to predict the boosted sanity drain. Never persisted.
 * 稳定 CCA 契约 {@code sparkwitch:abyss_zone_exposure}（NEVER_COPY）：站在深暗领域上的非队友还会被视为暴露多少刻。
 * 由服务端权威决定，仅以剩余刻数同步给被暴露的玩家本人（同步规则见 {@link AbyssZoneExposureState}），其他客户端无从得知
 * 谁站在领域里。客户端据此绘制仅用于显示的临时任务，并预测加速的理智下降。从不持久化。
 */
public final class AbyssZoneExposureComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<AbyssZoneExposureComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("abyss_zone_exposure"), AbyssZoneExposureComponent.class);

    private final PlayerEntity player;
    private final AbyssZoneExposureState state = new AbyssZoneExposureState();

    public AbyssZoneExposureComponent(PlayerEntity player) {
        this.player = player;
    }

    public int exposureTicks() {
        return state.exposureTicks();
    }

    public boolean isExposed() {
        return state.isExposed();
    }

    /** Server only; max with the current value, synced when exposure starts. / 仅服务端调用；与当前值取最大，暴露开始时同步。 */
    public void expose(int ticks) {
        if (state.expose(ticks)) {
            syncOwner();
        }
    }

    public void clear() {
        if (state.clear()) {
            syncOwner();
        }
    }

    @Override
    public void serverTick() {
        if (state.serverTick()) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        state.clientTick();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(state.exposureTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.restore(buf.readVarInt());
    }

    /** Exposure is never saved, so a relog or restart never strands a player in it. / 暴露从不保存，重登或重启都不会残留。 */
    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.restore(0);
    }

    private void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }
}
