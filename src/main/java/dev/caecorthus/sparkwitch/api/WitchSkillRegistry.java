package dev.caecorthus.sparkwitch.api;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WitchSkillRegistry {
    private static final Map<Identifier, WitchSkillDefinition> SKILLS = new LinkedHashMap<>();

    private WitchSkillRegistry() {
    }

    public static WitchSkillDefinition register(WitchSkillDefinition definition) {
        if (SKILLS.containsKey(definition.id())) {
            throw new IllegalArgumentException("Witch skill already registered: " + definition.id());
        }
        SKILLS.put(definition.id(), definition);
        return definition;
    }

    public static WitchSkillDefinition get(Identifier id) {
        return SKILLS.get(id);
    }

    public static Collection<WitchSkillDefinition> values() {
        return java.util.List.copyOf(SKILLS.values());
    }

}
