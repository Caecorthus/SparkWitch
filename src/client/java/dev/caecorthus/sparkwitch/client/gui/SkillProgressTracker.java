package dev.caecorthus.sparkwitch.client.gui;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * Totals for the owner card's gauge. The client only sees remaining ticks, and countdowns sync every 20 ticks,
 * so a phase starts when the observed value rises (or first appears): total = max(value, smallest known
 * candidate >= value). Opening the inventory mid-phase therefore shows the true fraction, not 0. Pure: callers
 * pass explicit ticks; the client feeds it from {@code OwnerInventoryPresenter.tick} every client tick.
 * 卡片进度条总量：客户端只能看到剩余刻数且倒计时每 20 刻同步一次，因此数值上升（或首次出现）即开始新阶段，
 * 总量取当前值与“不小于当前值的最小已知候选”中的较大者；中途打开背包也显示真实比例。纯逻辑，刻数由调用方传入。
 */
public final class SkillProgressTracker {
    /** A countdown can lead the last sync by at most 19 ticks. 两次同步之间最多插值 19 刻。 */
    static final int MAX_INTERPOLATION_TICKS = 19;

    record Phase(Identifier skill, boolean active, int total, int last, long changedAt) {}

    private @Nullable Identifier skill;
    private @Nullable Phase activePhase;
    private @Nullable Phase cooldownPhase;

    /** Records one observation; a different skill (or none) drops every phase first. 技能变化时清空所有阶段。 */
    public void observe(@Nullable Identifier skill, boolean active, int remaining, IntList candidates, long tick) {
        if (!Objects.equals(this.skill, skill)) {
            reset();
            this.skill = skill;
        }
        if (skill == null) return;
        int value = Math.max(0, remaining);
        Phase phase = active ? activePhase : cooldownPhase;
        Phase next;
        if (phase == null || value > phase.last()) next = new Phase(skill, active, total(value, candidates), value, tick);
        else if (value != phase.last()) next = new Phase(skill, active, phase.total(), value, tick);
        else return;
        if (active) activePhase = next;
        else cooldownPhase = next;
    }

    /**
     * Oriented gauge fill: ACTIVE drains (remaining / total), COOLDOWN fills (1 - remaining / total). While the
     * value still equals the last observation it is interpolated by {@code min(19, ticksSinceChange + delta)}.
     * A value not observed yet (a sync applied in a render frame before the next tick) keeps the phase total only
     * when it did not rise; an unobserved rise is a new phase, so it falls back to total = value (COOLDOWN empty,
     * ACTIVE full) instead of borrowing the previous phase's total (Swift Step: 600 after an initial 1200).
     * 返回已定向的填充比例：生效递减、冷却递增；数值未变化时按距上次变化的刻数插值（最多 19 刻）。
     * 尚未记录的数值（渲染帧先于下一刻应用的同步）仅在未上升时沿用阶段总量；未记录的上升是新阶段，
     * 总量取当前值，不借用上一阶段的总量（如疾步：初始 1200 之后的 600）。
     */
    public float progress(Identifier skill, boolean active, int remaining, long tick, float delta) {
        int value = Math.max(0, remaining);
        Phase phase = Objects.equals(this.skill, skill) ? (active ? activePhase : cooldownPhase) : null;
        float left = value;
        float total = value;
        if (phase != null && value <= phase.last()) {
            total = Math.max(value, phase.total());
            if (value == phase.last()) {
                float elapsed = Math.max(0f, Math.min(MAX_INTERPOLATION_TICKS, (tick - phase.changedAt()) + delta));
                left = Math.max(0f, value - elapsed);
            }
        }
        float fraction = total <= 0f ? 0f : Math.max(0f, Math.min(1f, left / total));
        return active ? fraction : 1f - fraction;
    }

    public void reset() {
        skill = null;
        activePhase = null;
        cooldownPhase = null;
    }

    public static IntList candidates(Identifier skill, boolean active) {
        return candidates(skill, WitchSkillRegistry.get(skill), active);
    }

    /**
     * Known phase totals. Cooldown: the definition's cooldown and initial cooldown, plus the sword's real
     * cooldown (its registry value is 0 because the use handler defers it). Active: the window each panel skill
     * opens (the registry only exposes live windows, not totals).
     * 已知阶段总量：冷却取定义的冷却与初始冷却，另加仪礼剑的真实冷却（注册值为 0，由使用处理延后设置）；
     * 生效取各面板技能开启的窗口长度（注册表只提供实时窗口，不提供总量）。
     */
    static IntList candidates(Identifier skill, @Nullable WitchSkillDefinition definition, boolean active) {
        IntList totals = new IntArrayList(3);
        if (active) {
            add(totals, activeWindowTicks(skill));
            return totals;
        }
        if (definition != null) {
            add(totals, definition.cooldownTicks());
            add(totals, definition.initialCooldownTicks());
        }
        if (GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skill)) {
            add(totals, GrandWitchRules.CEREMONIAL_SWORD_COOLDOWN_TICKS);
        }
        return totals;
    }

    /**
     * Reserve seconds for the pill: ceil(max(all candidates) / 20). This is the card-width stability contract: the
     * pill and card are measured at this value, so the width never follows the countdown.
     * 状态牌预留秒数，取所有候选总量的最大值除以 20 向上取整；这是卡片宽度稳定的约定，宽度从不随倒计时变化。
     */
    public static int widestSeconds(Identifier skill) {
        return widestSeconds(skill, WitchSkillRegistry.get(skill));
    }

    static int widestSeconds(Identifier skill, @Nullable WitchSkillDefinition definition) {
        int widest = 0;
        IntList cooldowns = candidates(skill, definition, false);
        for (int i = 0; i < cooldowns.size(); i++) widest = Math.max(widest, cooldowns.getInt(i));
        IntList windows = candidates(skill, definition, true);
        for (int i = 0; i < windows.size(); i++) widest = Math.max(widest, windows.getInt(i));
        return (int) Math.ceil(widest / 20.0);
    }

    /** The windows opened by the three Witch-skill roles' own skills (the only skills the panel shows). */
    private static int activeWindowTicks(Identifier skill) {
        if (GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skill)) return GrandWitchRules.CEREMONIAL_SWORD_DURATION_TICKS;
        if (MurderousWitchDeathRayRules.isDeathRaySkill(skill)) return MurderousWitchDeathRayRules.WINDOW_TICKS;
        if (MightyForceAbility.ID.equals(skill)) return MightyForceAbility.WINDOW_TICKS;
        if (SwiftStepAbility.ID.equals(skill)) return SwiftStepAbility.DURATION_TICKS;
        if (MurderSenseAbility.ID.equals(skill)) return MurderSenseAbility.DURATION_TICKS;
        if (HealingAbility.ID.equals(skill)) return HealingAbility.DURATION_TICKS;
        // Clairvoyance's effective window counts only the others-glow; self-glow is a penalty. 千里眼只计他人发光。
        if (ClairvoyanceAbility.ID.equals(skill)) return ClairvoyanceAbility.OTHERS_TICKS;
        return 0;
    }

    private static int total(int value, IntList candidates) {
        if (value <= 0) return 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            int candidate = candidates.getInt(i);
            if (candidate >= value && candidate < best) best = candidate;
        }
        return best == Integer.MAX_VALUE ? value : Math.max(value, best);
    }

    private static void add(IntList totals, int ticks) {
        if (ticks > 0 && !totals.contains(ticks)) totals.add(ticks);
    }
}
