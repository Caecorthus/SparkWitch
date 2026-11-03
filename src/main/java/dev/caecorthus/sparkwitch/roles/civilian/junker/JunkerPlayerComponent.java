package dev.caecorthus.sparkwitch.roles.civilian.junker;

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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * The 5-second corpse search: which body is being searched and how far along it is. Cancels the moment the body leaves
 * the player's sight.
 * 五秒搜刮：正在搜哪具尸体、进度多少。尸体一旦离开视线立刻取消。
 */
public final class JunkerPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<JunkerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ragpicker_player"),
            JunkerPlayerComponent.class
    );

    private static final int SYNC_INTERVAL_TICKS = 4;

    private final PlayerEntity player;
    private UUID searchBodyUuid;
    private int progressTicks;
    private boolean searching;
    /** Remaining search cooldown in seconds, shown on the HUD. 搜刮冷却剩余秒数，显示在 HUD 上。 */
    private int cooldownSeconds;

    public JunkerPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isSearching() {
        return searching || searchBodyUuid != null;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** Server-driven; syncs only when the displayed second actually changes. 服务端驱动；只在显示的秒数变化时同步。 */
    public void setCooldownSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != cooldownSeconds) {
            cooldownSeconds = clamped;
            sync();
        }
    }

    public float getProgress() {
        return Math.min(1.0F, (float) progressTicks / JunkerRules.SEARCH_TICKS_REQUIRED);
    }

    public void startSearch(UUID bodyUuid) {
        searchBodyUuid = bodyUuid;
        progressTicks = 0;
        searching = true;
        sync();
    }

    public void cancelSearch() {
        if (isSearching() || progressTicks != 0) {
            searchBodyUuid = null;
            progressTicks = 0;
            searching = false;
            sync();
        }
    }

    public void reset() {
        searchBodyUuid = null;
        progressTicks = 0;
        searching = false;
        cooldownSeconds = 0;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void serverTick() {
        if (searchBodyUuid == null) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !dev.doctor4t.wathe.game.GameFunctions.isPlayerPlayingAndAlive(player)) {
            cancelSearch();
            return;
        }
        UUID bodyUuid = searchBodyUuid;
        if (!JunkerService.isLookingAtBody(serverPlayer, bodyUuid)) {
            cancelSearch();
            return;
        }
        progressTicks++;
        if (progressTicks >= JunkerRules.SEARCH_TICKS_REQUIRED) {
            searchBodyUuid = null;
            progressTicks = 0;
            searching = false;
            sync();
            JunkerService.completeSearch(serverPlayer, bodyUuid);
            return;
        }
        if (progressTicks % SYNC_INTERVAL_TICKS == 0) {
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(searchBodyUuid != null);
        buf.writeInt(progressTicks);
        buf.writeInt(cooldownSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        searching = buf.readBoolean();
        progressTicks = buf.readInt();
        cooldownSeconds = buf.readInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        progressTicks = tag.contains("progressTicks") ? tag.getInt("progressTicks") : 0;
        cooldownSeconds = 0;
        searchBodyUuid = tag.contains("searchBodyUuid") ? tag.getUuid("searchBodyUuid") : null;
        searching = searchBodyUuid != null;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("progressTicks", progressTicks);
        if (searchBodyUuid != null) {
            tag.putUuid("searchBodyUuid", searchBodyUuid);
        }
    }
}
