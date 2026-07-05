package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class WitchSkillUseReadiness {
    private WitchSkillUseReadiness() {
    }

    static Result check(
            Role role,
            WitchPlayerComponent component,
            @Nullable Identifier skillId,
            @Nullable WitchSkillDefinition skill
    ) {
        return check(role, State.from(component), skillId, skill);
    }

    static Result check(
            Role role,
            State state,
            @Nullable Identifier skillId,
            @Nullable WitchSkillDefinition skill
    ) {
        if (skillId == null) {
            return Result.reject("message.sparkwitch.skill.no_skill");
        }
        if (GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID.equals(skillId)
                && WitchFactionRules.isGrandWitch(role)
                && !state.hasUnlockedGrandWitchCeremonialSword()) {
            return Result.reject(
                    "message.sparkwitch.skill.ceremonial_sword.locked",
                    state.grandWitchCeremonialSwordTasks(),
                    WitchFactionRules.CEREMONIAL_SWORD_UNLOCK_TASKS
            );
        }
        if (state.cooldownTicks() > 0) {
            return Result.reject("message.sparkwitch.skill.cooldown", cooldownSeconds(state.cooldownTicks()));
        }
        if (state.hasDeferredCooldown()) {
            return Result.reject(
                    "message.sparkwitch.skill.cooldown",
                    cooldownSeconds(state.activeSkillWindowTicks())
            );
        }
        if (skill == null) {
            return Result.rejectAndClear("message.sparkwitch.skill.unknown");
        }
        return Result.accept();
    }

    static double cooldownSeconds(int ticks) {
        return Math.ceil(Math.max(0, ticks) / 20.0);
    }

    record State(
            int cooldownTicks,
            int activeSkillWindowTicks,
            boolean hasDeferredCooldown,
            boolean hasUnlockedGrandWitchCeremonialSword,
            int grandWitchCeremonialSwordTasks
    ) {
        State {
            cooldownTicks = Math.max(0, cooldownTicks);
            activeSkillWindowTicks = Math.max(0, activeSkillWindowTicks);
            grandWitchCeremonialSwordTasks = Math.max(0, grandWitchCeremonialSwordTasks);
        }

        static State from(WitchPlayerComponent component) {
            return new State(
                    component.getCooldownTicks(),
                    component.getActiveSkillWindowTicks(),
                    component.hasDeferredCooldown(),
                    component.hasUnlockedGrandWitchCeremonialSword(),
                    component.getGrandWitchCeremonialSwordTasks()
            );
        }
    }

    record Result(boolean accepted, String messageKey, Object[] messageArgs, boolean clearComponent) {
        static Result accept() {
            return new Result(true, null, new Object[0], false);
        }

        static Result reject(String messageKey, Object... messageArgs) {
            return new Result(false, messageKey, messageArgs, false);
        }

        static Result rejectAndClear(String messageKey, Object... messageArgs) {
            return new Result(false, messageKey, messageArgs, true);
        }
    }
}
