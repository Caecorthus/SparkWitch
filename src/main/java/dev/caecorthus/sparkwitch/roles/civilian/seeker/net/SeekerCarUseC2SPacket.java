package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.hit.BlockHitResult;

/**
 * Stable packet contract {@code sparkwitch:seeker_car_use} (C2S, owner while driving the car):
 * {@code sessionId:varint}, then vanilla's block-hit layout (block pos, side, hit offset, inside flag). The hit is only
 * the client's claim; {@code SeekerCarUseService} re-validates everything on the server thread.
 * 稳定数据包契约 {@code sparkwitch:seeker_car_use}（C2S，拥有者驾驶小车时发送）：{@code sessionId:varint}，
 * 随后是原版方块命中布局（方块坐标、面、命中偏移、内部标记）。命中只是客户端的声明；
 * {@code SeekerCarUseService} 会在服务端线程重新校验全部内容。
 */
public record SeekerCarUseC2SPacket(int sessionId, BlockHitResult hit) implements CustomPayload {
    public static final Id<SeekerCarUseC2SPacket> ID = new Id<>(SeekerRules.CAR_USE_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCarUseC2SPacket> CODEC =
            PacketCodec.of(SeekerCarUseC2SPacket::write, SeekerCarUseC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockHitResult(hit);
    }

    public static SeekerCarUseC2SPacket read(PacketByteBuf buf) {
        return new SeekerCarUseC2SPacket(buf.readVarInt(), buf.readBlockHitResult());
    }
}
