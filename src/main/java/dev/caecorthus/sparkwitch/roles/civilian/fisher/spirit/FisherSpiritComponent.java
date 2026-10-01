package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stable CCA contract {@code sparkwitch:fisher_spirit} (NEVER_COPY, never saved): the Glimmerfish window. Every client
 * must know whether a player is glimmering (two-sided collision exemption, held-item hiding, instinct skip), so the
 * active flag is synced to everyone; only the owner receives remaining ticks. Client countdown never clears the flag.
 * 稳定 CCA 契约 {@code sparkwitch:fisher_spirit}（NEVER_COPY，不存盘）：灵光鱼窗口。每个客户端都必须知道某玩家
 * 是否处于灵光中（双向无碰撞、隐藏手持物、跳过本能描边），因此只把"灵光中"标记同步给所有人，钓鱼佬的其他信息不同步。
 * 只有拥有者接收剩余刻；客户端倒计时永不清除激活标记。
 *
 * <p>Packet: one VarInt, 0 = inactive, 1 = active observer, remaining + 1 = active owner. Server match, dimension,
 * safe position and effect provenance are neither synced nor saved.
 * 数据包：单个 VarInt，0 = 未激活，1 = 观察者激活标记，剩余刻 + 1 = 拥有者激活态。对局、维度、安全点及效果归属不发送、不存盘。</p>
 */
public final class FisherSpiritComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<FisherSpiritComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("fisher_spirit"), FisherSpiritComponent.class);

    private final PlayerEntity player;
    final FisherSpiritWindow window = new FisherSpiritWindow();
    @Nullable String matchId;
    @Nullable ServerWorld startedWorld;
    @Nullable Vec3d lastSafePosition;
    @Nullable FisherInvisibility invisibility;

    public FisherSpiritComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return window.isActive();
    }

    /** Meaningful on the server and on the owner's client (HUD). / 在服务端与拥有者客户端（HUD）上有意义。 */
    public int remainingTicks() {
        return window.remainingTicks();
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer && isActive()) {
            FisherSpiritService.tick(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        window.tickClient();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(window.encode(recipient == player));
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        window.applySync(buf.readVarInt());
    }

    void sync() {
        KEY.sync(player);
    }

    void clear() {
        window.clear();
        matchId = null;
        startedWorld = null;
        lastSafePosition = null;
        invisibility = null;
        sync();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
