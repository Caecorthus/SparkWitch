package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A dead round member's answer from the vote screen: {@code true} is 是, {@code false} is 否, for the window whose
 * serial the screen was opened for ({@code windowId}, so a late click can never land in a newer window). The server
 * accepts it only inside that live window and only from a voter; a later answer from the same player replaces the
 * earlier one. Replaces the old chat "1"/"2" vote (owner 2026-11-10: a GUI, not chat).
 * 本局死者在投票界面给出的回答：{@code true} 为「是」，{@code false} 为「否」，对应界面打开时所属窗口的序号
 * （{@code windowId}，迟到的点击因此绝不会落入更新的窗口）。服务端只在该窗口进行中、且发送者是投票者时接受；同一玩家后发的回答覆盖先前的回答。取代原先聊天栏发送「1」/「2」的投票方式（所有者 2026-11-10：用界面而非聊天）。
 */
public record SubmitMediumVoteC2SPacket(int windowId, boolean yes) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("submit_medium_vote");
    public static final Id<SubmitMediumVoteC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SubmitMediumVoteC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            SubmitMediumVoteC2SPacket::windowId,
            PacketCodecs.BOOL,
            SubmitMediumVoteC2SPacket::yes,
            SubmitMediumVoteC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
