package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public record UseWitchSkillC2SPacket(Optional<UUID> targetUuid) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_skill");
    public static final Id<UseWitchSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseWitchSkillC2SPacket> CODEC =
            PacketCodec.of(UseWitchSkillC2SPacket::write, UseWitchSkillC2SPacket::read);

    public UseWitchSkillC2SPacket {
        targetUuid = targetUuid == null ? Optional.empty() : targetUuid;
    }

    public UseWitchSkillC2SPacket() {
        this(Optional.empty());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(targetUuid.isPresent());
        targetUuid.ifPresent(buf::writeUuid);
    }

    public static UseWitchSkillC2SPacket read(PacketByteBuf buf) {
        return new UseWitchSkillC2SPacket(buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty());
    }
}
