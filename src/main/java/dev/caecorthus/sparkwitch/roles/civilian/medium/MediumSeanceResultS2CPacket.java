package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The private result of a vote window, sent only to the caster when the window ends and drawn by the client as a HUD
 * card (owner 2026-11-10: a GUI, never a vanilla title, so it cannot collide with an admin {@code /title}). The server
 * decides everything; {@code tie} and {@code boundAnswers} are filled only when the round's deaths are over half (they
 * are empty/false otherwise, so the client cannot show what it was never told). {@code boundAnswers} lists the
 * 招魂-bound dead targets who voted, in binding order.
 * <ul>
 *   <li>{@link MediumWindowKind#QUESTION} (通灵): {@code question} is the caster's own text and {@code outcome} one of
 *   {@link #OUTCOME_NO_RESPONSE}, {@link #OUTCOME_YES}, {@link #OUTCOME_NO}; {@code random} is false.</li>
 *   <li>{@link MediumWindowKind#BLESSING} (幽灵的加护): {@code question} is empty and {@code outcome} is the granted
 *   {@link MediumBlessing} index ({@link #blessing()}), or {@link #OUTCOME_BLESSING_NONE} when nothing could be
 *   granted; {@code random} says nobody voted and the server drew the blessing itself.</li>
 * </ul>
 * 投票窗口的私密结果，仅在窗口结束时发给施放者，客户端以 HUD 卡片绘制（所有者 2026-11-10：用界面而绝不用原版标题，
 * 以免与管理员的 {@code /title} 冲突）。一切由服务端决定；{@code tie} 与 {@code boundAnswers} 只在本局死者过半时填写
 * （否则为 false / 空，客户端无从显示没收到的内容）。{@code boundAnswers} 按绑定先后列出已投票的招魂绑定死者。
 * 通灵（QUESTION）：{@code question} 为施放者自己的提问，{@code outcome} 为无人回应 / 是 / 否，{@code random} 为 false。
 * 幽灵的加护（BLESSING）：{@code question} 为空，{@code outcome} 为获得的 {@link MediumBlessing} 序号
 * （{@link #blessing()}），无法给予任何加护时为 {@link #OUTCOME_BLESSING_NONE}；{@code random} 表示无人投票、由服务端
 * 随机抽取。
 */
public record MediumSeanceResultS2CPacket(MediumWindowKind kind, String question, int outcome, boolean random,
                                          boolean tie, List<BoundAnswer> boundAnswers)
        implements CustomPayload {
    /** QUESTION: nobody among the dead answered. / 提问：没有死者作答。 */
    public static final int OUTCOME_NO_RESPONSE = 0;
    public static final int OUTCOME_YES = 1;
    public static final int OUTCOME_NO = 2;
    /** BLESSING: no blessing was granted (nobody voted and nothing was available). / 加护：未给予任何加护（无人投票且无可用加护）。 */
    public static final int OUTCOME_BLESSING_NONE = -1;
    /** Cap for the bound-answer list on the wire (a round never has this many players). / 线上绑定答案列表的上限。 */
    public static final int MAX_BOUND_ANSWERS = 64;

    public static final Identifier PAYLOAD_ID = SparkWitch.id("medium_seance_result");
    public static final Id<MediumSeanceResultS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, MediumSeanceResultS2CPacket> CODEC = PacketCodec.tuple(
            MediumWindowKind.PACKET_CODEC, MediumSeanceResultS2CPacket::kind,
            PacketCodecs.string(MediumRules.QUESTION_MAX_CHARS * 4), MediumSeanceResultS2CPacket::question,
            PacketCodecs.VAR_INT, MediumSeanceResultS2CPacket::outcome,
            PacketCodecs.BOOL, MediumSeanceResultS2CPacket::random,
            PacketCodecs.BOOL, MediumSeanceResultS2CPacket::tie,
            BoundAnswer.CODEC.collect(PacketCodecs.toList(MAX_BOUND_ANSWERS)), MediumSeanceResultS2CPacket::boundAnswers,
            MediumSeanceResultS2CPacket::new
    );

    public MediumSeanceResultS2CPacket {
        kind = kind == null ? MediumWindowKind.QUESTION : kind;
        question = question == null ? "" : question;
        boundAnswers = boundAnswers == null ? List.of() : List.copyOf(boundAnswers);
    }

    /** A 通灵 result. / 通灵结果。 */
    public static MediumSeanceResultS2CPacket ofQuestion(String question, int outcome, boolean tie,
                                                         List<BoundAnswer> boundAnswers) {
        return new MediumSeanceResultS2CPacket(MediumWindowKind.QUESTION, question, outcome, false, tie, boundAnswers);
    }

    /** A 幽灵的加护 result; a null blessing is {@link #OUTCOME_BLESSING_NONE}. / 幽灵的加护结果；blessing 为 null 即未给予。 */
    public static MediumSeanceResultS2CPacket ofBlessing(@Nullable MediumBlessing blessing, boolean random,
                                                         boolean tie, List<BoundAnswer> boundAnswers) {
        return new MediumSeanceResultS2CPacket(MediumWindowKind.BLESSING, "",
                blessing == null ? OUTCOME_BLESSING_NONE : blessing.index(), random, tie, boundAnswers);
    }

    /** The granted blessing of a BLESSING result; null for a question or when nothing was granted.
     * 加护结果中获得的加护；提问结果或未给予时为 null。 */
    public @Nullable MediumBlessing blessing() {
        return kind == MediumWindowKind.BLESSING ? MediumBlessing.byIndex(outcome) : null;
    }

    /**
     * One bound dead target's own vote, as the choice index of the window's kind: {@link MediumRules#VOTE_YES} /
     * {@link MediumRules#VOTE_NO} for a question (NOT the {@code OUTCOME_*} values), a {@link MediumBlessing} index
     * for a blessing.
     * 一名被招魂绑定的死者自己的投票，以窗口种类下的选项序号表示：提问为 {@link MediumRules#VOTE_YES} /
     * {@link MediumRules#VOTE_NO}（不是 {@code OUTCOME_*} 值），加护为 {@link MediumBlessing} 的序号。
     */
    public record BoundAnswer(String name, int choice) {
        public static final PacketCodec<RegistryByteBuf, BoundAnswer> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), BoundAnswer::name,
                PacketCodecs.VAR_INT, BoundAnswer::choice,
                BoundAnswer::new
        );
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
