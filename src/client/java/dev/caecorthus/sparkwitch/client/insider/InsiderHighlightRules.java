package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Pure Insider instinct-outline rules (D3, D8, C7, C8). Hooks feed plain synced facts in; nothing here touches the
 * client or the world.
 * <ul>
 *     <li>A living Insider holding the instinct key sees the Corrupt Cop in navy and every other visible target in
 *     mint, at {@link #INSIDER_VIEW_PRIORITY}.</li>
 *     <li>A living Corrupt Cop sees the visible Insider in mint while holding the key or during its NoellesRoles
 *     Moment vision window, at {@link #PRIORITY}.</li>
 *     <li>A killer-instinct viewer holding the key sees the Insider in the SparkTraits Impostor blue at
 *     {@link #PRIORITY}, invisible or not, exactly as SparkTraits paints an invisible real Impostor.</li>
 * </ul>
 * Every answer is always-style ({@code requiresKeybind = false}) and is only given while its own condition holds, so a
 * key-up frame answers nothing and never hides lower-priority always-on outlines. While the Insider holds the key its
 * own view stays below SparkStrength's tablet marks (70/80), so those keep their colors, except on the Corrupt Cop,
 * which always stays navy.
 * 内应本能描边的纯规则（D3、D8、C7、C8）。钩子只传入已同步的事实，这里不接触客户端或世界。
 * <ul>
 *     <li>存活内应按住本能键：黑警显示黑警深蓝，其他可见目标显示薄荷青，优先级 {@link #INSIDER_VIEW_PRIORITY}。</li>
 *     <li>存活黑警按住本能键或处于 NoellesRoles 黑警时刻的透视窗口：可见的内应显示薄荷青，优先级 {@link #PRIORITY}。</li>
 *     <li>杀手本能观察者按住本能键：内应显示 SparkTraits 内鬼蓝，优先级 {@link #PRIORITY}；
 *     无论是否隐身，都与 SparkTraits 描绘隐身的真实内鬼一致。</li>
 * </ul>
 * 所有答复均为常亮样式（{@code requiresKeybind = false}），且只在各自条件成立时给出；松开按键的帧不作答，
 * 因此不会遮住更低优先级的常亮描边。内应按住按键时，其自身视角低于 SparkStrength 平板标记（70/80），平板标记保持原色；
 * 但黑警始终显示深蓝。
 */
public final class InsiderHighlightRules {
    /**
     * Wathe {@code GetInstinctHighlight} priority of the Corrupt Cop and killer views: above SparkStrength's Corrupt
     * Cop x-ray (90), SparkWitch's 90-level role outlines and NoellesRoles' Moment outline (0); below the Seeker mark
     * (95), {@code skip()} (100), Black Raven and Final Moment (101) and Grand Witch suppression (102). No listener in
     * the modpack uses 91-94, so it ties with nothing.
     * 黑警视角与杀手视角的 Wathe {@code GetInstinctHighlight} 优先级：高于 SparkStrength 黑警透视（90）、SparkWitch
     * 的 90 级职业描边与 NoellesRoles 黑警时刻描边（0）；低于搜寻者标记（95）、{@code skip()}（100）、黑羽鸦与终局时刻
     * （101）以及大魔女压制（102）。整合包中没有监听器使用 91-94，因此不会与任何监听器同级。
     */
    public static final int PRIORITY = 93;

    /**
     * Priority of the Insider's own view: below SparkStrength's tablet outlines (member 70, suspect 80), so a tablet
     * the Insider bought keeps its marks while the key is held, and above NoellesRoles' role-scoped outlines (0). Apart
     * from those tablet marks, every answer between 65 and 93 is scoped to other viewer roles (Veteran 85, Corrupt Cop
     * x-ray 90, witch, Murderous Witch and Pig God 90) or to corpses (Prophet 90), so nothing else repaints or hides a
     * player for the Insider.
     * 内应自身视角的优先级：低于 SparkStrength 平板描边（成员 70、嫌疑人 80），因此内应购买的平板在按住按键时仍保留
     * 标记；高于 NoellesRoles 按职业限定的描边（0）。除平板标记外，65 与 93 之间的答复都只作用于其他观察者职业
     * （老兵 85、黑警透视 90、魔女、杀意魔女与猪神 90）或尸体（先知 90），不会为内应重绘或隐藏其他玩家。
     */
    public static final int INSIDER_VIEW_PRIORITY = 65;

    /**
     * SparkTraits' {@code EffectiveTraitService.CIVILIAN_INSTINCT_COLOR}: the green its HEAD answers when an Impostor
     * viewer looks at a non-killer. Mirrored because SparkWitch may not read SparkTraits internals; also Wathe's own
     * default civilian green.
     * SparkTraits 的 {@code EffectiveTraitService.CIVILIAN_INSTINCT_COLOR}：内鬼观察者看非杀手时其 HEAD 给出的绿色。
     * SparkWitch 不能读取 SparkTraits 内部代码，所以在此镜像；它也是 Wathe 默认的平民绿色。
     */
    public static final int SPARKTRAITS_CIVILIAN_INSTINCT_COLOR = 0x4EDD35;

    /** Who is looking, as far as the Insider rules care. / 就内应规则而言的观察者类别。 */
    public enum Viewer {
        /** A living Insider. / 存活的内应。 */
        INSIDER,
        /** A living NoellesRoles Corrupt Cop. / 存活的 NoellesRoles 黑警。 */
        CORRUPT_COP,
        /**
         * A viewer for whom Wathe's default would paint the Insider as a killer target (see
         * {@link #isKillerInstinctViewer}); SparkTraits Impostors and promoted Saboteur Wraiths included.
         * Wathe 默认逻辑会把内应当作杀手本能目标描绘的观察者（见 {@link #isKillerInstinctViewer}），含 SparkTraits 内鬼与
         * 晋升的破坏者冤魂。
         */
        KILLER,
        /** Anyone else, including dead or spectating viewers: Insider rules stay silent. / 其他人（含死亡或旁观者）：内应规则不作答。 */
        NONE
    }

    private InsiderHighlightRules() {
    }

    /**
     * Cheap pre-filter before any viewer classification: only the Insider's own view answers for a non-Insider target,
     * so the Corrupt Cop and killer checks run only when the target is an Insider.
     * 在观察者分类之前的廉价预筛：只有内应自身视角会对非内应目标作答，因此黑警与杀手判断只在目标是内应时执行。
     */
    public static boolean mayAnswer(@Nullable Role viewerRole, @Nullable Role targetRole) {
        return InsiderParticipation.isInsiderRole(viewerRole) || InsiderParticipation.isInsiderRole(targetRole);
    }

    /**
     * Classifies the local viewer. The Insider and Corrupt Cop views need a living, non-spectating viewer and never
     * consult {@code killerInstinctViewer}; every other role is {@link Viewer#KILLER} exactly when
     * {@code killerInstinctViewer} (normally {@link #isKillerInstinctViewer}) holds, so the costly Wathe and SparkTraits
     * killer queries run only after the role check.
     * 对本地观察者分类。内应与黑警视角要求存活且非旁观的观察者，且从不查询 {@code killerInstinctViewer}；其他职业仅在
     * {@code killerInstinctViewer}（通常为 {@link #isKillerInstinctViewer}）成立时为 {@link Viewer#KILLER}，因此开销较大的
     * Wathe 与 SparkTraits 杀手查询只在职业判断之后执行。
     */
    public static Viewer viewer(@Nullable Role viewerRole, boolean playingAndAlive, boolean spectatingOrCreative,
                                BooleanSupplier killerInstinctViewer) {
        if (InsiderParticipation.isInsiderRole(viewerRole)) {
            return playingAndAlive && !spectatingOrCreative ? Viewer.INSIDER : Viewer.NONE;
        }
        if (InsiderParticipation.isCorruptCopRole(viewerRole)) {
            return playingAndAlive && !spectatingOrCreative ? Viewer.CORRUPT_COP : Viewer.NONE;
        }
        return killerInstinctViewer.getAsBoolean() ? Viewer.KILLER : Viewer.NONE;
    }

    /**
     * Exactly when Wathe's default {@code getInstinctHighlight} would paint a non-spectating player target with the
     * killer palette (red or green): {@code isInstinctEnabledAndIsKiller()}, not {@code canSeeSpectatorInformation()},
     * then {@code isKiller()}. The first term already carries the instinct key and every SparkWitch gate or extension
     * on it (fear, Curser, Black Raven perception, Control Expert, the promoted Saboteur Wraith); the last one is
     * SparkTraits' Impostor {@code isKiller} extension. Such a viewer sees a real Impostor blue, so it must see the
     * Insider blue too.
     * 恰好对应 Wathe 默认 {@code getInstinctHighlight} 用杀手配色（红或绿）描绘非旁观玩家目标的条件：
     * {@code isInstinctEnabledAndIsKiller()}、非 {@code canSeeSpectatorInformation()}，再加 {@code isKiller()}。
     * 第一项已包含本能键以及 SparkWitch 对它的全部门槛或扩展（恐惧、诅咒师、黑羽鸦感知、控场专家、晋升的破坏者冤魂）；
     * 最后一项包含 SparkTraits 内鬼的 {@code isKiller} 扩展。此类观察者看真实内鬼为蓝色，因此看内应也必须为蓝色。
     */
    public static boolean isKillerInstinctViewer(boolean instinctEnabledAndIsKiller, boolean canSeeSpectatorInformation,
                                                 boolean killer) {
        return instinctEnabledAndIsKiller && !canSeeSpectatorInformation && killer;
    }

    /**
     * Whether an Insider view needs the target to be visible. The Insider and Corrupt Cop views skip invisible targets,
     * like NoellesRoles' Moment outline and SparkStrength's Corrupt Cop x-ray; the killer view does not, because
     * SparkTraits paints an invisible real Impostor blue for killers (its invisibility skip only covers Conscience
     * targets), and the disguise must match.
     * 内应相关视角是否要求目标可见。内应与黑警视角跳过隐身目标，与 NoellesRoles 黑警时刻描边及 SparkStrength 黑警透视一致；
     * 杀手视角不跳过，因为 SparkTraits 会为杀手把隐身的真实内鬼描成蓝色（其隐身跳过只针对善良目标），伪装必须与之一致。
     */
    public static boolean requiresVisibleTarget(Viewer viewer) {
        return viewer == Viewer.INSIDER || viewer == Viewer.CORRUPT_COP;
    }

    /**
     * Whether a player target may carry an Insider outline for this viewer: another living, non-spectating player,
     * and visible when {@link #requiresVisibleTarget} says so. {@link Viewer#NONE} never has an eligible target.
     * 对该观察者而言玩家目标是否可能带有内应描边：其他存活、非旁观的玩家；{@link #requiresVisibleTarget} 要求时还须可见。
     * {@link Viewer#NONE} 没有合格目标。
     */
    public static boolean isEligibleTarget(Viewer viewer, boolean samePlayer, boolean targetPlayingAndAlive,
                                           boolean targetSpectatingOrCreative, boolean targetInvisible) {
        return viewer != Viewer.NONE
                && !samePlayer
                && targetPlayingAndAlive
                && !targetSpectatingOrCreative
                && (!targetInvisible || !requiresVisibleTarget(viewer));
    }

    /**
     * Listener priority for a viewer's answer: {@link #PRIORITY} for the Corrupt Cop and killer views, and for the
     * Corrupt Cop as seen by the Insider (the owner's "except the Corrupt Cop": its partner stays navy even when it
     * carries a tablet mark); {@link #INSIDER_VIEW_PRIORITY} for every other target of the Insider's own view.
     * 各视角答复的监听器优先级：黑警视角、杀手视角，以及内应看黑警时为 {@link #PRIORITY}（所有者要求的「黑警除外」：
     * 即使黑警带有平板标记，搭档仍显示深蓝）；内应自身视角的其他目标为 {@link #INSIDER_VIEW_PRIORITY}。
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
            case KILLER -> instinctKey && InsiderParticipation.isInsiderRole(targetRole)
                    ? InsiderRules.KILLER_VIEW_COLOR : null;
            case NONE -> null;
        };
    }

    /**
     * Impostor-viewer recolor (D3). SparkTraits' HEAD answers {@link #SPARKTRAITS_CIVILIAN_INSTINCT_COLOR} for an
     * Insider before any event runs; exactly that answer becomes the Impostor blue, everything else passes through.
     * The target check is the killer view's ({@link Viewer#KILLER} in {@link #isEligibleTarget}), so an invisible
     * Insider is recolored too.
     * 内鬼观察者换色（D3）。SparkTraits 的 HEAD 会在任何事件之前为内应给出 {@link #SPARKTRAITS_CIVILIAN_INSTINCT_COLOR}；
     * 只有这一答复被换成内鬼蓝，其他结果原样通过。目标判断采用杀手视角（{@link #isEligibleTarget} 中的
     * {@link Viewer#KILLER}），因此隐身的内应同样换色。
     */
    public static int recolorImpostorView(int highlight, boolean viewerLivingImpostor, boolean eligibleInsiderTarget) {
        return highlight == SPARKTRAITS_CIVILIAN_INSTINCT_COLOR && viewerLivingImpostor && eligibleInsiderTarget
                ? InsiderRules.KILLER_VIEW_COLOR
                : highlight;
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
