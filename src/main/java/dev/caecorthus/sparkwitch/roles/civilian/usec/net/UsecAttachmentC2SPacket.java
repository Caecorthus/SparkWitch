package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable packet contract {@code sparkwitch:usec_attachment} (C2S): {@code action, rifleSlot, itemSlot:int}, one
 * attachment-screen action ({@link UsecAttachmentAction} id) on player-inventory slots. For the LOOSE actions
 * {@code rifleSlot} holds the loose magazine's slot. The server re-validates every field; nothing here is trusted.
 * Decoding is tolerant: a short payload decodes to {@link #INVALID} (-1 everywhere) instead of disconnecting.
 * 稳定数据包契约 {@code sparkwitch:usec_attachment}（C2S）：对玩家背包栏位执行的一个配件界面动作
 * （{@link UsecAttachmentAction} id）。LOOSE 动作中 {@code rifleSlot} 存放散装弹匣的栏位。服务端复核每个字段，
 * 这里的任何内容都不被信任。解码是宽容的：残缺载荷解码为 {@link #INVALID}（全部为 -1），而不会断开连接。
 */
public record UsecAttachmentC2SPacket(int action, int rifleSlot, int itemSlot) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = UsecRules.ATTACHMENT_PAYLOAD_ID;
    public static final Id<UsecAttachmentC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UsecAttachmentC2SPacket> CODEC =
            PacketCodec.of(UsecAttachmentC2SPacket::write, UsecAttachmentC2SPacket::read);
    public static final UsecAttachmentC2SPacket INVALID = new UsecAttachmentC2SPacket(-1, -1, -1);

    public UsecAttachmentC2SPacket(UsecAttachmentAction action, int rifleSlot, int itemSlot) {
        this(action.id(), rifleSlot, itemSlot);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** The decoded action, or null for an unknown id. / 解码后的动作；未知 id 时为 null。 */
    public @Nullable UsecAttachmentAction resolvedAction() {
        return UsecAttachmentAction.fromId(action);
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(rifleSlot);
        buf.writeInt(itemSlot);
    }

    public static UsecAttachmentC2SPacket read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 3 * Integer.BYTES) {
            return new UsecAttachmentC2SPacket(buf.readInt(), buf.readInt(), buf.readInt());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return INVALID;
    }
}
