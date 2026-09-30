package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Insider instinct-outline rules (D3, D8, C7, C8). Hooks feed plain synced facts in; nothing here touches the
 * client or the world.
 * <ul>
 *     <li>A living Insider holding the instinct key sees the Corrupt Cop in navy and every other target in mint.</li>
 *     <li>A living Corrupt Cop sees the Insider in mint while holding the key or during its NoellesRoles Moment
 *     vision window.</li>
 *     <li>A living killer holding the key sees the Insider in the SparkTraits Impostor blue.</li>
 * </ul>
 * Every answer is always-style ({@code requiresKeybind = false}) and is only given while its own condition holds, so a
 * key-up frame answers nothing and never hides lower-priority always-on outlines (Bomber, Poisoner, tablet marks).
 * 内应本能描边的纯规则（D3、D8、C7、C8）。钩子只传入已同步的事实，这里不接触客户端或世界。
 * <ul>
 *     <li>存活内应按住本能键：黑警显示黑警深蓝，其他目标显示薄荷青。</li>
 *     <li>存活黑警按住本能键或处于 NoellesRoles 黑警时刻的透视窗口：内应显示薄荷青。</li>
 *     <li>存活杀手按住本能键：内应显示 SparkTraits 内鬼蓝。</li>
 * </ul>
 * 所有答复均为常亮样式（{@code requiresKeybind = false}），且只在各自条件成立时给出；松开按键的帧不作答，
 * 因此不会遮住更低优先级的常亮描边（炸弹客、毒师、平板标记等）。
 */
public final class InsiderHighlightRules {
    /**
     * Wathe {@code GetInstinctHighlight} priority: above SparkStrength's Corrupt Cop x-ray (90), SparkWitch's 90-level
     * role outlines and NoellesRoles' Moment outline (0); below the Seeker mark (95), {@code skip()} (100), Black Raven
     * and Final Moment (101) and Grand Witch suppression (102). No listener in the modpack uses 91-94, so it ties with
     * nothing.
     * Wathe {@code GetInstinctHighlight} 优先级：高于 SparkStrength 黑警透视（90）、SparkWitch 的 90 级职业描边与
     * NoellesRoles 黑警时刻描边（0）；低于搜寻者标记（95）、{@code skip()}（100）、黑羽鸦与终局时刻（101）以及
     * 大魔女压制（102）。整合包中没有监听器使用 91-94，因此不会与任何监听器同级。
     */
    public static final int PRIORITY = 93;

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
        /** A living killer (Wathe killer features, SparkTraits Impostors included). / 存活杀手（含 SparkTraits 内鬼）。 */
        KILLER,
        /** Anyone else, including dead or spectating viewers: Insider rules stay silent. / 其他人（含死亡或旁观者）：内应规则不作答。 */
        NONE
    }

    private InsiderHighlightRules() {
    }

    /**
     * Classifies the local viewer. Dead, spectating or creative viewers are {@link Viewer#NONE} so Wathe's spectator
     * view and the other roles' rules apply unchanged.
     * 对本地观察者分类。死亡、旁观或创造模式的观察者为 {@link Viewer#NONE}，旁观视角与其他职业规则保持不变。
     */
    public static Viewer viewer(@Nullable Role viewerRole, boolean playingAndAlive, boolean spectatingOrCreative,
                                boolean killer) {
        if (!playingAndAlive || spectatingOrCreative) {
            return Viewer.NONE;
        }
        if (InsiderParticipation.isInsiderRole(viewerRole)) {
            return Viewer.INSIDER;
        }
        if (InsiderParticipation.isCorruptCopRole(viewerRole)) {
            return Viewer.CORRUPT_COP;
        }
        return killer ? Viewer.KILLER : Viewer.NONE;
    }

    /**
     * Whether a player target may carry an Insider outline at all: another living, non-spectating, visible player.
     * Invisible targets are left to the existing rules, as NoellesRoles and the Murderous Witch do.
     * 玩家目标是否可能带有内应描边：其他存活、非旁观且可见的玩家。隐身目标交给现有规则，与 NoellesRoles 和杀意魔女一致。
     */
    public static boolean isEligibleTarget(boolean samePlayer, boolean targetPlayingAndAlive,
                                           boolean targetSpectatingOrCreative, boolean targetInvisible) {
        return !samePlayer && targetPlayingAndAlive && !targetSpectatingOrCreative && !targetInvisible;
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
     * 内鬼观察者换色（D3）。SparkTraits 的 HEAD 会在任何事件之前为内应给出 {@link #SPARKTRAITS_CIVILIAN_INSTINCT_COLOR}；
     * 只有这一答复被换成内鬼蓝，其他结果原样通过。
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
