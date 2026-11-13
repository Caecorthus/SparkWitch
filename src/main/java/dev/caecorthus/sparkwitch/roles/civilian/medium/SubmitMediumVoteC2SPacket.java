package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A dead round member's vote from the vote screen, for the window whose serial the screen was opened for
 * ({@code windowId}, so a late click can never land in a newer window). {@code choice} is an option index of that
 * window's kind: {@link MediumRules#VOTE_YES} / {@link MediumRules#VOTE_NO} for a 通灵 question, a
 * {@link MediumBlessing} index for 幽灵的加护. The server accepts it only inside that live window, only from a voter,
 * and only when the window offers the choice (out of range or disabled is dropped); a later vote from the same player
 * replaces the earlier one. Replaces the old chat "1"/"2" vote (owner 2026-11-10: a GUI, not chat).
 * 本局死者在投票界面投出的一票，对应界面打开时所属窗口的序号（{@code windowId}，迟到的点击因此绝不会落入更新的窗口）。
 * {@code choice} 是该窗口种类下的选项序号：通灵提问为 {@link MediumRules#VOTE_YES} / {@link MediumRules#VOTE_NO}，
 * 幽灵的加护为 {@link MediumBlessing} 的序号。服务端只在该窗口进行中、发送者是投票者、且窗口提供该选项时接受（越界或已禁用
 * 的选项被丢弃）；同一玩家后投的票覆盖先前的票。取代原先聊天栏发送「1」/「2」的投票方式（所有者 2026-11-10：用界面而非聊天）。
 */
public record SubmitMediumVoteC2SPacket(int windowId, int choice) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("submit_medium_vote");
    public static final Id<SubmitMediumVoteC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SubmitMediumVoteC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            SubmitMediumVoteC2SPacket::windowId,
            PacketCodecs.VAR_INT,
            SubmitMediumVoteC2SPacket::choice,
            SubmitMediumVoteC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
