package dev.caecorthus.sparkwitch.roles.neutral.ferryman.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S {@code sparkwitch:ferryman_pardon}: the shared ability key was pressed while a pardon window may be open. Carries
 * nothing; the server decides from its own window whether the press pardons the held kill.
 * C2S {@code sparkwitch:ferryman_pardon}：宽恕窗口可能开启期间按下了共享技能键。不带任何数据；是否宽恕由服务端依据
 * 自己的窗口判定。
 */
public record FerrymanPardonC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("ferryman_pardon");
    public static final Id<FerrymanPardonC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FerrymanPardonC2SPayload> CODEC =
            PacketCodec.of(FerrymanPardonC2SPayload::write, FerrymanPardonC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(FerrymanPardonC2SPayload payload, PacketByteBuf buf) {
        // No fields. / 无字段。
    }

    private static FerrymanPardonC2SPayload read(PacketByteBuf buf) {
        return new FerrymanPardonC2SPayload();
    }
}
