package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:bear_smoke_clear} (S2C): no body. The client drops every cloud and hole it
 * knows (round end, world unload, before a resync on a dimension change or respawn). Stray bytes are skipped.
 * 稳定数据包契约 {@code sparkwitch:bear_smoke_clear}（S2C）：无正文。客户端丢弃已知的全部烟团与空洞（回合结束、世界卸载，
 * 以及切换维度或重生后重新同步之前）。多余字节会被跳过。
 */
public record SmokeClearS2CPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("bear_smoke_clear");
    public static final Id<SmokeClearS2CPayload> ID = new Id<>(PAYLOAD_ID);
    public static final SmokeClearS2CPayload INSTANCE = new SmokeClearS2CPayload();
    public static final PacketCodec<RegistryByteBuf, SmokeClearS2CPayload> CODEC =
            PacketCodec.of(SmokeClearS2CPayload::write, SmokeClearS2CPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static SmokeClearS2CPayload read(PacketByteBuf buf) {
        buf.skipBytes(buf.readableBytes());
        return INSTANCE;
    }
}
