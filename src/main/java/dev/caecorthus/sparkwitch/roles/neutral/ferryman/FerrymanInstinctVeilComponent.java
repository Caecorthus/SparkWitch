package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

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
 * The 5 s instinct veil that follows a feign death ({@code sparkwitch:ferryman_feign_veil}): every client must know
 * it, because instinct is drawn locally, so this one component syncs to everyone and carries only a remaining-tick
 * counter (never server time). The owner's client counts it down; nobody renders an instinct outline through it.
 * 假死结束后的 5 秒本能帷幕（{@code sparkwitch:ferryman_feign_veil}）：本能是各客户端本地绘制，因此这个组件同步给所有人，
 * 且只带剩余 tick（绝不含服务端时间）。拥有者客户端自行递减；在此期间任何人的本能都描不出这个人。
 */
public final class FerrymanInstinctVeilComponent implements AutoSyncedComponent, ClientTickingComponent {
    public static final ComponentKey<FerrymanInstinctVeilComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ferryman_feign_veil"),
            FerrymanInstinctVeilComponent.class
    );

    private final PlayerEntity player;
    private int serverTicks;
    private int clientTicks;

    public FerrymanInstinctVeilComponent(PlayerEntity player) {
        this.player = player;
    }

    public static FerrymanInstinctVeilComponent get(PlayerEntity player) {
        return KEY.get(player);
    }

    /** True while this player must not be outlined by instinct, on either side. / 该玩家在此期间不该被本能描边（两端一致）。 */
    public boolean isVeiled() {
        return player.getWorld().isClient ? clientTicks > 0 : serverTicks > 0;
    }

    /** Server: starts the veil and syncs it to everyone. / 服务端：开启帷幕并同步给所有人。 */
    public void veil(int ticks) {
        if (player.getWorld().isClient) {
            return;
        }
        serverTicks = Math.max(0, ticks);
        KEY.sync(player);
    }

    /**
     * Server: drops a running veil (round reset), so it never leaks into the next round; syncs only when one ran.
     * 服务端：撤掉正在进行的帷幕（换局），使其绝不会带进下一局；仅在确有帷幕时同步。
     */
    public void clear() {
        if (player.getWorld().isClient || serverTicks <= 0) {
            return;
        }
        serverTicks = 0;
        KEY.sync(player);
    }

    /** Server: one tick of the veil; syncs only when it ends. / 服务端：推进一格，仅在结束时同步。 */
    public void tickServer() {
        if (serverTicks <= 0) {
            return;
        }
        serverTicks--;
        if (serverTicks == 0) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        if (clientTicks > 0) {
            clientTicks--;
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(serverTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        clientTicks = buf.readVarInt();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Transient by design. / 刻意保持瞬时。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        serverTicks = 0;
        clientTicks = 0;
    }
}
