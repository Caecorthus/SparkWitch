package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMatch;
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
 * Owner-private, match-bound disguise state ({@code sparkwitch:black_raven_disguise}, NEVER_COPY).
 * Only the owner receives the sync (not even spectators); applying it updates the client acting index.
 * 仅拥有者可见、绑定对局的伪装状态（sparkwitch:black_raven_disguise，NEVER_COPY）。
 * 只有拥有者接收同步（旁观者也不接收）；应用同步时更新客户端扮演索引。
 */
public final class BlackRavenDisguiseComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<BlackRavenDisguiseComponent> KEY = ComponentRegistry.getOrCreate(
            BlackRavenDisguiseRules.DISGUISE_ID,
            BlackRavenDisguiseComponent.class
    );

    private final PlayerEntity player;
    private final BlackRavenDisguiseState state = new BlackRavenDisguiseState();
    private BlackRavenDisguiseSyncCodec.View clientView = BlackRavenDisguiseSyncCodec.View.EMPTY;

    public BlackRavenDisguiseComponent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity player() {
        return player;
    }

    /** Server-authoritative state. / 服务端权威状态。 */
    public BlackRavenDisguiseState state() {
        return state;
    }

    /** Owner client: last synced view with locally decremented countdowns. / 拥有者客户端：最近同步视图。 */
    public BlackRavenDisguiseSyncCodec.View clientView() {
        return clientView;
    }

    public void sync() {
        if (!player.getWorld().isClient) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            BlackRavenDisguiseService.tick(serverPlayer, this);
        }
    }

    /** Owner countdowns tick down locally between syncs (Bell Ringer precedent). / 两次同步之间在本地递减倒计时。 */
    @Override
    public void clientTick() {
        BlackRavenDisguiseSyncCodec.View view = clientView;
        if (view.unlockRemaining() > 0 || view.cooldownRemaining() > 0) {
            clientView = new BlackRavenDisguiseSyncCodec.View(
                    view.bound(),
                    view.acting(),
                    Math.max(0, view.unlockRemaining() - 1),
                    Math.max(0, view.cooldownRemaining() - 1),
                    view.pool(),
                    view.ravenBalance()
            );
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        BlackRavenDisguiseSyncCodec.write(buf, state.toView(BlackRavenMatch.currentId(), player.getWorld().getTime()));
    }

    /**
     * Owner client only (shouldSyncWith): the synced acting id becomes the local acting overlay entry.
     * 仅拥有者客户端：同步来的扮演 id 写入本地扮演覆盖层。
     */
    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        BlackRavenDisguiseSyncCodec.View view = BlackRavenDisguiseSyncCodec.read(buf);
        clientView = view;
        BlackRavenActingRole.updateClient(
                player.getUuid(),
                BlackRavenActingRole.resolveRole(view.bound() ? view.acting() : null)
        );
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.writeNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.readNbt(tag, registryLookup);
    }
}
