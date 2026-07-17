package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
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

/**
 * Data-only owner of synchronized and persistent Wraith lifecycle state.
 * 仅负责保存与同步冤魂生命周期状态，不承载玩法逻辑。
 */
public final class WraithPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<WraithPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("wraith_player"),
            WraithPlayerComponent.class
    );

    private final @Nullable Object provider;
    private final WraithPlayerState state = new WraithPlayerState();
    private boolean canonicalDataPresent;
    private boolean legacyImportCompleted;

    public WraithPlayerComponent(@Nullable Object provider) {
        this.provider = provider;
    }

    public boolean isActive() {
        ensureLegacyImported();
        return state.isActive();
    }

    public boolean isRestricted() {
        ensureLegacyImported();
        return state.isRestricted();
    }

    public boolean isPromoted() {
        ensureLegacyImported();
        return state.isPromoted();
    }

    public int getCompletedTasks() {
        ensureLegacyImported();
        return state.getCompletedTasks();
    }

    public WraithState.Alignment getAlignment() {
        ensureLegacyImported();
        return state.getAlignment();
    }

    public boolean isPromotionPending() {
        ensureLegacyImported();
        return state.isPromotionPending();
    }

    public void activate(WraithState.Alignment alignment) {
        canonicalDataPresent = true;
        legacyImportCompleted = true;
        state.activate(alignment);
        sync();
    }

    public int recordTaskCompletion() {
        ensureLegacyImported();
        if (!state.isActive()) {
            return state.getCompletedTasks();
        }
        int completedTasks = state.recordTaskCompletion();
        sync();
        return completedTasks;
    }

    public void setPromotionPending(boolean pending) {
        ensureLegacyImported();
        if (state.setPromotionPending(pending)) {
            sync();
        }
    }

    public void promote() {
        ensureLegacyImported();
        if (state.promote()) {
            sync();
        }
    }

    public void clear() {
        boolean changed = state.clear();
        clearLegacyState();
        canonicalDataPresent = false;
        legacyImportCompleted = false;
        if (changed) {
            sync();
        }
    }

    public void sync() {
        if (provider instanceof PlayerEntity player) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        ensureLegacyImported();
        boolean owner = recipient == provider;
        buf.writeBoolean(state.isActive());
        buf.writeBoolean(state.isRestricted());
        buf.writeVarInt(owner ? state.getCompletedTasks() : 0);
        buf.writeVarInt(owner && state.getAlignment() != null ? state.getAlignment().ordinal() : -1);
        buf.writeBoolean(owner && state.isPromotionPending());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        boolean syncedActive = buf.readBoolean();
        boolean syncedRestricted = buf.readBoolean();
        int syncedCompletedTasks = buf.readVarInt();
        int alignmentOrdinal = buf.readVarInt();
        boolean syncedPromotionPending = buf.readBoolean();

        WraithState.Alignment syncedAlignment = syncedActive
                && alignmentOrdinal >= 0
                && alignmentOrdinal < WraithState.Alignment.values().length
                ? WraithState.Alignment.values()[alignmentOrdinal]
                : null;
        state.restore(
                syncedActive,
                syncedRestricted,
                syncedCompletedTasks,
                syncedAlignment,
                syncedPromotionPending
        );
        canonicalDataPresent = true;
        legacyImportCompleted = true;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ensureLegacyImported();
        if (!state.isActive()) {
            return;
        }
        tag.putBoolean("WraithActive", true);
        tag.putBoolean("WraithRestricted", state.isRestricted());
        tag.putInt("WraithCompletedTasks", state.getCompletedTasks());
        if (state.getAlignment() != null) {
            tag.putString("WraithAlignment", state.getAlignment().name());
        }
        if (state.isPromotionPending()) {
            tag.putBoolean("WraithPromotionPending", true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        readState(tag);
        canonicalDataPresent = true;
        legacyImportCompleted = true;
    }

    /**
     * Canonical data wins even if CCA reads the legacy sibling first; import waits until first use.
     * 即使 CCA 先读旧组件，规范数据仍优先；旧数据只在首次使用时惰性导入。
     */
    private void ensureLegacyImported() {
        if (legacyImportCompleted || canonicalDataPresent) {
            legacyImportCompleted = true;
            return;
        }
        LegacyWraithPlayerComponent.KEY.maybeGet(provider).ifPresent(legacy -> {
            if (legacy.hasData()) {
                legacy.restoreInto(state);
            }
        });
        legacyImportCompleted = true;
    }

    private void clearLegacyState() {
        LegacyWraithPlayerComponent.KEY.maybeGet(provider).ifPresent(LegacyWraithPlayerComponent::clear);
    }

    private void readState(NbtCompound tag) {
        boolean active = tag.getBoolean("WraithActive");
        WraithState.Alignment alignment = null;
        if (active && tag.contains("WraithAlignment", NbtElement.STRING_TYPE)) {
            try {
                alignment = WraithState.Alignment.valueOf(tag.getString("WraithAlignment"));
            } catch (IllegalArgumentException ignored) {
                alignment = null;
            }
        }
        state.restore(
                active,
                tag.getBoolean("WraithRestricted"),
                tag.getInt("WraithCompletedTasks"),
                alignment,
                tag.getBoolean("WraithPromotionPending")
        );
    }
}
