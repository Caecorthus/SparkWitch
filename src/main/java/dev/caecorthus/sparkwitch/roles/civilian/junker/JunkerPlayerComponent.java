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
 * The 5-second corpse search and the search cooldown, synced to the owner only. The search cancels the moment any gate
 * of {@link JunkerSearchRules#keepsSearching} fails (state gates, aim, range or block line of sight). The cooldown is an
 * absolute ready tick in server world time; forced cooldowns reach it only through
 * {@code compat/cooldown/SparkWitchForcedCooldowns#ragpickerSearch}, which only moves it later.
 * 五秒搜刮与搜刮冷却，只同步给本人。{@link JunkerSearchRules#keepsSearching} 的任一门槛失败（状态门槛、瞄准、范围或
 * 方块视线）就立即取消搜刮。冷却是服务端世界时间的绝对就绪刻；其他功能的强制冷却只经
 * {@code compat/cooldown/SparkWitchForcedCooldowns#ragpickerSearch} 作用于它，且只会把它推后。
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
    /**
     * Absolute server world tick at which the search is ready again; synced to the owner only.
     * 搜刮再次可用的服务端世界时间绝对刻，只同步给本人。
     */
    private long searchReadyTick;

    public JunkerPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isSearching() {
        return searching || searchBodyUuid != null;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public long getSearchReadyTick() {
        return searchReadyTick;
    }

    /** Server only; syncs the owner. / 仅服务端调用，并同步给本人。 */
    public void setSearchReadyTick(long tick) {
        if (tick != searchReadyTick) {
            searchReadyTick = tick;
            sync();
        }
    }

    /** Remaining cooldown at {@code worldTime} (server or client world time). / 在 worldTime 时剩余的冷却刻数。 */
    public int searchCooldownRemainingTicks(long worldTime) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, searchReadyTick - worldTime));
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

    /**
     * Server only: a new round or a new ragpicker. Clears any search and sets the ready tick exactly (a leftover from
     * an earlier round must not carry over); syncs once.
     * 仅服务端：新回合或新成为拾荒者。清除进行中的搜刮并精确设置就绪刻（上一回合的残留不得延续）；只同步一次。
     */
    public void reset(long readyTick) {
        searchBodyUuid = null;
        progressTicks = 0;
        searching = false;
        searchReadyTick = readyTick;
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
        UUID bodyUuid = searchBodyUuid;
        // Every persistent gate and the line of sight are re-read each tick; the completing tick has passed them too.
        // 每刻重新读取全部持续门槛与视线；完成的那一刻同样已通过它们。
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !JunkerService.keepsSearching(serverPlayer, bodyUuid)) {
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
        buf.writeLong(searchReadyTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        searching = buf.readBoolean();
        progressTicks = buf.readInt();
        searchReadyTick = buf.readLong();
    }

    /**
     * Only the ready tick is saved (world time survives a relog or restart, so a relog cannot skip the cooldown); a
     * search in progress is never resumed.
     * 只保存就绪刻（世界时间在重新登录或重启后仍延续，因此重新登录无法跳过冷却）；进行中的搜刮从不恢复。
     */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        searchBodyUuid = null;
        progressTicks = 0;
        searching = false;
        searchReadyTick = tag.getLong("searchReadyTick");
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putLong("searchReadyTick", searchReadyTick);
    }
}
