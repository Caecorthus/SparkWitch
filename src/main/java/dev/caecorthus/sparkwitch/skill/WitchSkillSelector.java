package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class WitchSkillSelector {
    private WitchSkillSelector() {
    }

    public static Optional<WitchSkillDefinition> select(WitchSkillSelectionContext context, RandomGenerator random) {
        return selectFrom(WitchSkillRegistry.values(), context, random);
    }

    public static Optional<WitchSkillDefinition> selectFrom(
            Collection<WitchSkillDefinition> definitions,
            WitchSkillSelectionContext context,
            RandomGenerator random
    ) {
        List<WitchSkillDefinition> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (WitchSkillDefinition definition : definitions) {
            if (definition.weight() <= 0 || !definition.canSelect(context)) {
                continue;
            }
            candidates.add(definition);
            totalWeight += definition.weight();
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return Optional.empty();
        }

        int pick = random.nextInt(totalWeight);
        for (WitchSkillDefinition candidate : candidates) {
            pick -= candidate.weight();
            if (pick < 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }
}
