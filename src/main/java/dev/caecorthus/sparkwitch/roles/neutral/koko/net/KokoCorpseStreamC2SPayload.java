package dev.caecorthus.sparkwitch.roles.neutral.koko.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Stable C2S contract {@code sparkwitch:koko_corpse_stream}: the second key; the server validates the body. */
public record KokoCorpseStreamC2SPayload() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = KokoRules.CORPSE_SKILL_ID;
    public static final Id<KokoCorpseStreamC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, KokoCorpseStreamC2SPayload> CODEC =
            PacketCodec.unit(new KokoCorpseStreamC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
