package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to the marked player: close an open inventory or container screen and refuse to open one for
 * {@code lockTicks}. Its id differs from the {@code sparkwitch:dominator_forced_shot} CCA component id.
 * 服务端发给被标记者：关闭已打开的物品栏或容器界面，并在 {@code lockTicks} 内拒绝再次打开。其 id 与
 * {@code sparkwitch:dominator_forced_shot} CCA 组件 id 不同。
 */
public record DominatorForcedShotS2CPacket(int lockTicks) implements CustomPayload {
    public static final Id<DominatorForcedShotS2CPacket> ID =
            new Id<>(SparkWitch.id("dominator_forced_shot_lock"));
    public static final PacketCodec<RegistryByteBuf, DominatorForcedShotS2CPacket> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(Math.max(0, value.lockTicks)),
            buf -> new DominatorForcedShotS2CPacket(Math.max(0, buf.readVarInt()))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
