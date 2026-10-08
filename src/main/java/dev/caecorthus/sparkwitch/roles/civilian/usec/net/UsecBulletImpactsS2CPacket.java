package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable packet contract {@code sparkwitch:usec_bullet_impacts} (S2C, players near the impact): {@code count:varint},
 * then per impact {@code pos:long, stage:byte}. Purely visual block cracks (D13): the client draws them with reserved
 * fake breaker ids and never changes a real block. Stages are clamped to the vanilla 0..9 breaking range and the list
 * is capped at {@link #MAX_IMPACTS} on both sides.
 * 稳定数据包契约 {@code sparkwitch:usec_bullet_impacts}（S2C，发给命中点附近的玩家）：纯视觉的方块裂痕（D13），
 * 客户端用保留的假破坏者 id 绘制，从不改动真实方块。级别被钳制到原版 0..9 破坏范围，列表在两端都截断到
 * {@link #MAX_IMPACTS}。
 */
public record UsecBulletImpactsS2CPacket(List<Impact> impacts) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = UsecRules.IMPACTS_PAYLOAD_ID;
    public static final Id<UsecBulletImpactsS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UsecBulletImpactsS2CPacket> CODEC =
            PacketCodec.of(UsecBulletImpactsS2CPacket::write, UsecBulletImpactsS2CPacket::read);
    /** More than any one shot can crack. / 超过任何一枪能产生的裂痕数。 */
    public static final int MAX_IMPACTS = 64;
    public static final int MIN_STAGE = 0;
    public static final int MAX_STAGE = 9;

    /** One cracked block and its crack stage (0..9). / 一个出现裂痕的方块及其裂痕级别（0..9）。 */
    public record Impact(BlockPos pos, int stage) {
        public Impact {
            pos = pos.toImmutable();
            stage = Math.max(MIN_STAGE, Math.min(MAX_STAGE, stage));
        }
    }

    public UsecBulletImpactsS2CPacket {
        impacts = impacts == null ? List.of()
                : List.copyOf(impacts.size() > MAX_IMPACTS ? impacts.subList(0, MAX_IMPACTS) : impacts);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(impacts.size());
        for (Impact impact : impacts) {
            buf.writeLong(impact.pos().asLong());
            buf.writeByte(impact.stage());
        }
    }

    public static UsecBulletImpactsS2CPacket read(PacketByteBuf buf) {
        int count = Math.max(0, Math.min(MAX_IMPACTS, buf.readVarInt()));
        List<Impact> impacts = new ArrayList<>(count);
        for (int i = 0; i < count && buf.readableBytes() >= Long.BYTES + 1; i++) {
            impacts.add(new Impact(BlockPos.fromLong(buf.readLong()), buf.readByte()));
        }
        // Drop anything past the cap or a truncated tail. / 丢弃超出上限的部分或残缺尾部。
        buf.skipBytes(buf.readableBytes());
        return new UsecBulletImpactsS2CPacket(impacts);
    }
}
