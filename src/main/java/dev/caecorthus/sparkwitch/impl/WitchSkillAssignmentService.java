package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class WitchSkillAssignmentService {
    private WitchSkillAssignmentService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier forcedSkillId = component.getForcedSkillId();
        if (!SparkWitchRoles.isSparkWitchRole(role)) {
            if (forcedSkillId != null) {
                component.clearForcedSkill();
            }
            component.clear();
            return;
        }

        ServerWorld world = player.getServerWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        WitchWorldComponent worldComponent = WitchWorldComponent.KEY.get(world);
        WitchSkillSelectionContext context = new WitchSkillSelectionContext(world, gameComponent, player, role);
        RandomGenerator random = new java.util.Random(world.getRandom().nextLong());
        SkillAssignmentPlan plan = selectSkillForRole(
                role,
                forcedSkillId,
                WitchSkillRegistry.values().stream()
                        .filter(skill -> worldComponent.isSkillEnabled(skill.id()))
                        .toList(),
                context,
                random
        );
        if (plan.consumedForcedSkill()) {
            component.clearForcedSkill();
        }
        Optional<WitchSkillDefinition> selected = plan.selected();
        Identifier previousSkillId = component.getActiveSkillId();
        Identifier selectedSkillId = selected.map(WitchSkillDefinition::id).orElse(null);
        component.setActiveSkill(selectedSkillId);
        if (shouldApplyInitialCooldown(previousSkillId, selectedSkillId)) {
            component.setCooldownTicks(selected.map(WitchSkillDefinition::initialCooldownTicks).orElse(0));
        }
    }

    static boolean shouldApplyInitialCooldown(@Nullable Identifier previousSkillId, @Nullable Identifier selectedSkillId) {
        return previousSkillId == null ? selectedSkillId != null : !previousSkillId.equals(selectedSkillId);
    }

    static SkillAssignmentPlan selectSkillForRole(
            Role role,
            Identifier forcedSkillId,
            Collection<WitchSkillDefinition> randomCandidates,
            WitchSkillSelectionContext context,
            RandomGenerator random
    ) {
        if (!SparkWitchRoles.isSparkWitchRole(role)) {
            return new SkillAssignmentPlan(Optional.empty(), forcedSkillId != null);
        }
        if (forcedSkillId != null) {
            WitchSkillDefinition forcedSkill = WitchSkillRegistry.get(forcedSkillId);
            if (forcedSkill != null && forcedSkill.canSelect(context)) {
                return new SkillAssignmentPlan(Optional.of(forcedSkill), true);
            }
            return new SkillAssignmentPlan(WitchSkillSelector.selectFrom(randomCandidates, context, random), true);
        }
        return new SkillAssignmentPlan(WitchSkillSelector.selectFrom(randomCandidates, context, random), false);
    }

    record SkillAssignmentPlan(Optional<WitchSkillDefinition> selected, boolean consumedForcedSkill) {
    }
}
