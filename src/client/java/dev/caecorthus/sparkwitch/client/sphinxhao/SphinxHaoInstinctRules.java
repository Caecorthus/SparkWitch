package dev.caecorthus.sparkwitch.client.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;

/**
 * Pure rules of the Sphinx Hao's own instinct outline: while a living Sphinx Hao holds the instinct key, every player
 * it knows to be infected (the synced {@code infectedIds()}, which reaches only a Sphinx Hao, a spectator or a
 * creative player) is outlined in Team Jiahao gold. A keyed answer at {@link #OUTLINE_PRIORITY}, the Koko level: below
 * Wathe's {@code skip()} (100), the Seeker mark (95) and the Corrupt Cop view (93), above the Insider's own view (65)
 * and SparkStrength's tablet suspect mark (80). It answers only while the key gate reads true, so a key-up frame never
 * masks a lower always-on outline. Everyone else keeps the Team Jiahao (Insider-style) instinct behaviour.
 * 狮身豪自身本能描边的纯规则：存活的狮身豪按住本能键时，它所知的每名被感染者（同步的 {@code infectedIds()}，只发给狮身豪、
 * 旁观者与创造模式玩家）以嘉豪阵营金色描边。按键型答复，优先级 {@link #OUTLINE_PRIORITY}（与可可相同）：低于 Wathe 的
 * {@code skip()}（100）、搜寻者标记（95）与黑警视角（93），高于内应自身视角（65）与 SparkStrength 平板嫌疑人标记（80）。只在按键
 * 门槛为真时作答，因此松开按键的帧不会遮住更低优先级的常亮描边。其他目标保持嘉豪阵营（内应式）本能行为。
 */
public final class SphinxHaoInstinctRules {
    /** Team Jiahao gold. / 嘉豪阵营金色。 */
    public static final int OUTLINE_COLOR = InsiderRules.TEAM_JIAHAO_COLOR;
    public static final int OUTLINE_PRIORITY = 90;

    private SphinxHaoInstinctRules() {
    }

    /**
     * Whether the viewer may answer at all: a confirmed server, the instinct key held, and a living (playing, not
     * spectating, not swallowed) Sphinx Hao by its real role.
     * 观察者是否可以作答：已确认的服务器、按住本能键，且按真实职业为存活（参与、非旁观、未被吞下）的狮身豪。
     */
    public static boolean viewerAnswers(boolean confirmedServer, boolean instinctKey, boolean sphinxHao,
                                        boolean playingAndAlive, boolean spectatingOrCreative, boolean swallowed) {
        return confirmedServer && instinctKey && sphinxHao && playingAndAlive && !spectatingOrCreative && !swallowed;
    }

    /**
     * Whether an infected target is outlined: another living participant, not spectating or creative, not invisible
     * (to the viewer), not swallowed and not hidden from the viewer's instinct by SparkTraits.
     * 被感染目标是否描边：其他存活参与者，非旁观或创造模式，未隐身（且对观察者不隐身），未被吞下，未被 SparkTraits 从观察者
     * 本能中隐藏。
     */
    public static boolean outlinesTarget(boolean samePlayer, boolean infected, boolean playingAndAlive,
                                         boolean spectatingOrCreative, boolean invisibleToViewer, boolean swallowed,
                                         boolean sparkTraitsHidden) {
        return !samePlayer && infected && playingAndAlive && !spectatingOrCreative && !invisibleToViewer && !swallowed
                && !sparkTraitsHidden;
    }
}
