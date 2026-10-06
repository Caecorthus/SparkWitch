package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import org.jetbrains.annotations.Nullable;

public record GrandWitchRecruitC2SPacket(@Nullable UUID targetId) implements CustomPayload {
    public static final Id<GrandWitchRecruitC2SPacket> ID = new Id<>(SparkWitch.id("recruit_accomplice"));
    public static final PacketCodec<RegistryByteBuf, GrandWitchRecruitC2SPacket> CODEC =
            PacketCodec.of(GrandWitchRecruitC2SPacket::write, GrandWitchRecruitC2SPacket::read);

    public GrandWitchRecruitC2SPacket() {
        this(null);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeBoolean(targetId != null);
        if (targetId != null) {
            buf.writeUuid(targetId);
        }
    }

    private static GrandWitchRecruitC2SPacket read(PacketByteBuf buf) {
        return new GrandWitchRecruitC2SPacket(buf.readBoolean() ? buf.readUuid() : null);
    }
}
