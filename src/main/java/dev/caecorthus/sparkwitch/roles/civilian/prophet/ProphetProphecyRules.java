package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Prophecy guess evaluation, shared by the server confirm path and tests. Checks run in a fixed order and the
 * first failure wins; only {@link Verdict#isGuess()} verdicts are charged and start the cooldown.
 * 纯预言判定逻辑，供服务端确认流程与测试共用。检查按固定顺序执行，首个失败即为结果；
 * 只有 {@link Verdict#isGuess()} 的结果才会扣费并进入冷却。
 */
public final class ProphetProphecyRules {
    public static final String MESSAGE_PREFIX = "message.sparkwitch.prophecy.";
    private static final int FALLBACK_NAME_LENGTH = 8;

    private ProphetProphecyRules() {
    }

    public enum Verdict {
        INVALID_TARGET("invalid_target"),
        ALREADY_SOLVED("already_solved"),
        INVALID_CAUSE("invalid_cause"),
        ALREADY_EXCLUDED("already_excluded"),
        NOT_ENOUGH_MONEY("not_enough_money"),
        CORRECT_KILLER("correct_killer"),
        CORRECT_NO_KILLER("correct_no_killer"),
        WRONG("wrong");

        private final String messageKey;

        Verdict(String suffix) {
            this.messageKey = MESSAGE_PREFIX + suffix;
        }

        public String messageKey() {
            return messageKey;
        }

        /** A confirmed guess: charged, recorded and cooled down. / 已确认的猜测：扣费、记录并进入冷却。 */
        public boolean isGuess() {
            return this == CORRECT_KILLER || this == CORRECT_NO_KILLER || this == WRONG;
        }

        public boolean isCorrect() {
            return this == CORRECT_KILLER || this == CORRECT_NO_KILLER;
        }
    }

    /**
     * @param prophet   the guessing Prophet / 进行猜测的先知
     * @param death     the victim's ledger record, or {@code null} when the victim is no longer a dead participant
     *                  / 死者的账本记录；死者不再处于死亡状态时为 {@code null}
     * @param existing  the Prophet's own record for that victim / 先知对该死者已有的预言记录
     * @param guess     the parsed cause group, or {@code null} for an unknown id / 解析出的死因分组，未知 id 为 {@code null}
     * @param balance   the Prophet's current coins / 先知当前金币
     */
    public static Verdict evaluate(
            UUID prophet,
            @Nullable ProphetDeathRecord death,
            @Nullable ProphecyRecord existing,
            @Nullable ProphetDeathCauseGroup guess,
            int balance
    ) {
        if (death == null || death.victim().equals(prophet)) {
            return Verdict.INVALID_TARGET;
        }
        if (existing != null && existing.outcome() != ProphecyRecord.Outcome.PENDING) {
            return Verdict.ALREADY_SOLVED;
        }
        if (guess == null) {
            return Verdict.INVALID_CAUSE;
        }
        if (existing != null && existing.excluded().contains(guess)) {
            return Verdict.ALREADY_EXCLUDED;
        }
        if (balance < ProphetRules.PROPHECY_COIN_COST) {
            return Verdict.NOT_ENOUGH_MONEY;
        }
        if (guess != death.group()) {
            return Verdict.WRONG;
        }
        return death.responsible() == null ? Verdict.CORRECT_NO_KILLER : Verdict.CORRECT_KILLER;
    }

    /**
     * The name revealed by a correct guess: {@code null} means no killer; a known responsible UUID without a captured
     * name falls back to the UUID's first eight characters.
     * 猜中后揭示的名字：{@code null} 表示无人行凶；已知责任人 UUID 但未抓到名字时，回退为 UUID 前八个字符。
     */
    public static @Nullable String revealedKiller(ProphetDeathRecord death) {
        if (death.responsible() == null) {
            return null;
        }
        String name = death.responsibleName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return death.responsible().toString().substring(0, FALLBACK_NAME_LENGTH);
    }
}
