package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Stores the secret killer bond separately from the stable Wraith packet schema.
 * 将秘密凶手绑定独立保存，避免改变稳定的冤魂数据包结构。
 */
public final class VendettaPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<VendettaPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("vendetta"),
            VendettaPlayerComponent.class
    );

    private final PlayerEntity player;
    private final VendettaState state = new VendettaState();
    private boolean boundViewer;
    private @Nullable UUID clearingKillerUuid;

    public VendettaPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable UUID getBoundKillerUuid() {
        return state.boundKillerUuid();
    }

    public boolean isActive() {
        return state.isActive();
    }

    public int getRevealCooldownTicks() {
        return state.revealCooldownTicks();
    }

    public int getRevealActiveTicks() {
        return state.revealActiveTicks();
    }

    public boolean isRevealActive() {
        return state.isRevealActive();
    }

    public boolean isKnifeAvailable() {
        return state.isKnifeAvailable();
    }

    public boolean isBoundViewer() {
        return boundViewer;
    }

    public boolean isTimerPaused() {
        return state.isTimerPaused();
    }

    public void stageCreditedKiller(@Nullable UUID killerUuid) {
        state.stageCreditedKiller(killerUuid);
    }

    public boolean activateForPromotion() {
        if (!state.activate()) {
            return false;
        }
        sync();
        return true;
    }

    /** Called only after the bound killer's ordinary death reaches Wathe's confirmed AFTER seam. */
    public boolean consumeKnifeAfterConfirmedDeath() {
        if (!state.consumeKnife()) {
            return false;
        }
        sync();
        return true;
    }

    public void clear() {
        UUID killerUuid = state.boundKillerUuid();
        if (!state.clear()) {
            return;
        }
        boundViewer = false;
        clearingKillerUuid = killerUuid;
        sync();
        clearingKillerUuid = null;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            VendettaLifecycleService.tickOwner(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        if (state.isActive() && !state.isTimerPaused()) {
            state.tickTimer(true);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Only the owner receives the UUID/timers; the killer receives a marker for pair-owned presentation.
        // 只有持有者接收 UUID 与计时；凶手只接收用于双方表现的标记。
        if (state.isActive()) {
            return recipient == player || recipient.getUuid().equals(state.boundKillerUuid());
        }
        return clearingKillerUuid != null
                && (recipient == player || recipient.getUuid().equals(clearingKillerUuid));
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(state.isActive());
        if (!state.isActive()) {
            return;
        }
        boolean ownerView = recipient == player;
        buf.writeBoolean(ownerView);
        buf.writeBoolean(!ownerView);
        if (!ownerView) {
            return;
        }
        UUID killerUuid = state.boundKillerUuid();
        buf.writeBoolean(killerUuid != null);
        if (killerUuid != null) {
            buf.writeUuid(killerUuid);
        }
        buf.writeVarInt(state.revealCooldownTicks());
        buf.writeVarInt(state.revealActiveTicks());
        buf.writeBoolean(state.isTimerPaused());
        buf.writeBoolean(state.isKnifeAvailable());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        if (!buf.readBoolean()) {
            state.clear();
            boundViewer = false;
            return;
        }
        boolean ownerView = buf.readBoolean();
        boundViewer = buf.readBoolean();
        if (!ownerView) {
            state.restore(null, false, 0, 0);
            return;
        }
        UUID killerUuid = buf.readBoolean() ? buf.readUuid() : null;
        int cooldownTicks = buf.readVarInt();
        int revealTicks = buf.readVarInt();
        boolean paused = buf.readBoolean();
        boolean knifeAvailable = buf.readBoolean();
        state.restore(killerUuid, true, cooldownTicks, revealTicks, knifeAvailable);
        if (paused) {
            state.tickTimer(false);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        UUID killerUuid = state.boundKillerUuid();
        if (killerUuid != null) {
            tag.putUuid("BoundKiller", killerUuid);
        }
        tag.putBoolean("Active", state.isActive());
        tag.putBoolean("KnifeAvailable", state.isKnifeAvailable());
        if (state.revealCooldownTicks() > 0) {
            tag.putInt("RevealCooldownTicks", state.revealCooldownTicks());
        }
        if (state.revealActiveTicks() > 0) {
            tag.putInt("RevealActiveTicks", state.revealActiveTicks());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        UUID killerUuid = tag.containsUuid("BoundKiller") ? tag.getUuid("BoundKiller") : null;
        boolean active = tag.getBoolean("Active");
        int cooldownTicks = tag.contains("RevealCooldownTicks", NbtElement.NUMBER_TYPE)
                ? tag.getInt("RevealCooldownTicks") : 0;
        int revealTicks = tag.contains("RevealActiveTicks", NbtElement.NUMBER_TYPE)
                ? tag.getInt("RevealActiveTicks") : 0;
        boolean knifeAvailable = tag.getBoolean("KnifeAvailable");
        state.restore(killerUuid, active, cooldownTicks, revealTicks, knifeAvailable);
    }

    boolean tickTimer(boolean bothOnline) {
        return state.tickTimer(bothOnline);
    }

    void sync() {
        KEY.sync(player);
    }
}
