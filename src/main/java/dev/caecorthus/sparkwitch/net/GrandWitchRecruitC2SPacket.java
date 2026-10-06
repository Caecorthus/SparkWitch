package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Second skill key of the Grand Witch: "recruit now". It carries no target since recruitment picks the recruit on the
 * server (owner request 2026-10-06); the id is unchanged, so the Control Expert stun still blocks it.
 * 大魔女二技能键："立即招募"。招募对象由服务端选出（所有者 2026-10-06 要求），因此不携带目标；id 不变，控场专家眩晕仍会拦截它。
 */
public record GrandWitchRecruitC2SPacket() implements CustomPayload {
    public static final Id<GrandWitchRecruitC2SPacket> ID = new Id<>(SparkWitch.id("recruit_accomplice"));
    public static final PacketCodec<RegistryByteBuf, GrandWitchRecruitC2SPacket> CODEC =
            PacketCodec.unit(new GrandWitchRecruitC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
