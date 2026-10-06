package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Insider instinct-outline rules (D8, C7, C8). Hooks feed plain synced facts in; nothing here touches the
 * client or the world.
 * <ul>
 *     <li>A living Insider holding the instinct key sees the Corrupt Cop in navy and every other visible target in
 *     mint, at {@link #INSIDER_VIEW_PRIORITY}.</li>
 *     <li>A living Corrupt Cop sees the visible Insider in mint while holding the key or during its NoellesRoles
 *     Moment vision window, at {@link #PRIORITY}.</li>
 * </ul>
 * Killers get no Insider answer (owner 2026-10-05, replacing D3's Impostor disguise): Wathe's default paints the
 * Insider green like any other passenger.
 * Every answer is always-style ({@code requiresKeybind = false}) and is only given while its own condition holds, so a
 * key-up frame answers nothing and never hides lower-priority always-on outlines. While the Insider holds the key its
 * own view stays below SparkStrength's tablet suspect mark (80), so that mark keeps its color, except on the Corrupt
 * Cop, which always stays navy.
 * 内应本能描边的纯规则（D8、C7、C8）。钩子只传入已同步的事实，这里不接触客户端或世界。
 * <ul>
 *     <li>存活内应按住本能键：黑警显示黑警深蓝，其他可见目标显示薄荷青，优先级 {@link #INSIDER_VIEW_PRIORITY}。</li>
 *     <li>存活黑警按住本能键或处于 NoellesRoles 黑警时刻的透视窗口：可见的内应显示薄荷青，优先级 {@link #PRIORITY}。</li>
 * </ul>
 * 杀手不会得到内应答复（所有者 2026-10-05 决定，取代 D3 的内鬼伪装）：Wathe 默认逻辑把内应与其他乘客一样描成绿色。
 * 所有答复均为常亮样式（{@code requiresKeybind = false}），且只在各自条件成立时给出；松开按键的帧不作答，
 * 因此不会遮住更低优先级的常亮描边。内应按住按键时，其自身视角低于 SparkStrength 平板嫌疑人标记（80），该标记保持原色；
 * 但黑警始终显示深蓝。
 */
public final class InsiderHighlightRules {
    /**
     * Wathe {@code GetInstinctHighlight} priority of the Corrupt Cop view: above SparkStrength's Corrupt Cop x-ray
     * (90), SparkWitch's 90-level role outlines and NoellesRoles' Moment outline (0); below the Seeker mark (95),
     * {@code skip()} (100), Black Raven and Final Moment (101) and Grand Witch suppression (102). No listener in the
     * modpack uses 91-94, so it ties with nothing.
     * 黑警视角的 Wathe {@code GetInstinctHighlight} 优先级：高于 SparkStrength 黑警透视（90）、SparkWitch 的 90 级职业
     * 描边与 NoellesRoles 黑警时刻描边（0）；低于搜寻者标记（95）、{@code skip()}（100）、黑羽鸦与终局时刻（101）以及
     * 大魔女压制（102）。整合包中没有监听器使用 91-94，因此不会与任何监听器同级。
     */
    public static final int PRIORITY = 93;

    /**
     * Priority of the Insider's own view: below SparkStrength's tablet suspect outline (80), so the Insider's issued
     * tablet keeps that mark while the key is held, and above NoellesRoles' role-scoped outlines (0). SparkStrength no
     * longer outlines police-network members (its former 70 mark is gone), so 65 now sits below the suspect mark alone.
     * Apart from that mark, every answer between 65 and 93 is scoped to other viewer roles (Veteran 85, Corrupt Cop
     * x-ray 90, witch, Murderous Witch and Pig God 90) or to corpses (Prophet 90), so nothing else repaints or hides a
     * player for the Insider.
     * 内应自身视角的优先级：低于 SparkStrength 平板嫌疑人描边（80），因此内应开局发放的平板在按住按键时仍保留该标记；
     * 高于 NoellesRoles 按职业限定的描边（0）。SparkStrength 已不再描边警察网络成员（原 70 标记已移除），因此 65 现在只需
     * 低于嫌疑人标记。除该标记外，65 与 93 之间的答复都只作用于其他观察者职业（老兵 85、黑警透视 90、魔女、杀意魔女与
     * 猪神 90）或尸体（先知 90），不会为内应重绘或隐藏其他玩家。
     */
    public static final int INSIDER_VIEW_PRIORITY = 65;

    /** Who is looking, as far as the Insider rules care. / 就内应规则而言的观察者类别。 */
    public enum Viewer {
        /** A living Insider. / 存活的内应。 */
        INSIDER,
        /** A living NoellesRoles Corrupt Cop. / 存活的 NoellesRoles 黑警。 */
        CORRUPT_COP,
        /** Anyone else, killers and dead or spectating viewers included: Insider rules stay silent. / 其他人（含杀手、死亡或旁观者）：内应规则不作答。 */
        NONE
    }

    private InsiderHighlightRules() {
    }

    /**
     * Cheap pre-filter before any viewer classification: only the Insider's own view answers for a non-Insider target,
     * so the Corrupt Cop check runs only when the target is an Insider.
     * 在观察者分类之前的廉价预筛：只有内应自身视角会对非内应目标作答，因此黑警判断只在目标是内应时执行。
     */
    public static boolean mayAnswer(@Nullable Role viewerRole, @Nullable Role targetRole) {
        return InsiderParticipation.isInsiderRole(viewerRole) || InsiderParticipation.isInsiderRole(targetRole);
    }

    /**
     * Classifies the local viewer. The Insider and Corrupt Cop views need a living, non-spectating viewer; every other
     * role is {@link Viewer#NONE}.
     * 对本地观察者分类。内应与黑警视角要求存活且非旁观的观察者；其他职业均为 {@link Viewer#NONE}。
     */
    public static Viewer viewer(@Nullable Role viewerRole, boolean playingAndAlive, boolean spectatingOrCreative) {
        if (!playingAndAlive || spectatingOrCreative) {
            return Viewer.NONE;
        }
        if (InsiderParticipation.isInsiderRole(viewerRole)) {
            return Viewer.INSIDER;
        }
        return InsiderParticipation.isCorruptCopRole(viewerRole) ? Viewer.CORRUPT_COP : Viewer.NONE;
    }

    /**
     * Whether a player target may carry an Insider outline for this viewer: another living, non-spectating, visible
     * player, like NoellesRoles' Moment outline and SparkStrength's Corrupt Cop x-ray. {@link Viewer#NONE} never has
     * an eligible target.
     * 对该观察者而言玩家目标是否可能带有内应描边：其他存活、非旁观且可见的玩家，与 NoellesRoles 黑警时刻描边及
     * SparkStrength 黑警透视一致。{@link Viewer#NONE} 没有合格目标。
     */
    public static boolean isEligibleTarget(Viewer viewer, boolean samePlayer, boolean targetPlayingAndAlive,
                                           boolean targetSpectatingOrCreative, boolean targetInvisible) {
        return viewer != Viewer.NONE
                && !samePlayer
                && targetPlayingAndAlive
                && !targetSpectatingOrCreative
                && !targetInvisible;
    }

    /**
     * Listener priority for a viewer's answer: {@link #PRIORITY} for the Corrupt Cop view, and for the Corrupt Cop as
     * seen by the Insider (the owner's "except the Corrupt Cop": its partner stays navy even when it carries a tablet
     * suspect mark); {@link #INSIDER_VIEW_PRIORITY} for every other target of the Insider's own view.
     * 各视角答复的监听器优先级：黑警视角，以及内应看黑警时为 {@link #PRIORITY}（所有者要求的「黑警除外」：
     * 即使黑警带有平板嫌疑人标记，搭档仍显示深蓝）；内应自身视角的其他目标为 {@link #INSIDER_VIEW_PRIORITY}。
     */
    public static int priority(Viewer viewer, @Nullable Role targetRole) {
        return viewer == Viewer.INSIDER && !InsiderParticipation.isCorruptCopRole(targetRole)
                ? INSIDER_VIEW_PRIORITY
                : PRIORITY;
    }

    /**
     * RGB outline for an eligible target, or {@code null} when the Insider rules do not answer.
     * 合格目标的描边 RGB；内应规则不作答时返回 {@code null}。
     *
     * @param instinctKey            Wathe's keyed instinct gate ({@code WatheClient.isInstinctEnabled()}) / Wathe 按键本能门槛
     * @param corruptCopVisionWindow the viewer's own NoellesRoles Moment always-on vision phase / 观察者自身的黑警时刻常亮透视阶段
     */
    public static @Nullable Integer color(Viewer viewer, boolean instinctKey, boolean corruptCopVisionWindow,
                                          @Nullable Role targetRole) {
        return switch (viewer) {
            case INSIDER -> !instinctKey ? null
                    : InsiderParticipation.isCorruptCopRole(targetRole) ? InsiderRules.CORRUPT_COP_COLOR : InsiderRules.COLOR;
            case CORRUPT_COP -> (instinctKey || corruptCopVisionWindow) && InsiderParticipation.isInsiderRole(targetRole)
                    ? InsiderRules.COLOR : null;
            case NONE -> null;
        };
    }

    /**
     * The Insider shares killer-style instinct night vision (owner-approved A3). The Corrupt Cop is not added here:
     * SparkStrength already grants it.
     * 内应使用杀手式本能夜视（所有者批准的 A3）。黑警不在此添加：SparkStrength 已经提供。
     */
    public static boolean usesKillerStyleInstinctLight(@Nullable Role role) {
        return InsiderParticipation.isInsiderRole(role);
    }
}
