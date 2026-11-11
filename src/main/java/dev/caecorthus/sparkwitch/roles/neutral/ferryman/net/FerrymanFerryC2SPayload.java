package dev.caecorthus.sparkwitch.roles.neutral.ferryman.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S {@code sparkwitch:ferryman_ferry}: the secondary key was pressed on the body under the crosshair. The id is a
 * claim only; the server re-checks the body, its age, the reach and the role before paying anything.
 * C2S {@code sparkwitch:ferryman_ferry}：在准星所指遗体上按下第二技能键。id 仅是声明；服务端在支付前会重新校验
 * 遗体、死亡时间、距离与职业。
 */
public record FerrymanFerryC2SPayload(int bodyEntityId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("ferryman_ferry");
    public static final Id<FerrymanFerryC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FerrymanFerryC2SPayload> CODEC =
            PacketCodec.of(FerrymanFerryC2SPayload::write, FerrymanFerryC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(FerrymanFerryC2SPayload payload, PacketByteBuf buf) {
        buf.writeVarInt(payload.bodyEntityId);
    }

    private static FerrymanFerryC2SPayload read(PacketByteBuf buf) {
        return new FerrymanFerryC2SPayload(buf.readVarInt());
    }
}
