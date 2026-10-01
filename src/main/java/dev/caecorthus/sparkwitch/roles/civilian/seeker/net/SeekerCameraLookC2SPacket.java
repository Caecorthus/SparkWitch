package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Stable packet contract {@code sparkwitch:seeker_camera_look} (C2S): {@code sessionId:varint, yaw:float,
 * pitch:float}, the owner's camera view direction while viewing. Values are untrusted: the server drops non-finite
 * values and stale sessions, and wraps and clamps the rest into the viewed camera's cone (the latest packet wins).
 * Never blocked while viewing; like car moves it passes the stun and Fear lists because those end the session anyway.
 * 稳定数据包契约 {@code sparkwitch:seeker_camera_look}（C2S）：{@code sessionId:varint, yaw:float, pitch:float}，
 * 即拥有者观看时的摄像头视角。数值不可信：服务端丢弃非有限值与过期会话，其余的规范并钳制到所观看摄像头的锥角内（以最新的包为准）。
 * 观看期间从不拦截；与小车移动相同，它不在眩晕与恐惧名单内，因为这两者本身就会结束会话。
 */
public record SeekerCameraLookC2SPacket(int sessionId, float yaw, float pitch) implements CustomPayload {
    public static final Id<SeekerCameraLookC2SPacket> ID = new Id<>(SeekerRules.CAMERA_LOOK_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SeekerCameraLookC2SPacket> CODEC =
            PacketCodec.of(SeekerCameraLookC2SPacket::write, SeekerCameraLookC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static SeekerCameraLookC2SPacket read(PacketByteBuf buf) {
        return new SeekerCameraLookC2SPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }
}
