package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * C2S intent {@code sparkwitch:savior_lightsaber_swing}: the client left-clicked while a lit lightsaber was in the main
 * hand. The server re-validates the weapon and then broadcasts the swing sound at the player's position, so nearby
 * players hear it with distance attenuation.
 * C2S 意图 {@code sparkwitch:savior_lightsaber_swing}：客户端在主手光剑点亮时左键。服务端重新确认武器后，在玩家位置
 * 广播挥动音，附近玩家可按距离衰减听到。
 */
public record SaviorLightsaberSwingC2SPayload() implements CustomPayload {
    public static final Id<SaviorLightsaberSwingC2SPayload> ID =
            new Id<>(SparkWitch.id("savior_lightsaber_swing"));
    public static final PacketCodec<RegistryByteBuf, SaviorLightsaberSwingC2SPayload> CODEC =
            PacketCodec.unit(new SaviorLightsaberSwingC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
