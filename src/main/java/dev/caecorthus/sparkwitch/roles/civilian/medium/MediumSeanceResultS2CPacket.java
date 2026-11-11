package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The private 通灵 result, sent only to the caster when the vote window ends and drawn by the client as a HUD card
 * (owner 2026-11-10: a GUI, never a vanilla title, so it cannot collide with an admin {@code /title}). The server
 * decides everything: {@code outcome} is the tallied answer, and {@code tie} and {@code boundAnswers} are filled only
 * when the round's deaths are over half (they are empty/false otherwise, so the client cannot show what it was never
 * told). {@code boundAnswers} lists the 招魂-bound dead targets who voted, in binding order.
 * 通灵的私密结果，仅在投票窗口结束时发给施放者，客户端以 HUD 卡片绘制（所有者 2026-11-10：用界面而绝不用原版标题，
 * 以免与管理员的 {@code /title} 冲突）。一切由服务端决定：{@code outcome} 是计票后的答案；{@code tie} 与
 * {@code boundAnswers} 只在本局死者过半时填写（否则为 false / 空，客户端无从显示没收到的内容）。
 * {@code boundAnswers} 按绑定先后列出已投票的招魂绑定死者。
 */
public record MediumSeanceResultS2CPacket(String question, int outcome, boolean tie, List<BoundAnswer> boundAnswers)
        implements CustomPayload {
    /** Nobody among the dead answered. / 没有死者作答。 */
    public static final int OUTCOME_NO_RESPONSE = 0;
    public static final int OUTCOME_YES = 1;
    public static final int OUTCOME_NO = 2;
    /** Cap for the bound-answer list on the wire (a round never has this many players). / 线上绑定答案列表的上限。 */
    public static final int MAX_BOUND_ANSWERS = 64;

    public static final Identifier PAYLOAD_ID = SparkWitch.id("medium_seance_result");
    public static final Id<MediumSeanceResultS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, MediumSeanceResultS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.string(MediumRules.QUESTION_MAX_CHARS * 4), MediumSeanceResultS2CPacket::question,
            PacketCodecs.VAR_INT, MediumSeanceResultS2CPacket::outcome,
            PacketCodecs.BOOL, MediumSeanceResultS2CPacket::tie,
            BoundAnswer.CODEC.collect(PacketCodecs.toList(MAX_BOUND_ANSWERS)), MediumSeanceResultS2CPacket::boundAnswers,
            MediumSeanceResultS2CPacket::new
    );

    public MediumSeanceResultS2CPacket {
        question = question == null ? "" : question;
        boundAnswers = boundAnswers == null ? List.of() : List.copyOf(boundAnswers);
    }

    /** One bound dead target's own answer. / 一名被招魂绑定的死者自己的回答。 */
    public record BoundAnswer(String name, boolean yes) {
        public static final PacketCodec<RegistryByteBuf, BoundAnswer> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), BoundAnswer::name,
                PacketCodecs.BOOL, BoundAnswer::yes,
                BoundAnswer::new
        );
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
