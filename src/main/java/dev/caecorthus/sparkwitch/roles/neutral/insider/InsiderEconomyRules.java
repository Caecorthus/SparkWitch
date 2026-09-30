package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Insider money decisions (C4): who is paid per task, who starts at {@link InsiderRules#INITIAL_MONEY}, and who
 * sees the coin counter. Inputs in, values out, no world access.
 * 纯内应金钱判定（C4）：谁按任务领钱、谁以 {@link InsiderRules#INITIAL_MONEY} 开局、谁能看到金币。只接收输入、返回结果，不访问世界。
 */
public final class InsiderEconomyRules {
    private InsiderEconomyRules() {
    }

    /**
     * Task pay: an active, living, participating Insider that is not a spectator, creative or Wraith-restricted. Always
     * paid, with no Impostor skip: a neutral cannot roll Impostor, and SparkTraits bonuses stack on top.
     * 任务收入：处于 ACTIVE、存活且参与对局、非旁观/创造、未受灵体限制的内应。始终发放，不跳过内鬼：
     * 中立抽不到内鬼，SparkTraits 的加成叠加在此之上。
     */
    public static boolean earnsTaskMoney(boolean active, boolean playingAndAlive, @Nullable Role role,
                                         boolean spectator, boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && InsiderParticipation.isInsiderRole(role)
                && !spectator && !creative && !wraithRestricted;
    }

    /** Round-start balance for the exact Insider role. / 精确内应职业的开局余额。 */
    public static boolean startsWithInitialMoney(@Nullable Role role) {
        return InsiderParticipation.isInsiderRole(role);
    }

    /**
     * Stable contract: ALLOW for an Insider that holds a round role and is not dead, deliberately without the
     * running-state clause of {@code GameFunctions.isPlayerPlayingAndAlive}. SparkTraits rolls traits while the round
     * is still STARTING and offers Task Master (+25 per task) to a FAKE-mood role only when this event answers ALLOW,
     * so a running-state gate would silently rule the trait out for the Insider.
     * 稳定契约：对拥有局内身份且未死亡的内应返回 ALLOW，刻意不含 {@code GameFunctions.isPlayerPlayingAndAlive}
     * 的运行状态条件。SparkTraits 在对局仍处于 STARTING 时抽取词条，且只有此事件返回 ALLOW 时才会为假理智职业提供
     * 任务大师（每任务 +25），依赖运行状态会让内应永远抽不到该词条。
     */
    public static @Nullable CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean hasAnyRole, boolean dead) {
        return InsiderParticipation.isInsiderRole(role) && hasAnyRole && !dead ? CanSeeMoney.Result.ALLOW : null;
    }
}
