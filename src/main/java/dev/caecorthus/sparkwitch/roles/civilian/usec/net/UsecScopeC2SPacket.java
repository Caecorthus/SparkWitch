package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Stable packet contract {@code sparkwitch:usec_scope} (C2S): {@code scoped:boolean}, the owner's scope state for the
 * lens glint others see (S1). Presentation only: the server still checks the held rifle before storing it in
 * {@code UsecPlayerComponent}. Decoding is tolerant: an empty payload decodes to "not scoped".
 * 稳定数据包契约 {@code sparkwitch:usec_scope}（C2S）：本人的开镜状态，用于他人看到的镜头反光（S1）。仅用于表现：
 * 服务端仍会先检查手持步枪，再写入 {@code UsecPlayerComponent}。解码是宽容的：空载荷解码为「未开镜」。
 */
public record UsecScopeC2SPacket(boolean scoped) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = UsecRules.SCOPE_PAYLOAD_ID;
    public static final Id<UsecScopeC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UsecScopeC2SPacket> CODEC =
            PacketCodec.of(UsecScopeC2SPacket::write, UsecScopeC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(scoped);
    }

    public static UsecScopeC2SPacket read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 1) {
            boolean scoped = buf.readBoolean();
            buf.skipBytes(buf.readableBytes());
            return new UsecScopeC2SPacket(scoped);
        }
        return new UsecScopeC2SPacket(false);
    }
}
