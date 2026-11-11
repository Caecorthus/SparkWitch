package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * The smallest synced state of the Rope strangle, component {@code sparkwitch:rope_hold}: which side of a running
 * strangle its owner is on. It exists only so the owner's client can lock its own input (through
 * {@link RopeStrangleHold} and the Control Expert stun seam); the stun's own component cannot carry it without also
 * reading as a real Taser stun. Synced to the owner only, never saved (a relog always starts free), never copied on
 * respawn. Server authority is {@link RopeStrangleService}'s session table, which writes this field when a strangle
 * starts and zeroes it whenever one ends; the zero sync is what unlocks the client.
 * 绳索勒杀所需的最小同步状态，组件 {@code sparkwitch:rope_hold}：拥有者处于进行中勒杀的哪一方。它只为让拥有者的客户端锁住自己的
 * 输入而存在（经 {@link RopeStrangleHold} 与控场专家眩晕接缝）；眩晕自身的组件无法在不被当成真正电击眩晕的前提下承载它。只同步给
 * 本人，不保存（重新登录后必定自由），重生时不复制。服务端权威是 {@link RopeStrangleService} 的会话表：勒杀开始时写入该字段，任何结束
 * 都会清零；清零同步才会解除客户端锁定。
 */
public final class RopeHoldComponent implements AutoSyncedComponent {
    public static final ComponentKey<RopeHoldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("rope_hold"),
            RopeHoldComponent.class
    );

    /** The owner's part in a running strangle. / 拥有者在进行中勒杀里的角色。 */
    public enum Side {
        NONE,
        STRANGLER,
        STRANGLED;

        static Side byIndex(int index) {
            Side[] values = values();
            return index >= 0 && index < values.length ? values[index] : NONE;
        }
    }

    private final PlayerEntity player;
    private Side side = Side.NONE;

    public RopeHoldComponent(PlayerEntity player) {
        this.player = player;
    }

    public Side side() {
        return side;
    }

    /** Server only: writes the side and syncs the owner when it changed. / 仅服务端：写入并在变化时同步给本人。 */
    void set(Side next) {
        Side value = next == null ? Side.NONE : next;
        if (side == value) {
            return;
        }
        side = value;
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Transient on purpose: a saved hold could outlive its server-side session. / 有意不保存：存档中的定身可能比服务端会话活得更久。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        side = Side.NONE;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeByte(side.ordinal());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        side = Side.byIndex(buf.readByte());
    }
}
