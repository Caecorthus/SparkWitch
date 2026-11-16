package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty request for 二阶堂希罗's 死亡回归 (ability key G); the server owns every check ({@link HiroReturnService#use}).
 * 二阶堂希罗「死亡回归」（技能键 G）的空请求；全部校验在服务端（{@link HiroReturnService#use}）。
 */
public record UseHiroSkillC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("use_hiro_skill");
    public static final Id<UseHiroSkillC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UseHiroSkillC2SPacket> CODEC =
            PacketCodec.of(UseHiroSkillC2SPacket::write, UseHiroSkillC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UseHiroSkillC2SPacket read(PacketByteBuf buf) {
        return new UseHiroSkillC2SPacket();
    }
}
