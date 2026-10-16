package dev.caecorthus.sparkwitch.roles.neutral.fiend;

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

/**
 * Feign-death cycle per player ({@code sparkwitch:fiend_feign}, {@code NEVER_COPY}, appended last in the CCA list).
 * Server authority: only {@link FiendFeignService} changes the {@link FiendFeignState} it holds. It syncs to the owner
 * alone, so no other client learns who the Fiend is or when its corpse is fake; the packet carries only a shown flag,
 * a feigning flag and the remaining ticks (to the next feign, or to the revival), never server time, the corpse or the
 * match id. The client counts down for its HUD only. Transient: never saved.
 * 每名玩家的假死周期（{@code sparkwitch:fiend_feign}，{@code NEVER_COPY}，追加在 CCA 列表末尾）。服务端权威：只有
 * {@link FiendFeignService} 修改其中的 {@link FiendFeignState}。只同步给本人，其他客户端无从得知谁是魔人、哪具尸体是假的；
 * 数据包只含显示标记、假死标记与剩余 tick（距下次假死或距复活），绝不含服务端时间、尸体或对局 id。客户端只为 HUD 倒数。
 * 瞬时状态：从不保存。
 */
public final class FiendFeignComponent implements AutoSyncedComponent, ClientTickingComponent {
    public static final ComponentKey<FiendFeignComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("fiend_feign"), FiendFeignComponent.class);

    private final PlayerEntity player;
    /** Server only. / 仅服务端。 */
    private final FiendFeignState state = new FiendFeignState();
    private boolean clientShown;
    private boolean clientFeigning;
    private int clientRemainingTicks;

    public FiendFeignComponent(PlayerEntity player) {
        this.player = player;
    }

    public static FiendFeignComponent get(PlayerEntity player) {
        return KEY.get(player);
    }

    FiendFeignState state() {
        return state;
    }

    /** Server only. / 仅服务端。 */
    void sync() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }

    /** Server: the real state; client: the synced flag. / 服务端读真实状态；客户端读同步标记。 */
    public boolean isFeigning() {
        return player.getWorld().isClient() ? clientFeigning : state.isFeigning();
    }

    /** Whether the owner's HUD has a countdown (server: a scheduled cycle). / 本人 HUD 是否显示倒计时（服务端：已排定周期）。 */
    public boolean isShown() {
        return player.getWorld().isClient() ? clientShown : state.isScheduled();
    }

    /** Ticks to the next feign or to the revival; 0 when not shown. / 距下次假死或距复活的 tick；不显示时为 0。 */
    public int remainingTicks() {
        if (player.getWorld().isClient()) {
            return clientRemainingTicks;
        }
        if (!state.isScheduled()) {
            return 0;
        }
        long now = player.getWorld().getTime();
        return FiendFeignRules.remaining(now, state.isFeigning() ? state.endTick() : state.dueTick());
    }

    @Override
    public void clientTick() {
        if (clientShown && clientRemainingTicks > 0) {
            clientRemainingTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean shown = isShown();
        buf.writeBoolean(shown);
        if (shown) {
            buf.writeBoolean(state.isFeigning());
            buf.writeVarInt(remainingTicks());
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        clientShown = buf.readBoolean();
        if (clientShown) {
            clientFeigning = buf.readBoolean();
            clientRemainingTicks = Math.max(0, buf.readVarInt());
        } else {
            clientFeigning = false;
            clientRemainingTicks = 0;
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        state.clear();
        clientShown = false;
        clientFeigning = false;
        clientRemainingTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
