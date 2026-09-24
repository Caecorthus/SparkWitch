package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class JudgeRules {
    public static final Identifier ROLE_ID = SparkWitch.id("judge");
    public static final int ROLE_COLOR = 0xDFA94F;
    public static final int OUTLINE_COLOR = 0xFF7043;
    public static final int JUDGMENT_COST = 200;
    public static final int SENTENCE_TICKS = 60 * 20;
    public static final int SELECTION_TICKS = 30 * 20;
    public static final int MAX_SELECTION_TARGETS = 512;
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;

    private JudgeRules() {
    }

    public static boolean isJudge(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static int requiredKills(int openingParticipants) {
        return Math.max(0, openingParticipants) / 6 + 1;
    }
}
