package dev.caecorthus.sparkwitch.api;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public record WitchSkillDefinition(
        Identifier id,
        int color,
        int weight,
        int initialCooldownTicks,
        int cooldownTicks,
        int manaCost,
        Predicate<WitchSkillSelectionContext> selector,
        Function<WitchSkillUseContext, WitchSkillUseResult> useHandler
) {
    public WitchSkillDefinition {
        Objects.requireNonNull(id, "id");
        weight = Math.max(0, weight);
        initialCooldownTicks = Math.max(0, initialCooldownTicks);
        cooldownTicks = Math.max(0, cooldownTicks);
        manaCost = Math.max(0, manaCost);
        int defaultCooldown = cooldownTicks;
        selector = selector == null ? context -> true : selector;
        useHandler = useHandler == null ? context -> WitchSkillUseResult.success(defaultCooldown) : useHandler;
    }

    public WitchSkillDefinition(
            Identifier id,
            int color,
            int weight,
            int cooldownTicks,
            Predicate<WitchSkillSelectionContext> selector,
            Function<WitchSkillUseContext, WitchSkillUseResult> useHandler
    ) {
        this(id, color, weight, 0, cooldownTicks, 0, selector, useHandler);
    }

    public MutableText name() {
        return Text.translatable(translationKey("name"));
    }

    public MutableText description() {
        return Text.translatable(translationKey("description"));
    }

    public boolean canSelect(WitchSkillSelectionContext context) {
        return selector.test(context);
    }

    public WitchSkillUseResult use(WitchSkillUseContext context) {
        return useHandler.apply(context);
    }

    public String translationKey(String suffix) {
        return "skill." + id.getNamespace() + "." + id.getPath().replace('/', '.') + "." + suffix;
    }
}
